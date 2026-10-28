package io.github.oluengo.wavestr.player

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.ForwardingPlayer

/** Une el servicio (notificación, auriculares, coche) con [PlayerConnection.skip]. Mismo proceso. */
object SkipBridge {
    @Volatile var handler: ((Int) -> Unit)? = null
    @Volatile var available: () -> Boolean = { false }
}

/**
 * El reproductor del servicio con «anterior / siguiente» activos aunque solo haya una emisora cargada:
 * los botones saltan a la emisora de al lado (favoritos o la lista de origen).
 */
@UnstableApi
class SkipPlayer(player: Player) : ForwardingPlayer(player) {
    private val skipCommands = Player.Commands.Builder()
        .addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        .build()

    private fun canSkip() = SkipBridge.available() && SkipBridge.handler != null

    override fun getAvailableCommands(): Player.Commands {
        val base = super.getAvailableCommands()
        return if (canSkip()) base.buildUpon().addAll(skipCommands).build() else base
    }

    override fun isCommandAvailable(command: Int): Boolean =
        super.isCommandAvailable(command) || (canSkip() && skipCommands.contains(command))

    override fun hasNextMediaItem(): Boolean = canSkip() || super.hasNextMediaItem()
    override fun hasPreviousMediaItem(): Boolean = canSkip() || super.hasPreviousMediaItem()

    override fun seekToNext() { if (canSkip()) SkipBridge.handler?.invoke(+1) else super.seekToNext() }
    override fun seekToNextMediaItem() { if (canSkip()) SkipBridge.handler?.invoke(+1) else super.seekToNextMediaItem() }
    override fun seekToPrevious() { if (canSkip()) SkipBridge.handler?.invoke(-1) else super.seekToPrevious() }
    override fun seekToPreviousMediaItem() { if (canSkip()) SkipBridge.handler?.invoke(-1) else super.seekToPreviousMediaItem() }
}
