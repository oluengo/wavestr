package io.github.oluengo.wavestr.desktop

import io.github.oluengo.wavestr.crypto.Secp256k1
import io.github.oluengo.wavestr.data.Library
import io.github.oluengo.wavestr.data.LibraryRepository
import io.github.oluengo.wavestr.nostr.BunkerUri
import io.github.oluengo.wavestr.nostr.Event
import io.github.oluengo.wavestr.nostr.EventSigning
import io.github.oluengo.wavestr.nostr.Filter
import io.github.oluengo.wavestr.nostr.Kinds
import io.github.oluengo.wavestr.nostr.Nip46Client
import io.github.oluengo.wavestr.nostr.NostrJson
import io.github.oluengo.wavestr.nostr.NostrRepository
import io.github.oluengo.wavestr.nostr.Nwc
import io.github.oluengo.wavestr.nostr.NwcConnection
import io.github.oluengo.wavestr.nostr.RelayPool
import io.github.oluengo.wavestr.nostr.Scheduled
import io.github.oluengo.wavestr.nostr.hexToBytes
import io.github.oluengo.wavestr.nostr.toHex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Secretos del escritorio (conexión con el bunker, enlace NWC): cifrados con AES-GCM y una clave en la
 * carpeta de datos, solo legible por el usuario. Evita que acaben en claro en copias o al compartir
 * ajustes; no protege frente a programas que ya corren con tu usuario.
 */
class Secrets(dir: File) {
    private val keyFile = File(dir, "secret.key")
    private val store = FileStore(File(dir, "secrets.properties"))
    private val key: ByteArray by lazy {
        runCatching { keyFile.readBytes().takeIf { it.size == 32 } }.getOrNull() ?: ByteArray(32).also { k ->
            SecureRandom().nextBytes(k)
            keyFile.writeBytes(k)
            ownerOnly(keyFile)
        }
    }

    fun get(name: String): String? = store.get(name)?.let { s ->
        runCatching {
            val dec = Base64.getDecoder()
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, dec.decode(s.substringBefore(':'))))
            String(c.doFinal(dec.decode(s.substringAfter(':'))))
        }.getOrNull()
    }

    fun put(name: String, value: String?) {
        if (value == null) { store.put(name, null); return }
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val enc = Base64.getEncoder()
        store.put(name, enc.encodeToString(iv) + ":" + enc.encodeToString(c.doFinal(value.toByteArray())))
        ownerOnly(File(keyFile.parentFile, "secrets.properties"))
    }

    private fun ownerOnly(f: File) {
        runCatching { Files.setPosixFilePermissions(f.toPath(), PosixFilePermissions.fromString("rw-------")) }
    }
}

/**
 * Firmante del escritorio: un bunker (NIP-46). En el ordenador no hay Amber y la app no guarda tu
 * clave privada. Si el firmante pide aprobar en su web, se abre el navegador.
 */
class DesktopSigner(private val settings: SettingsRepository, private val pool: RelayPool, private val secrets: Secrets) {
    @Volatile private var client: Nip46Client? = null
    private val _authUrl = MutableStateFlow<String?>(null)
    val authUrl: StateFlow<String?> = _authUrl.asStateFlow()

    val canSign: Boolean get() = settings.current.pubkey != null && settings.current.bunker && clientOrNull() != null

    sealed interface Result {
        data class Ok(val pubkey: String) : Result
        data object BadUri : Result
        data class Failed(val message: String?) : Result
    }

    suspend fun login(uri: String): Result {
        val b = BunkerUri.parse(uri) ?: return Result.BadUri
        val secret = Secp256k1.randomSecret()
        val cl = Nip46Client(pool, b, secret, onAuthUrl = ::openAuth)
        return try {
            val pk = cl.connect()
            secrets.put("bunker_uri", b.copy(secret = null).toUri())
            secrets.put("bunker_key", secret.toHex())
            client = cl
            settings.loginBunker(pk)
            Result.Ok(pk)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failed(e.message)
        }
    }

