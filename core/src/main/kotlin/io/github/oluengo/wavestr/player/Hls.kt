package io.github.oluengo.wavestr.player

/** Lectura mínima de playlists HLS, sin dependencias de Android (testeable en JVM). */
object Hls {
    enum class AudioLayout { SEPARATE, MUXED, UNKNOWN }

    /**
     * Pista de audio separada = el modo "solo audio" ahorra datos además de batería.
     * Master playlist con un #EXT-X-MEDIA:TYPE=AUDIO que tiene URI propia → SEPARATE.
     * Cualquier otra playlist HLS válida → MUXED (audio y vídeo en los mismos segmentos).
     */
    fun audioLayout(playlist: String): AudioLayout {
        val lines = playlist.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.firstOrNull()?.startsWith("#EXTM3U") != true) return AudioLayout.UNKNOWN
        val separate = lines.any { it.startsWith("#EXT-X-MEDIA:") && it.contains("TYPE=AUDIO") && it.contains("URI=") }
        return if (separate) AudioLayout.SEPARATE else AudioLayout.MUXED
    }
}
