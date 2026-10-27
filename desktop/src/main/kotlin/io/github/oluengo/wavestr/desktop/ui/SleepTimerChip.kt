package io.github.oluengo.wavestr.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import io.github.oluengo.wavestr.desktop.t
import io.github.oluengo.wavestr.player.SleepTimer
import kotlinx.coroutines.delay

/** Temporizador de apagado: minutos para todo; «al terminar el stream» solo con streams de Nostr. */
@Composable
fun SleepTimerChip(timer: SleepTimer, isNostr: Boolean) {
    val mode by timer.mode.collectAsState()
    var open by remember { mutableStateOf(false) }
    var left by remember { mutableLongStateOf(0L) }
    LaunchedEffect(mode) {
        while (mode is SleepTimer.Mode.At) { left = timer.remainingMs() ?: 0; delay(1_000) }
    }
    val label = when (mode) {
        null -> t("sleep_timer")
        SleepTimer.Mode.StreamEnd -> t("sleep_until_stream_end")
        is SleepTimer.Mode.At -> t("sleep_in", formatLeft(left))
    }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(label) },
            leadingIcon = { Icon(Icons.Outlined.Bedtime, null, Modifier.size(18.dp)) },
            colors = if (mode != null) AssistChipDefaults.assistChipColors(containerColor = Rs.colors.accentSoft, labelColor = MaterialTheme.colorScheme.primary, leadingIconContentColor = MaterialTheme.colorScheme.primary) else AssistChipDefaults.assistChipColors(),
        )
        DropdownMenu(open, { open = false }) {
            SleepTimer.PRESETS.forEach { m ->
                DropdownMenuItem(text = { Text(t("sleep_minutes", m)) }, onClick = { open = false; timer.start(m) })
            }
            if (isNostr) DropdownMenuItem(text = { Text(t("sleep_stream_end")) }, onClick = { open = false; timer.untilStreamEnd() })
            if (mode != null) {
                HorizontalDivider()
                if (mode is SleepTimer.Mode.At) DropdownMenuItem(text = { Text(t("sleep_extend")) }, onClick = { open = false; timer.extend(10) })
                DropdownMenuItem(text = { Text(t("sleep_cancel"), color = Rs.colors.live) }, onClick = { open = false; timer.cancel() })
            }
        }
    }
}

fun formatLeft(ms: Long): String {
    val s = (ms + 999) / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
