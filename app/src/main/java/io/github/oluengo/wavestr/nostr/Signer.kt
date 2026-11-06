package io.github.oluengo.wavestr.nostr

import android.content.Context
import android.content.Intent
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.oluengo.wavestr.crypto.Secp256k1
import io.github.oluengo.wavestr.data.LoginMethod
import io.github.oluengo.wavestr.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Con esta sesión se puede firmar (Amber o un firmante remoto NIP-46). */
val LoginMethod.canSign: Boolean get() = this == LoginMethod.AMBER || this == LoginMethod.BUNKER

/**
 * Firma y cifrado NIP-44 para toda la app, con el firmante que se haya elegido al entrar:
 * - **Amber** (NIP-55): primero en silencio; si hace falta su pantalla y la llamada es interactiva, se
 *   publica en [pending] para que la UI la abra ([io.github.oluengo.wavestr.ui.favorites.AmberRequestHost]).
 * - **Bunker** (NIP-46, nsecBunker y similares): por relays, con una clave de cliente propia guardada
 *   cifrada con el Android Keystore. Si el firmante pide aprobar en su web, la URL sale en [authUrl].
 */
class Signer(private val app: Context, private val settings: SettingsRepository, private val pool: RelayPool) {

    /** Petición a Amber que necesita abrir su pantalla (la lanza la UI). */
    class Call(val intent: Intent, internal val result: CompletableDeferred<Intent?>) {
        fun complete(data: Intent?) { result.complete(data) }
    }

    private val _pending = MutableStateFlow<Call?>(null)
    val pending: StateFlow<Call?> = _pending.asStateFlow()
    private val _authUrl = MutableStateFlow<String?>(null)
    /** El firmante remoto pide aprobar la conexión o la firma en su web. */
    val authUrl: StateFlow<String?> = _authUrl.asStateFlow()
    fun authUrlShown() { _authUrl.value = null }

    private val prefs = app.getSharedPreferences("bunker", Context.MODE_PRIVATE)
    @Volatile private var client: Nip46Client? = null

    // ---------- Operaciones ----------

    suspend fun sign(unsigned: Event, interactive: Boolean = true): Event? {
        val s = settings.current()
        if (s.pubkey != unsigned.pubkey) return null
        return when (s.loginMethod) {
            LoginMethod.AMBER ->
                withContext(Dispatchers.IO) { Amber.signSilently(app, s.signerPackage, unsigned, s.pubkey) }
                    ?: if (interactive) Amber.parseSigned(ask(Amber.signIntent(unsigned, s.pubkey, s.signerPackage)), unsigned) else null
            LoginMethod.BUNKER -> remote { it.sign(unsigned) }
            else -> null
        }
    }

    suspend fun nip44Encrypt(plain: String, peer: String, interactive: Boolean = true): String? {
        val s = settings.current()
        val me = s.pubkey ?: return null
        return when (s.loginMethod) {
            LoginMethod.AMBER ->
                withContext(Dispatchers.IO) { Amber.encryptSilently(app, s.signerPackage, plain, peer, me) }
                    ?: if (interactive) ask(Amber.nip44EncryptIntent(plain, peer, me, s.signerPackage))?.getStringExtra("result") else null
            LoginMethod.BUNKER -> remote { it.nip44Encrypt(plain, peer) }
            else -> null
        }
    }

    suspend fun nip44Decrypt(cipher: String, peer: String, interactive: Boolean = true): String? {
        val s = settings.current()
        val me = s.pubkey ?: return null
        return when (s.loginMethod) {
            LoginMethod.AMBER ->
                withContext(Dispatchers.IO) { Amber.decryptSilently(app, s.signerPackage, cipher, peer, me) }
                    ?: if (interactive) ask(Amber.nip44DecryptIntent(cipher, peer, me, s.signerPackage))?.getStringExtra("result") else null
            LoginMethod.BUNKER -> remote { it.nip44Decrypt(cipher, peer) }
            else -> null
        }
    }

    // ---------- Bunker (NIP-46) ----------

    sealed interface BunkerResult {
        data class Ok(val pubkey: String) : BunkerResult
        data object BadUri : BunkerResult
        data class Failed(val message: String?) : BunkerResult
    }

    /** Conecta con el firmante del enlace `bunker://`, guarda la conexión e inicia sesión. */
    suspend fun loginBunker(uri: String): BunkerResult {
        val b = BunkerUri.parse(uri) ?: return BunkerResult.BadUri
        val secret = Secp256k1.randomSecret()
        val cl = Nip46Client(pool, b, secret, onAuthUrl = { _authUrl.value = it })
        return try {
            val pk = cl.connect()
            prefs.edit().putString(K_URI, encrypt(b.copy(secret = null).toUri())).putString(K_KEY, encrypt(secret.toHex())).apply()
            client = cl
            settings.login(pk, LoginMethod.BUNKER)
            BunkerResult.Ok(pk)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BunkerResult.Failed(e.message)
        }
    }

    /** Al cerrar sesión se olvida la conexión con el firmante. */
    fun forgetBunker() {
        client = null
        prefs.edit().clear().apply()
    }

    private fun bunkerClient(): Nip46Client? {
        client?.let { return it }
        val uri = prefs.getString(K_URI, null)?.let(::decrypt)?.let(BunkerUri::parse) ?: return null
        val key = prefs.getString(K_KEY, null)?.let(::decrypt)?.hexToBytes()?.takeIf(Secp256k1::isValidSecret) ?: return null
        return Nip46Client(pool, uri, key, onAuthUrl = { _authUrl.value = it }).also { client = it }
    }

    private suspend fun <T> remote(op: suspend (Nip46Client) -> T): T? {
        val cl = bunkerClient() ?: return null
        return try { op(cl) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    }

    // ---------- Amber interactivo ----------

    private suspend fun ask(intent: Intent): Intent? {
        val call = Call(intent, CompletableDeferred())
        _pending.value = call
        return try {
            withTimeoutOrNull(180_000) { call.result.await() }
        } finally {
            if (_pending.value === call) _pending.value = null
        }
    }

    // ---------- Cifrado local (Android Keystore) ----------

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val enc = Base64.getEncoder()
        return enc.encodeToString(c.iv) + ":" + enc.encodeToString(c.doFinal(plain.toByteArray()))
    }

    /** null si no se puede descifrar (copia restaurada en otro móvil): habrá que volver a conectar. */
    private fun decrypt(s: String): String? = runCatching {
        val dec = Base64.getDecoder()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, dec.decode(s.substringBefore(':'))))
        String(c.doFinal(dec.decode(s.substringAfter(':'))))
    }.getOrNull()

    private companion object {
        const val ALIAS = "wavestr_bunker"
        const val K_URI = "uri"
        const val K_KEY = "client_key"
    }
}
