package io.github.oluengo.radiostr.nostr

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Directo NIP-53 (kind 30311). */
data class LiveStream(
    val address: String,
    val eventPubkey: String,
    val hostPubkey: String,
    val dTag: String,
    val title: String,
    val summary: String?,
    val image: String?,
    val streamingUrl: String?,
    val recordingUrl: String?,
    val status: Status,
    val startsAt: Long?,
    val endsAt: Long?,
    val participants: Int?,
    val hashtags: List<String>,
    val updatedAt: Long,
) {
    enum class Status { PLANNED, LIVE, ENDED }

    /** NIP-53: un "live" sin actualizar en 1 h puede considerarse terminado. */
    fun isLiveNow(nowSec: Long, staleAfterSec: Long = 3600): Boolean =
        status == Status.LIVE && streamingUrl != null && nowSec - updatedAt <= staleAfterSec

    val hasRecording: Boolean get() = status == Status.ENDED && !recordingUrl.isNullOrBlank()

    companion object {
        fun from(e: Event): LiveStream? {
            if (e.kind != Kinds.LIVE_EVENT) return null
            val d = e.tag("d") ?: return null
            // El anfitrión es la etiqueta p con rol "Host"; si no la hay, el autor del evento.
            val host = e.tagsNamed("p").firstOrNull { it.size > 3 && it[3].equals("host", ignoreCase = true) }?.get(1)
                ?: e.pubkey
            val status = when (e.tag("status")?.lowercase()) {
                "live" -> Status.LIVE
                "ended" -> Status.ENDED
                else -> Status.PLANNED
            }
            return LiveStream(
                address = e.address,
                eventPubkey = e.pubkey,
                hostPubkey = host.lowercase(),
                dTag = d,
                title = e.tag("title")?.takeIf { it.isNotBlank() } ?: "",
                summary = e.tag("summary")?.takeIf { it.isNotBlank() },
                image = e.tag("image")?.takeIf { it.startsWith("http") },
                streamingUrl = e.tagValues("streaming").firstOrNull { it.startsWith("http") },
                recordingUrl = e.tagValues("recording").firstOrNull { it.startsWith("http") },
                status = status,
                startsAt = e.tag("starts")?.toLongOrNull(),
                endsAt = e.tag("ends")?.toLongOrNull(),
                participants = e.tag("current_participants")?.toIntOrNull(),
                hashtags = e.tagValues("t").map { it.lowercase() }.distinct(),
                updatedAt = e.createdAt,
            )
        }
    }
}

/** Perfil kind 0. */
data class Profile(
    val pubkey: String,
    val name: String?,
    val displayName: String?,
    val picture: String?,
    val banner: String?,
    val about: String?,
    val nip05: String?,
    val lud16: String?,
) {
    val bestName: String get() = displayName?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() } ?: Nip19.shortNpub(pubkey)

    companion object {
        fun from(e: Event): Profile? {
            if (e.kind != Kinds.METADATA) return null
            val o = runCatching { NostrJson.parseToJsonElement(e.content) as JsonObject }.getOrNull() ?: return null
            fun s(k: String) = runCatching { o[k]?.jsonPrimitive?.contentOrNull }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            return Profile(
                pubkey = e.pubkey,
                name = s("name"),
                displayName = s("display_name") ?: s("displayName"),
                picture = s("picture")?.takeIf { it.startsWith("http") },
                banner = s("banner")?.takeIf { it.startsWith("http") },
                about = s("about"),
                nip05 = s("nip05"),
                lud16 = s("lud16"),
            )
        }
    }
}

/** Relays de lectura de una lista NIP-65 (kind 10002). */
fun Event.readRelays(): List<String> =
    tagsNamed("r").filter { it.size > 1 && (it.size == 2 || it[2] == "read") }.map { it[1].trimEnd('/') }
        .filter { it.startsWith("wss://") }

/** Pubkeys seguidas de una lista kind 3. */
fun Event.followedPubkeys(): List<String> =
    tagValues("p").map { it.lowercase() }.filter { it.length == 64 }.distinct()
