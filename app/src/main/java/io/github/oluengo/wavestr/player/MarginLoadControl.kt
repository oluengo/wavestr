package io.github.oluengo.wavestr.player

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl

/**
 * Carga de ExoPlayer con el margen elegido en Ajustes. En la radio (stream continuo) no empieza a sonar
 * hasta tener ese margen; en los streams HLS el margen es el retraso respecto al directo (MediaItem) y aquí
 * se deja el comportamiento normal, porque los segmentos ya están en el servidor y se cargan al momento.
 */
@UnstableApi
class MarginLoadControl(private val base: DefaultLoadControl = DefaultLoadControl()) : LoadControl by base {
    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
        val marginUs = BufferPolicy.marginMs() * 1000
        if (marginUs == 0L || parameters.targetLiveOffsetUs != C.TIME_UNSET) return base.shouldStartPlayback(parameters)
        return parameters.bufferedDurationUs >= marginUs
    }
}
