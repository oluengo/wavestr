package io.github.oluengo.radiostr.nostr

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

/**
 * Firma externa con Amber (NIP-55). La app nunca ve la clave privada.
 * F0: solo get_public_key. F2 añadirá sign_event (zaps) y nip44 (listas privadas).
 */
object Amber {
    private val probe get() = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:"))

    fun isInstalled(context: Context): Boolean {
        val pm = context.packageManager
        val list = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(probe, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(probe, 0)
        }
        return list.isNotEmpty()
    }

    /** Permisos que pediremos por adelantado para no molestar en cada zap. */
    private const val PERMISSIONS =
        """[{"type":"sign_event","kind":9734},{"type":"sign_event","kind":30003},{"type":"nip44_encrypt"},{"type":"nip44_decrypt"}]"""

    fun loginIntent(): Intent = probe.apply {
        putExtra("type", "get_public_key")
        putExtra("permissions", PERMISSIONS)
    }

    /** Pide a Amber que firme un evento (NIP-55 sign_event). */
    fun signIntent(unsigned: Event, userPubkey: String, signerPackage: String?): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:" + unsigned.toJson().toString())).apply {
            signerPackage?.let { `package` = it }
            putExtra("type", "sign_event")
            putExtra("id", unsigned.id)
            putExtra("current_user", Nip19.hexToNpub(userPubkey))
        }

    /**
     * Amber devuelve el evento firmado en "event" o solo la firma en "result"/"signature".
     * Se comprueba la firma: si no cuadra con lo que pedimos, se descarta.
     */
    fun parseSigned(data: android.content.Intent?, unsigned: Event): Event? {
        data ?: return null
        val full = data.getStringExtra("event")?.let(Event::fromJson)
        val candidate = full ?: (data.getStringExtra("result") ?: data.getStringExtra("signature"))
            ?.takeIf { it.length == 128 }?.let { unsigned.copy(sig = it) }
        return candidate?.takeIf { EventSigning.verify(it) && it.pubkey == unsigned.pubkey && it.kind == unsigned.kind }
    }

    data class Login(val pubkeyHex: String, val signerPackage: String?)

    /** Amber devuelve la clave en "result" (npub o hex según versión) y su paquete en "package". */
    fun parseLogin(data: Intent?): Login? {
        val raw = data?.getStringExtra("result") ?: return null
        val hex = Nip19.normalizePubkey(raw) ?: return null
        return Login(hex, data.getStringExtra("package"))
    }
}
