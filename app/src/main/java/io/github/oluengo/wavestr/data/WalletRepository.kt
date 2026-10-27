package io.github.oluengo.wavestr.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.oluengo.wavestr.nostr.Nwc
import io.github.oluengo.wavestr.nostr.NwcConnection
import io.github.oluengo.wavestr.nostr.RelayPool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class WalletState(
    val connection: NwcConnection? = null,
    val budgetSats: Long = 10_000,
    val spentThisMonth: Long = 0,
    val defaultZap: Long = 100,
) {
    val leftThisMonth: Long get() = (budgetSats - spentThisMonth).coerceAtLeast(0)
}

/**
 * Cartera NWC. El enlace de conexión da permiso para pagar, así que se guarda cifrado con una clave del
 * Android Keystore (no sale del móvil ni va en las copias). La app lleva su propio límite mensual
 * además del que tenga la cartera.
 */
class WalletRepository(context: Context, private val pool: RelayPool) {
    private val prefs = context.getSharedPreferences("wallet", Context.MODE_PRIVATE)
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

    private fun load(): WalletState {
        val conn = prefs.getString("nwc", null)?.let { decrypt(it) }?.let(NwcConnection::parse)
        return WalletState(
            connection = conn,
            budgetSats = prefs.getLong("budget", 10_000),
            spentThisMonth = if (prefs.getString("month", null) == month()) prefs.getLong("spent", 0) else 0,
            defaultZap = prefs.getLong("default_zap", 100),
        )
    }

    private fun refresh() { _state.value = load() }

    /** Guarda la conexión si el enlace es válido. */
    fun connect(uri: String): NwcConnection? {
        val conn = NwcConnection.parse(uri) ?: return null
        prefs.edit().putString("nwc", encrypt(conn.toUri())).apply()
        refresh()
        return conn
    }

    fun disconnect() { prefs.edit().remove("nwc").apply(); refresh() }
    fun setBudget(sats: Long) { prefs.edit().putLong("budget", sats.coerceIn(0, 10_000_000)).apply(); refresh() }
    fun setDefaultZap(sats: Long) { prefs.edit().putLong("default_zap", sats.coerceIn(1, 1_000_000)).apply(); refresh() }

    /** Prueba la conexión con get_info: cualquier respuesta de la cartera (aunque sea un error) vale. */
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
        val spent = (if (prefs.getString("month", null) == m) prefs.getLong("spent", 0) else 0) + sats
        prefs.edit().putString("month", m).putLong("spent", spent).apply()
        refresh()
        return PayResult.Paid
    }

    // ---------- Cifrado con el Android Keystore ----------
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

    /** null si no se puede descifrar (p. ej. copia restaurada en otro móvil): la conexión se pide de nuevo. */
    private fun decrypt(s: String): String? = runCatching {
        val dec = Base64.getDecoder()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, dec.decode(s.substringBefore(':'))))
        String(c.doFinal(dec.decode(s.substringAfter(':'))))
    }.getOrNull()

    companion object {
        private const val ALIAS = "wavestr_nwc"
    }
}
