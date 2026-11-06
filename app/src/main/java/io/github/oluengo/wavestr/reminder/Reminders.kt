package io.github.oluengo.wavestr.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.oluengo.wavestr.MainActivity
import io.github.oluengo.wavestr.R
import io.github.oluengo.wavestr.nostr.NostrJson
import io.github.oluengo.wavestr.nostr.Scheduled
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Un recordatorio de un directo o evento programado: avisa [leadMin] minutos antes de [startsAt]. */
data class Reminder(
    val address: String,
    val title: String,
    val host: String,
    val hostName: String,
    val startsAt: Long,
    val leadMin: Int,
) {
    val fireAtMs: Long get() = (startsAt - leadMin * 60L) * 1000
}

/**
 * Recordatorios guardados en el móvil y programados con AlarmManager (exactos si el sistema lo permite;
 * si no, aproximados). Se vuelven a programar al reiniciar el móvil o actualizar la app.
 */
class Reminders(private val app: Context) {
    private val prefs = app.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    private val _all = MutableStateFlow(read())
    val all: StateFlow<List<Reminder>> = _all.asStateFlow()

    fun get(address: String): Reminder? = _all.value.firstOrNull { it.address == address }

    fun set(s: Scheduled, hostName: String, leadMin: Int) {
        val r = Reminder(s.address, s.title, s.hostPubkey, hostName, s.startsAt, leadMin)
        save(_all.value.filter { it.address != r.address } + r)
        schedule(r)
    }

    fun remove(address: String) {
        get(address)?.let { cancel(it) }
        save(_all.value.filter { it.address != address })
    }

    /** Si el organizador cambió la hora, se reprograma. */
    fun refresh(list: List<Scheduled>) {
        var changed = false
        val next = _all.value.map { r ->
            val s = list.firstOrNull { it.address == r.address } ?: return@map r
            if (s.startsAt == r.startsAt && s.title == r.title) r else { changed = true; r.copy(startsAt = s.startsAt, title = s.title).also(::schedule) }
        }
        if (changed) save(next)
    }

    /** Tras reiniciar: programa los pendientes y olvida los que ya pasaron. */
    fun rescheduleAll() {
        val now = System.currentTimeMillis()
        val keep = _all.value.filter { it.startsAt * 1000 > now - 3_600_000 }
        save(keep)
        keep.filter { it.fireAtMs > now }.forEach(::schedule)
    }

    fun fired(address: String) {
        save(_all.value.filter { it.address != address || it.startsAt * 1000 > System.currentTimeMillis() + 60_000 })
    }

    private fun schedule(r: Reminder) {
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val at = maxOf(r.fireAtMs, System.currentTimeMillis() + 5_000)
        val pi = pending(r.address)
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        runCatching {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancel(r: Reminder) {
        app.getSystemService(AlarmManager::class.java)?.cancel(pending(r.address))
    }

    private fun pending(address: String): PendingIntent = PendingIntent.getBroadcast(
        app, address.hashCode(),
        Intent(app, ReminderReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_ADDRESS, address),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun read(): List<Reminder> = runCatching {
        (NostrJson.parseToJsonElement(prefs.getString(K, "[]")!!) as JsonArray).mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
            Reminder(
                s("a") ?: return@mapNotNull null, s("t").orEmpty(), s("h") ?: return@mapNotNull null, s("n").orEmpty(),
                (o["s"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null, (o["l"] as? JsonPrimitive)?.intOrNull ?: 0,
            )
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<Reminder>) {
        val json = buildJsonArray {
            list.forEach { r -> add(buildJsonObject { put("a", r.address); put("t", r.title); put("h", r.host); put("n", r.hostName); put("s", r.startsAt); put("l", r.leadMin) }) }
        }
        prefs.edit().putString(K, json.toString()).apply()
        _all.value = list
    }

    /** Muestra el aviso; al tocarlo se abre el directo (o el perfil, si aún no ha empezado). */
    fun notify(r: Reminder) {
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, app.getString(R.string.reminders_channel), NotificationManager.IMPORTANCE_HIGH))
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(
            app, r.address.hashCode(),
            Intent(app, MainActivity::class.java).setAction(ACTION_OPEN).putExtra(EXTRA_ADDRESS, r.address).putExtra(EXTRA_HOST, r.host)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (r.leadMin == 0) app.getString(R.string.reminder_now, r.hostName)
        else app.getString(R.string.reminder_soon, r.leadMin, r.hostName)
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(r.title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(r.address.hashCode(), n) }
    }

    companion object {
        const val ACTION_FIRE = "io.github.oluengo.wavestr.REMINDER"
        const val ACTION_OPEN = "io.github.oluengo.wavestr.OPEN_STREAM"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_HOST = "host"
        private const val CHANNEL = "reminders"
        private const val K = "list"
        /** Opciones de aviso, en minutos antes del inicio. */
        val LEADS = listOf(0, 5, 15, 60)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val r = (context.applicationContext as io.github.oluengo.wavestr.WavestrApp).container.reminders
        when (intent.action) {
            Reminders.ACTION_FIRE -> {
                val address = intent.getStringExtra(Reminders.EXTRA_ADDRESS) ?: return
                r.get(address)?.let { r.notify(it); r.fired(address) }
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> r.rescheduleAll()
        }
    }
}
