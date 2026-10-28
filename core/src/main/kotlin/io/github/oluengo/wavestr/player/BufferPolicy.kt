package io.github.oluengo.wavestr.player

/** Margen contra cortes: cuánto se carga antes de sonar (y cuánto por detrás del directo se va). */
enum class BufferMode { AUTO, LIVE, S10, S20, S30 }

object BufferPolicy {
    @Volatile var mode: BufferMode = BufferMode.S10
    /** Lo pone la app: true si la conexión actual es de datos móviles. */
    @Volatile var cellular: Boolean = false

    /** Milisegundos de margen; 0 = lo mínimo (directo). Auto: directo con wifi, 20 s con datos. */
    fun marginMs(mode: BufferMode = this.mode, cellular: Boolean = this.cellular): Long = when (mode) {
        BufferMode.AUTO -> if (cellular) 20_000 else 0
        BufferMode.LIVE -> 0
        BufferMode.S10 -> 10_000
        BufferMode.S20 -> 20_000
        BufferMode.S30 -> 30_000
    }
}
