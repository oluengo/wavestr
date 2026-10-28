package io.github.oluengo.wavestr.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oluengo.wavestr.AppContainer
import io.github.oluengo.wavestr.R
import io.github.oluengo.wavestr.data.AppSettings
import io.github.oluengo.wavestr.nostr.RelayPool
import io.github.oluengo.wavestr.ui.theme.Rs
import kotlinx.coroutines.launch

/**
 * Ajustes → Relays. Tu lista NIP-65 se muestra pero no se edita (es de tu identidad y la comparten
 * todos tus clientes). Los relays de Wavestr sí: se añaden, se quitan y se pueden restaurar.
 */
@Composable
fun RelaysSection(c: AppContainer) {
    val s by c.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()
    val mine = s.appRelays ?: RelayPool.BUILTIN
    var nip65 by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var input by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }

    LaunchedEffect(s.pubkey) {
        nip65 = s.pubkey?.let { pk -> runCatching { c.nostr.relayList(pk) }.getOrDefault(emptyList()) }
    }

    fun save(list: List<String>) = scope.launch { c.settings.setAppRelays(if (list == RelayPool.BUILTIN) null else list) }
    fun add() {
        val r = RelayPool.normalize(input)
        if (r == null) { invalid = true; return }
        if (r !in mine) save(mine + r)
        input = ""; invalid = false
    }

    var openYours by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var openApp by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    Group {
        if (s.pubkey != null) {
            CollapsibleHeader(stringResource(R.string.relays_yours), nip65?.size, openYours) { openYours = !openYours }
            val list = nip65
            if (openYours) when {
                list == null -> Text("…", color = Rs.colors.muted)
                list.isEmpty() -> Text(stringResource(R.string.relays_yours_none), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
                else -> list.forEach { (url, mark) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(url, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(when (mark) { "read" -> R.string.relay_read; "write" -> R.string.relay_write; else -> R.string.relay_both }),
                            color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (openYours) Text(stringResource(R.string.relays_yours_note), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            HorizontalDivider(color = Rs.colors.line, modifier = Modifier.padding(vertical = 10.dp))
        }

        CollapsibleHeader(stringResource(R.string.relays_app), mine.size, openApp) { openApp = !openApp }
        if (!openApp) return@Group
        Text(stringResource(R.string.relays_app_sub), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
        mine.forEach { url ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(url, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                if (url !in RelayPool.BUILTIN) Text(stringResource(R.string.relay_added), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = { save(mine - url) }, enabled = mine.size > 1) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.relay_remove, url))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            OutlinedTextField(
                value = input, onValueChange = { input = it; invalid = false },
                placeholder = { Text("wss://relay.example.com") }, singleLine = true, isError = invalid,
                supportingText = { if (invalid) Text(stringResource(R.string.relay_invalid)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { add() }, enabled = input.isNotBlank()) { Text(stringResource(R.string.relay_add)) }
        }
        if (s.appRelays != null) {
            TextButton(onClick = { save(RelayPool.BUILTIN) }) { Text(stringResource(R.string.relays_restore)) }
        }
        Text(stringResource(R.string.relays_profiles_note, RelayPool.INDEXERS.joinToString()), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
    }
}

/** Cabecera plegable: título, número de relays y flecha. */
@Composable
private fun CollapsibleHeader(title: String, count: Int?, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (count != null) Text(count.toString(), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 6.dp))
        Icon(
            if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            stringResource(if (open) R.string.collapse else R.string.expand), tint = Rs.colors.muted,
        )
    }
}