    fun forget() {
        client = null
        secrets.put("bunker_uri", null); secrets.put("bunker_key", null)
    }

    suspend fun sign(unsigned: Event): Event? = remote { it.sign(unsigned) }
    suspend fun nip44Encrypt(plain: String, peer: String): String? = remote { it.nip44Encrypt(plain, peer) }
    suspend fun nip44Decrypt(cipher: String, peer: String): String? = remote { it.nip44Decrypt(cipher, peer) }

    private fun clientOrNull(): Nip46Client? {
        client?.let { return it }
        val uri = secrets.get("bunker_uri")?.let(BunkerUri::parse) ?: return null
        val key = secrets.get("bunker_key")?.hexToBytes()?.takeIf(Secp256k1::isValidSecret) ?: return null
        return Nip46Client(pool, uri, key, onAuthUrl = ::openAuth).also { client = it }
    }

    private suspend fun <T> remote(op: suspend (Nip46Client) -> T): T? {
        if (!settings.current.bunker) return null
        val cl = clientOrNull() ?: return null
        return try { op(cl) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    }

    private fun openAuth(url: String) {
        if (!url.startsWith("https://")) return
        _authUrl.value = url
        runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
    }
}

data class WalletState(val connection: NwcConnection? = null, val budgetSats: Long = 10_000, val spentThisMonth: Long = 0, val defaultZap: Long = 100) {
    val leftThisMonth: Long get() = (budgetSats - spentThisMonth).coerceAtLeast(0)
}

/** Cartera NWC (como en Android): el enlace va cifrado en [Secrets] y la app lleva su propio límite mensual. */
class DesktopWallet(private val store: FileStore, private val secrets: Secrets, private val pool: RelayPool) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<WalletState> = _state.asStateFlow()

    sealed interface PayResult {
        data object Paid : PayResult
        data class OverBudget(val left: Long) : PayResult
        data class WalletError(val code: String?, val message: String?) : PayResult
        data object Timeout : PayResult
        data object NotConnected : PayResult
    }

    private fun month() = SimpleDateFormat("yyyy-MM", Locale.ROOT).format(Date())
    private fun load() = WalletState(
        connection = secrets.get("nwc")?.let(NwcConnection::parse),
        budgetSats = store.get("budget")?.toLongOrNull() ?: 10_000,
        spentThisMonth = if (store.get("month") == month()) store.get("spent")?.toLongOrNull() ?: 0 else 0,
        defaultZap = store.get("default_zap")?.toLongOrNull() ?: 100,
    )
    private fun refresh() { _state.value = load() }

    fun connect(uri: String): NwcConnection? {
        val conn = NwcConnection.parse(uri) ?: return null
        secrets.put("nwc", conn.toUri()); refresh(); return conn
    }
    fun disconnect() { secrets.put("nwc", null); refresh() }
    fun setBudget(sats: Long) { store.put("budget", sats.coerceIn(0, 10_000_000).toString()); refresh() }
    fun setDefaultZap(sats: Long) { store.put("default_zap", sats.coerceIn(1, 1_000_000).toString()); refresh() }

    suspend fun test(conn: NwcConnection? = _state.value.connection): Boolean {
        conn ?: return false
        val req = Nwc.getInfo(conn)
        val resp = pool.roundTrip(conn.relays, req, Nwc.responseFilter(conn, req), timeoutMs = 20_000) ?: return false
        return Nwc.parseResponse(conn, resp) != null
    }

    suspend fun pay(invoice: String, sats: Long): PayResult {
        val st = _state.value
        val conn = st.connection ?: return PayResult.NotConnected
        if (sats > st.leftThisMonth) return PayResult.OverBudget(st.leftThisMonth)
        val req = Nwc.payInvoice(conn, invoice)
        val ev = pool.roundTrip(conn.relays, req, Nwc.responseFilter(conn, req)) ?: return PayResult.Timeout
        val resp = Nwc.parseResponse(conn, ev) ?: return PayResult.WalletError(null, null)
        if (resp.errorCode != null) return PayResult.WalletError(resp.errorCode, resp.errorMessage)
        val m = month()
        val spent = (if (store.get("month") == m) store.get("spent")?.toLongOrNull() ?: 0 else 0) + sats
        store.put("month", m); store.put("spent", spent.toString())
        refresh()
        return PayResult.Paid
    }
}

