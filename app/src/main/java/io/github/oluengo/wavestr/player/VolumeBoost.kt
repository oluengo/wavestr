package io.github.oluengo.wavestr.player

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.github.oluengo.wavestr.R
import io.github.oluengo.wavestr.ui.theme.Rs

/**
 * Refuerzo de volumen por emisora o stream (por su id), para las que suenan flojas. El reproductor no
 * puede pasar del 100 %; el amplificador de sonoridad de Android (LoudnessEnhancer) sí, sobre la
 * sesión de audio de ExoPlayer. Se guarda en el móvil y se aplica al cambiar de emisora.
 */
object VolumeBoost {
    val STEPS = listOf(0, 3, 6, 9, 12)
    private const val PREFS = "volume_boost"

    fun get(ctx: Context, id: String?): Int = id?.let { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(it, 0) } ?: 0

    fun set(ctx: Context, id: String, db: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply { if (db == 0) remove(id) else putInt(id, db) }.apply()
    }

    /** Aplica el refuerzo al reproductor del servicio; se llama al cambiar de pista o de ajuste. */
    @OptIn(UnstableApi::class)
    class Applier(private val ctx: Context, private val player: ExoPlayer) {
        private var enhancer: LoudnessEnhancer? = null
        private var session = 0
        private val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> apply() }

        init { prefs.registerOnSharedPreferenceChangeListener(listener) }

        fun apply() {
            val db = get(ctx, player.currentMediaItem?.mediaId)
            val sid = player.audioSessionId
            if (db == 0 || sid == 0) { runCatching { enhancer?.enabled = false }; return }
            if (enhancer == null || session != sid) {
                runCatching { enhancer?.release() }
                enhancer = runCatching { LoudnessEnhancer(sid) }.getOrNull()
                session = sid
            }
            runCatching { enhancer?.setTargetGain(db * 100); enhancer?.enabled = true }
        }

        fun release() {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            runCatching { enhancer?.release() }
            enhancer = null
        }
    }
}

/** Chip «Volumen +6 dB» del reproductor para subir una emisora que suena floja. */
@Composable
fun VolumeBoostChip(id: String) {
    val ctx = LocalContext.current
    var db by remember(id) { mutableIntStateOf(VolumeBoost.get(ctx, id)) }
    var open by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(if (db == 0) stringResource(R.string.boost) else stringResource(R.string.boost_on, db)) },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.VolumeUp, null, Modifier.size(18.dp)) },
            colors = if (db != 0) AssistChipDefaults.assistChipColors(containerColor = Rs.colors.accentSoft, labelColor = MaterialTheme.colorScheme.primary, leadingIconContentColor = MaterialTheme.colorScheme.primary)
            else AssistChipDefaults.assistChipColors(),
        )
        DropdownMenu(open, { open = false }) {
            Text(stringResource(R.string.boost_explain), style = MaterialTheme.typography.bodySmall, color = Rs.colors.muted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            VolumeBoost.STEPS.forEach { s ->
                DropdownMenuItem(
                    text = { Text(if (s == 0) stringResource(R.string.boost_off) else "+$s dB", fontWeight = if (s == db) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { open = false; db = s; VolumeBoost.set(ctx, id, s) },
                )
            }
        }
    }
}
