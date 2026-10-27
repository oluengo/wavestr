package io.github.oluengo.wavestr.data

import android.content.Context
import android.content.Intent
import io.github.oluengo.wavestr.crypto.Secp256k1
import io.github.oluengo.wavestr.nostr.Amber
import io.github.oluengo.wavestr.nostr.EventSigning
import io.github.oluengo.wavestr.nostr.Filter
import io.github.oluengo.wavestr.nostr.Kinds
import io.github.oluengo.wavestr.nostr.NostrJson
import io.github.oluengo.wavestr.nostr.NostrRepository
import io.github.oluengo.wavestr.nostr.RelayPool
import io.github.oluengo.wavestr.nostr.toHex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject

/**
 * Favoritos en Nostr (ver docs/decisiones/0008): toda la biblioteca, cifrada con NIP-44 para uno mismo,
 * en un evento reemplazable kind 30078 (NIP-78). Solo con Amber, que cifra y firma.
 *
 * - Al activar o al abrir la app: se trae la copia de Nostr. Si aquí no ha cambiado nada desde la
 *   última sincronización, se adopta tal cual (así se propagan también los borrados); si los dos lados
 *   cambiaron, se combinan sin perder nada (gana lo local en conflicto).
 * - Cada cambio local se sube a los 4 s.
 * - En segundo plano solo se usa Amber si dio permiso «siempre»; si no, el estado pasa a
 *   [Status.NeedsAmber] y el usuario sincroniza a mano (se abre Amber).
 */