/**
 * Favoritos en Nostr, igual que en Android (mismo evento kind 30078 «wavestr/favorites», cifrado
 * NIP-44 para uno mismo): así móvil y ordenador comparten carpetas y favoritos. Necesita bunker.
 */
@OptIn(FlowPreview::class)
class DesktopFavSync(
    private val settings: SettingsRepository,
    private val library: LibraryRepository,
    private val nostr: NostrRepository,
    private val pool: RelayPool,
    private val signer: DesktopSigner,
    private val store: FileStore,
    private val scope: CoroutineScope,
) {
    enum class Status { OFF, IDLE, WORKING, DONE, FAILED, TOO_BIG, NO_SIGNER }
    data class State(val status: Status = Status.OFF, val lastSync: Long = 0)

    private val _state = MutableStateFlow(State(lastSync = store.get("last")?.toLongOrNull() ?: 0))
    val state: StateFlow<State> = _state.asStateFlow()
    private val mutex = Mutex()

    fun start() {
        scope.launch {
            settings.flow.map { s -> s.pubkey.takeIf { s.favSync && s.bunker } }.distinctUntilChanged().collectLatest { me ->
                if (me == null) { _state.update { it.copy(status = Status.OFF) }; return@collectLatest }
                if (store.get("pubkey") != me) {
                    listOf("hash", "remote_at", "last").forEach { store.put(it, null) }
                    store.put("pubkey", me)
                }
                library.ready()
                run(me)
                library.library.drop(1).debounce(4_000).collect { run(me, pull = false) }
            }
        }
    }

    fun syncNow() { settings.current.pubkey?.let { me -> scope.launch { run(me) } } }

    private suspend fun run(me: String, pull: Boolean = true) = mutex.withLock {
        _state.update { it.copy(status = Status.WORKING) }
        val st = try { sync(me, pull) } catch (e: CancellationException) { throw e } catch (_: Exception) { Status.FAILED }
        _state.value = State(st, store.get("last")?.toLongOrNull() ?: 0)
    }

    private suspend fun sync(me: String, pull: Boolean): Status {
        val relays = nostr.ownRelays(me)
        if (pull) {
            val remote = pool.query(relays, listOf(Filter(kinds = listOf(Kinds.APP_DATA), authors = listOf(me), tags = mapOf("d" to listOf(D_TAG)), limit = 1)))
                .filter { it.pubkey == me && it.tag("d") == D_TAG && EventSigning.verify(it) }
                .maxByOrNull { it.createdAt }
            if (remote != null && remote.createdAt > (store.get("remote_at")?.toLongOrNull() ?: 0)) {
                val plain = signer.nip44Decrypt(remote.content, me) ?: return Status.NO_SIGNER
                val obj = runCatching { NostrJson.parseToJsonElement(plain) as? JsonObject }.getOrNull() ?: return Status.FAILED
                val remoteLib = Library.fromJson(obj)
                val local = library.library.value
                val lastHash = store.get("hash")
                val next = if (lastHash == null || hash(local) != lastHash) remoteLib.merge(local) else remoteLib
                library.commit { next }
                store.put("remote_at", remote.createdAt.toString())
                if (hash(next) == hash(remoteLib)) { store.put("hash", hash(next)); store.put("last", System.currentTimeMillis().toString()); return Status.DONE }
            }
        }
        val lib = library.library.value
        val h = hash(lib)
        if (h == store.get("hash")) return Status.DONE
        val json = lib.toJson().toString()
        if (json.toByteArray().size > 64_000) return Status.TOO_BIG
        val enc = signer.nip44Encrypt(json, me) ?: return Status.NO_SIGNER
        val signed = signer.sign(EventSigning.unsigned(me, Kinds.APP_DATA, listOf(listOf("d", D_TAG)), enc)) ?: return Status.NO_SIGNER
        if (pool.publish(relays, signed) == 0) return Status.FAILED
        store.put("hash", h); store.put("remote_at", signed.createdAt.toString()); store.put("last", System.currentTimeMillis().toString())
        return Status.DONE
    }

    private fun hash(l: Library) = Secp256k1.sha256(l.toJson().toString().toByteArray()).toHex()

    companion object { const val D_TAG = "wavestr/favorites" }
}

