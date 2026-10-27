package io.github.oluengo.wavestr.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Temporizador de apagado (Android y escritorio): para la reproducción al cabo de X minutos, bajando el
 * volumen en los últimos 30 s, o al terminar un stream de Nostr. [setVolume] recibe 0..1 sobre el volumen
 * del usuario; [stop] para el reproductor.
 */
class SleepTimer(
    private val scope: CoroutineScope,
    private val setVolume: (Float) -> Unit,
    private val stop: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    sealed interface Mode {
        data class At(val endsAt: Long) : Mode
        /** Solo streams de Nostr: se para cuando el stream deja de emitir. */
        data object StreamEnd : Mode
    }

    private val _mode = MutableStateFlow<Mode?>(null)
    val mode: StateFlow<Mode?> = _mode.asStateFlow()
    private var job: Job? = null

    fun start(minutes: Int) = schedule(now() + minutes * 60_000L)

    /** «+10 min»: alarga el que hay o empieza uno. */
    fun extend(minutes: Int) {
        val at = (_mode.value as? Mode.At)?.endsAt?.coerceAtLeast(now()) ?: now()
        schedule(at + minutes * 60_000L)
    }

    fun untilStreamEnd() {
        job?.cancel()
        setVolume(1f)
        _mode.value = Mode.StreamEnd
    }

    fun cancel() {
        job?.cancel()
        setVolume(1f)
        _mode.value = null
    }

    /** Lo llama el reproductor cuando un stream termina o deja de responder. */
    fun onStreamEnded() {
        if (_mode.value == Mode.StreamEnd) {
            _mode.value = null
            stop()
        }
    }

    /** Milisegundos que quedan (null si no hay cuenta atrás). */
    fun remainingMs(): Long? = (_mode.value as? Mode.At)?.let { (it.endsAt - now()).coerceAtLeast(0) }

    private fun schedule(endsAt: Long) {
        job?.cancel()
        setVolume(1f)
        _mode.value = Mode.At(endsAt)
        job = scope.launch {
            while (true) {
                val left = endsAt - now()
                if (left <= 0) break
                if (left <= FADE_MS) {
                    setVolume(left.toFloat() / FADE_MS)
                    delay(250)
                } else {
                    delay(minOf(left - FADE_MS, 1_000))
                }
            }
            stop()
            setVolume(1f)
            _mode.value = null
        }
    }

    companion object {
        const val FADE_MS = 30_000L
        val PRESETS = listOf(15, 30, 45, 60, 90)
    }
}
