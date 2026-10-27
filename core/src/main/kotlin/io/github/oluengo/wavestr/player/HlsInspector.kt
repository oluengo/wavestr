package io.github.oluengo.wavestr.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Descarga el master playlist para saber si el audio va separado. */
class HlsInspector(private val http: OkHttpClient) {
    suspend fun audioLayout(url: String): Hls.AudioLayout = withContext(Dispatchers.IO) {
        if (!url.contains(".m3u8", ignoreCase = true)) return@withContext Hls.AudioLayout.UNKNOWN
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) Hls.AudioLayout.UNKNOWN
                else Hls.audioLayout(r.body?.string().orEmpty().take(64_000))
            }
        }.getOrDefault(Hls.AudioLayout.UNKNOWN)
    }
}