/** Un recordatorio de un directo o evento programado ([leadMin] minutos antes del inicio). */
data class Reminder(val address: String, val title: String, val host: String, val hostName: String, val startsAt: Long, val leadMin: Int) {
    val fireAtMs: Long get() = (startsAt - leadMin * 60L) * 1000
}

/**
 * Recordatorios del escritorio: se guardan en la carpeta de datos y se comprueban cada 20 s mientras la
 * app está abierta (también en la bandeja). Al llegar la hora se avisa con [onFire].
 */
class DesktopReminders(dir: File, private val scope: CoroutineScope) {
    private val file = File(dir, "reminders.json")
    private val _all = MutableStateFlow(read())
    val all: StateFlow<List<Reminder>> = _all.asStateFlow()
    var onFire: (Reminder) -> Unit = {}

    fun start() {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            while (isActive) {
                val now = System.currentTimeMillis()
                val due = _all.value.filter { it.fireAtMs <= now }
                if (due.isNotEmpty()) {
                    // Avisos recientes (no los que pasaron con la app cerrada hace rato).
                    due.filter { now - it.fireAtMs < 30 * 60_000 }.forEach { r -> runCatching { onFire(r) } }
                    save(_all.value - due.toSet())
                }
                delay(20_000)
            }
        }
    }

    fun get(address: String) = _all.value.firstOrNull { it.address == address }
    fun set(s: Scheduled, hostName: String, leadMin: Int) =
        save(_all.value.filter { it.address != s.address } + Reminder(s.address, s.title, s.hostPubkey, hostName, s.startsAt, leadMin))
    fun remove(address: String) = save(_all.value.filter { it.address != address })

    fun refresh(list: List<Scheduled>) {
        var changed = false
        val next = _all.value.map { r ->
            val s = list.firstOrNull { it.address == r.address } ?: return@map r
            if (s.startsAt == r.startsAt && s.title == r.title) r else { changed = true; r.copy(startsAt = s.startsAt, title = s.title) }
        }
        if (changed) save(next)
    }

    private fun read(): List<Reminder> = runCatching {
        (NostrJson.parseToJsonElement(file.readText()) as JsonArray).mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
            Reminder(s("a") ?: return@mapNotNull null, s("t").orEmpty(), s("h") ?: return@mapNotNull null, s("n").orEmpty(),
                (o["s"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null, (o["l"] as? JsonPrimitive)?.intOrNull ?: 0)
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<Reminder>) {
        _all.value = list
        runCatching {
            file.writeText(buildJsonArray {
                list.forEach { r -> add(buildJsonObject { put("a", r.address); put("t", r.title); put("h", r.host); put("n", r.hostName); put("s", r.startsAt); put("l", r.leadMin) }) }
            }.toString())
        }
    }

    companion object { val LEADS = listOf(0, 5, 15, 60) }
}

/**
 * Aviso del sistema: en Linux con `notify-send` (si está), si no con la bandeja (si la hay).
 * Devuelve false si no se pudo mostrar por ninguna vía.
 */
object SystemNotify {
    var tray: ((String, String) -> Unit)? = null

    fun show(title: String, text: String): Boolean {
        if (!AppInfo.isWindows) {
            val ok = runCatching {
                ProcessBuilder("notify-send", "-a", AppInfo.name, "-u", "normal", title, text).redirectErrorStream(true).start().waitFor() == 0
            }.getOrDefault(false)
            if (ok) return true
        }
        tray?.let { runCatching { it(title, text) }.onSuccess { return true } }
        return false
    }
}