@OptIn(FlowPreview::class)
class FavoritesSync(
    private val app: Context,
    private val settings: SettingsRepository,
    private val library: LibraryRepository,
    private val nostr: NostrRepository,
    private val pool: RelayPool,
    private val scope: CoroutineScope,
) {
    sealed interface Status {
        data object Off : Status
        data object Idle : Status
        data object Working : Status
        data object NeedsAmber : Status
        data object Done : Status
        data class Failed(val reason: Reason) : Status
    }

    enum class Reason { OFFLINE, TOO_BIG, UNREADABLE }

    data class State(val status: Status = Status.Off, val lastSync: Long = 0)

    /** Petición a Amber que necesita abrir su pantalla (la lanza la UI). */
    class AmberCall(val intent: Intent, internal val result: CompletableDeferred<Intent?>) {
        fun complete(data: Intent?) { result.complete(data) }
    }

    private val prefs = app.getSharedPreferences("favsync", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(State(lastSync = prefs.getLong(K_LAST, 0)))
    val state: StateFlow<State> = _state.asStateFlow()
    private val _pending = MutableStateFlow<AmberCall?>(null)
    val pending: StateFlow<AmberCall?> = _pending.asStateFlow()
    private val mutex = Mutex()

    private data class Account(val pubkey: String, val signer: String?)

    fun start() {
        scope.launch {
            settings.flow
                .map { s -> if (s.favSync == true && s.loginMethod == LoginMethod.AMBER && s.pubkey != null) Account(s.pubkey, s.signerPackage) else null }
                .distinctUntilChanged()
                .collectLatest { acc ->
                    if (acc == null) { _state.update { it.copy(status = Status.Off) }; return@collectLatest }
                    if (prefs.getString(K_PUBKEY, null) != acc.pubkey) {
                        prefs.edit().clear().putString(K_PUBKEY, acc.pubkey).apply()
                        _state.value = State(Status.Idle, 0)
                    }
                    library.ready()
                    run(acc, interactive = false)
                    library.library.drop(1).debounce(4_000).collect { run(acc, interactive = false, pull = false) }
                }
        }
    }

    /** «Sincronizar ahora»: puede abrir Amber. */
    fun syncNow() {
        scope.launch {
            val s = settings.current()
            if (s.loginMethod != LoginMethod.AMBER || s.pubkey == null) return@launch
            run(Account(s.pubkey, s.signerPackage), interactive = true)
        }
    }

    private suspend fun run(acc: Account, interactive: Boolean, pull: Boolean = true) = mutex.withLock {
        _state.update { it.copy(status = Status.Working) }
        val status = try {
            sync(acc, interactive, pull)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Status.Failed(Reason.OFFLINE)
        }
        _state.update { State(status, prefs.getLong(K_LAST, 0)) }
    }

    private suspend fun sync(acc: Account, interactive: Boolean, pull: Boolean): Status {
        val me = acc.pubkey
        val relays = nostr.ownRelays(me)
        val lastHash = prefs.getString(K_HASH, null)

        if (pull) {
            val remote = pool.query(relays, listOf(Filter(kinds = listOf(Kinds.APP_DATA), authors = listOf(me), tags = mapOf("d" to listOf(D_TAG)), limit = 1)))
                .filter { it.pubkey == me && it.tag("d") == D_TAG && EventSigning.verify(it) }
                .maxByOrNull { it.createdAt }
            if (remote != null && remote.createdAt > prefs.getLong(K_REMOTE_AT, 0)) {
                val plain = decrypt(acc, remote.content, interactive) ?: return Status.NeedsAmber
                val obj = runCatching { NostrJson.parseToJsonElement(plain) as? JsonObject }.getOrNull()
                    ?: return Status.Failed(Reason.UNREADABLE)
                val remoteLib = Library.fromJson(obj)
                val local = library.library.value
                val localChanged = lastHash == null || hash(local) != lastHash
                val next = if (localChanged) remoteLib.merge(local) else remoteLib
                library.commit { next }
                prefs.edit().putLong(K_REMOTE_AT, remote.createdAt).apply()
                if (hash(next) == hash(remoteLib)) {
                    prefs.edit().putString(K_HASH, hash(next)).putLong(K_LAST, now()).apply()
                    return Status.Done
                }
            }
        }

        val lib = library.library.value
        val h = hash(lib)
        if (h == prefs.getString(K_HASH, null)) return Status.Done
        val json = lib.toJson().toString()
        if (json.toByteArray().size > MAX_BYTES) return Status.Failed(Reason.TOO_BIG)
        val enc = encrypt(acc, json, interactive) ?: return Status.NeedsAmber
        val unsigned = EventSigning.unsigned(me, Kinds.APP_DATA, listOf(listOf("d", D_TAG)), enc)
        val signed = sign(acc, unsigned, interactive) ?: return Status.NeedsAmber
        if (pool.publish(relays, signed) == 0) return Status.Failed(Reason.OFFLINE)
        prefs.edit().putString(K_HASH, h).putLong(K_REMOTE_AT, signed.createdAt).putLong(K_LAST, now()).apply()
        return Status.Done
    }

    // ---------- Amber: primero en silencio, luego (si se pidió a mano) abriendo su pantalla ----------
    private suspend fun encrypt(acc: Account, plain: String, interactive: Boolean): String? =
        withContext(Dispatchers.IO) { Amber.encryptSilently(app, acc.signer, plain, acc.pubkey, acc.pubkey) }
            ?: if (interactive) ask(Amber.nip44EncryptIntent(plain, acc.pubkey, acc.pubkey, acc.signer))?.getStringExtra("result") else null

    private suspend fun decrypt(acc: Account, cipher: String, interactive: Boolean): String? =
        withContext(Dispatchers.IO) { Amber.decryptSilently(app, acc.signer, cipher, acc.pubkey, acc.pubkey) }
            ?: if (interactive) ask(Amber.nip44DecryptIntent(cipher, acc.pubkey, acc.pubkey, acc.signer))?.getStringExtra("result") else null

    private suspend fun sign(acc: Account, unsigned: io.github.oluengo.wavestr.nostr.Event, interactive: Boolean) =
        withContext(Dispatchers.IO) { Amber.signSilently(app, acc.signer, unsigned, acc.pubkey) }
            ?: if (interactive) Amber.parseSigned(ask(Amber.signIntent(unsigned, acc.pubkey, acc.signer)), unsigned) else null

    private suspend fun ask(intent: Intent): Intent? {
        val call = AmberCall(intent, CompletableDeferred())
        _pending.value = call
        return try {
            withTimeoutOrNull(180_000) { call.result.await() }
        } finally {
            if (_pending.value === call) _pending.value = null
        }
    }

    private fun hash(l: Library) = Secp256k1.sha256(l.toJson().toString().toByteArray()).toHex()
    private fun now() = System.currentTimeMillis()

    companion object {
        /** Identificador del dato en Nostr. Cambiarlo deja huérfanas las copias ya guardadas. */
        const val D_TAG = "wavestr/favorites"
        /** NIP-44 admite hasta 65 535 bytes de texto. */
        const val MAX_BYTES = 64_000
        private const val K_HASH = "hash"
        private const val K_REMOTE_AT = "remote_at"
        private const val K_LAST = "last"
        private const val K_PUBKEY = "pubkey"
    }
}
