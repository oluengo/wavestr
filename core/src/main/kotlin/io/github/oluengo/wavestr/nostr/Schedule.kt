package io.github.oluengo.wavestr.nostr

import java.time.Instant
import java.time.ZoneId

/**
 * Algo programado: un directo NIP-53 en estado «planned» con hora de inicio, o un evento de calendario
 * NIP-52 con hora (kind 31923). Las horas son segundos Unix (UTC); [tzid] es la zona horaria del
 * organizador si la da (solo NIP-52), para mostrar también su hora local.
 */
data class Scheduled(
    val address: String,
    val hostPubkey: String,
    val title: String,
    val summary: String?,
    val image: String?,
    val startsAt: Long,
    val endsAt: Long?,
    val tzid: String?,
    val fromCalendar: Boolean,
) {
    /** Zona del organizador, si es válida y distinta de [local] en ese momento. */
    fun otherZone(local: ZoneId): ZoneId? {
        val z = tzid?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: return null
        val at = Instant.ofEpochSecond(startsAt)
        return z.takeIf { it.rules.getOffset(at) != local.rules.getOffset(at) }
    }

    companion object {
        fun from(e: Event): Scheduled? = when (e.kind) {
            Kinds.LIVE_EVENT -> LiveStream.from(e)?.takeIf { it.status == LiveStream.Status.PLANNED }?.let { s ->
                val start = s.startsAt ?: return null
                Scheduled(s.address, s.hostPubkey, s.title, s.summary, s.image, start, s.endsAt, null, fromCalendar = false)
            }
            Kinds.CALENDAR_TIME -> {
                val start = e.tag("start")?.toLongOrNull() ?: return null
                val title = (e.tag("title") ?: e.tag("name"))?.takeIf { it.isNotBlank() } ?: return null
                // El organizador: la etiqueta p con rol host, si la hay; si no, el autor.
                val host = e.tagsNamed("p").firstOrNull { it.size > 3 && it[3].equals("host", true) }?.get(1)?.lowercase() ?: e.pubkey
                Scheduled(
                    e.address, host, title, e.tag("summary") ?: e.content.takeIf { it.isNotBlank() }?.take(300),
                    e.tag("image")?.takeIf { it.startsWith("http") }, start, e.tag("end")?.toLongOrNull(),
                    e.tag("start_tzid")?.takeIf { it.isNotBlank() }, fromCalendar = true,
                )
            }
            else -> null
        }
    }
}
