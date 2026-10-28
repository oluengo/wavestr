package io.github.oluengo.wavestr.radio

import io.github.oluengo.wavestr.data.FavItem

/**
 * Anterior / siguiente emisora. Si la que suena está en favoritos: las radios favoritas de su misma carpeta
 * (o todas, si es la única de la carpeta). Si no: la lista desde la que se eligió (populares, país, estilo,
 * búsqueda, parecidas…). Las que el directorio marca como caídas se saltan.
 */
object StationSkip {
    fun list(current: Station, favorites: List<FavItem>, context: List<Station>): List<Station> {
        val fav = favorites.firstOrNull { it.station?.id == current.id }
        if (fav != null) {
            val folder = favorites.filter { it.folderId == fav.folderId }.mapNotNull { it.station }
            return if (folder.size > 1) folder else favorites.mapNotNull { it.station }
        }
        return if (context.any { it.id == current.id }) context.filter { it.ok || it.id == current.id } else emptyList()
    }

    /** [dir] = +1 siguiente, -1 anterior; da la vuelta al llegar al final. null si no hay a dónde ir. */
    fun neighbor(current: Station, list: List<Station>, dir: Int): Station? {
        if (list.size < 2) return null
        val i = list.indexOfFirst { it.id == current.id }.takeIf { it >= 0 } ?: return list.first()
        return list[((i + dir) % list.size + list.size) % list.size]
    }
}
