package io.github.oluengo.wavestr.player

import org.junit.Assert.assertEquals
import org.junit.Test

class HlsTest {
    @Test fun separateAudioRendition() {
        val master = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="Main",DEFAULT=YES,URI="audio/index.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720,CODECS="avc1.64001f,mp4a.40.2",AUDIO="aud"
            720/index.m3u8
        """.trimIndent()
        assertEquals(Hls.AudioLayout.SEPARATE, Hls.audioLayout(master))
    }

    @Test fun muxedVariants() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=4500000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2"
            1080/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720,CODECS="avc1.64001f,mp4a.40.2"
            720/index.m3u8
        """.trimIndent()
        assertEquals(Hls.AudioLayout.MUXED, Hls.audioLayout(master))
    }

    @Test fun mediaPlaylistIsMuxedAndGarbageUnknown() {
        assertEquals(Hls.AudioLayout.MUXED, Hls.audioLayout("#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXTINF:4,\nseg1.ts"))
        assertEquals(Hls.AudioLayout.UNKNOWN, Hls.audioLayout("<html>404</html>"))
    }
}
