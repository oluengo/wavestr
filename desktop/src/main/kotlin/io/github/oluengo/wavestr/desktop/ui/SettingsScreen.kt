package io.github.oluengo.wavestr.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.oluengo.wavestr.data.Library
import io.github.oluengo.wavestr.desktop.Accent
import io.github.oluengo.wavestr.desktop.AppInfo
import io.github.oluengo.wavestr.desktop.DesktopContainer
import io.github.oluengo.wavestr.desktop.ThemeMode
import io.github.oluengo.wavestr.desktop.t
import io.github.oluengo.wavestr.nostr.Nip19
import io.github.oluengo.wavestr.nostr.NostrJson
import io.github.oluengo.wavestr.nostr.RelayPool
import kotlinx.serialization.json.JsonObject
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

private const val REPO_URL = "https://github.com/oluengo/wavestr"

@Composable
fun SettingsScreen(c: DesktopContainer) {
    val s by c.settings.flow.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(t("tab_settings"), null) { HeaderActions(c) }
        Column(Modifier.padding(horizontal = 20.dp).widthIn(max = 720.dp)) {
            SectionLabel(t("settings_appearance"))
            Group {
                Text(t("settings_theme"), fontWeight = FontWeight.SemiBold)
                val themes = listOf(ThemeMode.LIGHT to "theme_light", ThemeMode.DARK to "theme_dark", ThemeMode.SYSTEM to "theme_system")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    themes.forEachIndexed { i, (m, label) ->
                        SegmentedButton(s.theme == m, { c.settings.setTheme(m) }, SegmentedButtonDefaults.itemShape(i, themes.size)) { Text(t(label)) }
                    }
                }
                HorizontalDivider(color = Rs.colors.line, modifier = Modifier.padding(vertical = 12.dp))
                Text(t("settings_accent"), fontWeight = FontWeight.SemiBold)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Accent.entries.forEach { a ->
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(accentSwatch(a))
                                .border(3.dp, if (s.accent == a) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                                .selectable(selected = s.accent == a, role = Role.RadioButton) { c.settings.setAccent(a) },
                        )
                    }
                }
            }

            SectionLabel(t("settings_playback"))
            Group {
                Text(t("buffer_title"), fontWeight = FontWeight.SemiBold)
                Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    io.github.oluengo.wavestr.player.BufferMode.entries.forEach { m ->
                        androidx.compose.material3.FilterChip(
                            selected = s.bufferMode == m, onClick = { c.settings.setBufferMode(m) },
                            label = {
                                Text(when (m) {
                                    io.github.oluengo.wavestr.player.BufferMode.AUTO -> t("buffer_auto")
                                    io.github.oluengo.wavestr.player.BufferMode.LIVE -> t("buffer_live")
                                    else -> t("buffer_seconds", (io.github.oluengo.wavestr.player.BufferPolicy.marginMs(m) / 1000).toInt())
                                })
                            },
                        )
                    }
                }
                Text(t("buffer_sub"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
            }

            SectionLabel(t("settings_language"))
            Group {
                listOf<Pair<String?, String>>(null to t("lang_system"), "ca" to "Català", "es" to "Español", "en" to "English").forEach { (tag, label) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = s.language == tag, role = Role.RadioButton) { c.settings.setLanguage(tag) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = s.language == tag, onClick = null)
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            SectionLabel(t("settings_account"))
            AccountSection(c, s.pubkey)

            SectionLabel(t("desk_window"))
            Group {
                Row(
                    Modifier.fillMaxWidth().selectable(selected = s.keepInTray, role = Role.Switch) { c.settings.setKeepInTray(!s.keepInTray) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t("desk_keep_in_tray"), fontWeight = FontWeight.SemiBold)
                        Text(t("desk_keep_in_tray_sub"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = s.keepInTray, onCheckedChange = null)
                }
            }

            SectionLabel(t("settings_relays"))
            RelaysSection(c, s.pubkey, s.appRelays)

            SectionLabel(t("backup_title"))
            BackupSection(c)

            SectionLabel(t("settings_about"))
            AboutSection()
            Box(Modifier.padding(vertical = 24.dp))
        }
    }
}

@Composable
private fun AccountSection(c: DesktopContainer, pubkey: String?) {
    var input by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    Group {
        if (pubkey == null) {
            Text(t("account_none"), fontWeight = FontWeight.SemiBold)
            Text(t("desk_login_npub_only"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(
                    input, { input = it.trim(); invalid = false }, singleLine = true, isError = invalid,
                    placeholder = { Text("npub1…") }, modifier = Modifier.weight(1f),
                    supportingText = { if (invalid) Text(t("npub_invalid")) },
                )
                TextButton(onClick = {
                    val hex = Nip19.normalizePubkey(input)
                    if (hex == null) invalid = true else { c.settings.login(hex); input = "" }
                }) { Text(t("save")) }
            }
        } else {
            val profile by produceState<io.github.oluengo.wavestr.nostr.Profile?>(null, pubkey) {
                value = runCatching { c.nostr.profiles(listOf(pubkey), pubkey)[pubkey] }.getOrNull()
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Artwork(profile?.picture, pubkey, profile?.bestName ?: "?", Modifier.size(44.dp), corner = 22.dp)
                Column(Modifier.weight(1f)) {
                    Text(profile?.bestName ?: Nip19.shortNpub(pubkey), fontWeight = FontWeight.SemiBold)
                    Text(t("account_readonly"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = { c.settings.logout() }) { Text(t("logout")) }
            }
            Text(Nip19.hexToNpub(pubkey), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = Rs.colors.muted, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun RelaysSection(c: DesktopContainer, pubkey: String?, appRelays: List<String>?) {
    val mine = appRelays ?: RelayPool.BUILTIN
    val nip65 by produceState<List<Pair<String, String>>?>(null, pubkey) {
        value = pubkey?.let { pk -> runCatching { c.nostr.relayList(pk) }.getOrDefault(emptyList()) }
    }
    var input by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    fun save(list: List<String>) = c.settings.setAppRelays(if (list == RelayPool.BUILTIN) null else list)
    fun add() {
        val r = RelayPool.normalize(input) ?: run { invalid = true; return }
        if (r !in mine) save(mine + r)
        input = ""; invalid = false
    }
    Group {
        if (pubkey != null) {
            Text(t("relays_yours"), fontWeight = FontWeight.SemiBold)
            val list = nip65
            when {
                list == null -> Text("…", color = Rs.colors.muted)
                list.isEmpty() -> Text(t("relays_yours_none"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
                else -> list.forEach { (url, mark) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(url, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(t(when (mark) { "read" -> "relay_read"; "write" -> "relay_write"; else -> "relay_both" }), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text(t("relays_yours_note"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            HorizontalDivider(color = Rs.colors.line, modifier = Modifier.padding(vertical = 10.dp))
        }
        Text(t("relays_app"), fontWeight = FontWeight.SemiBold)
        Text(t("relays_app_sub"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
        mine.forEach { url ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(url, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                if (url !in RelayPool.BUILTIN) Text(t("relay_added"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = { save(mine - url) }, enabled = mine.size > 1) { Icon(Icons.Outlined.Close, t("relay_remove", url)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                input, { input = it; invalid = false }, singleLine = true, isError = invalid,
                placeholder = { Text("wss://relay.example.com") }, modifier = Modifier.weight(1f),
                supportingText = { if (invalid) Text(t("relay_invalid")) },
            )
            TextButton(onClick = { add() }, enabled = input.isNotBlank()) { Text(t("relay_add")) }
        }
        if (appRelays != null) TextButton(onClick = { save(RelayPool.BUILTIN) }) { Text(t("relays_restore")) }
        Text(t("relays_profiles_note", RelayPool.INDEXERS.joinToString()), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
    }
}

/** Copias: las mismas que en Android, para pasar los favoritos del móvil al ordenador y al revés. */
@Composable
private fun BackupSection(c: DesktopContainer) {
    val lib by c.library.library.collectAsState()
    var msg by remember { mutableStateOf<String?>(null) }
    val stamp = remember { SimpleDateFormat("yyyy-MM-dd").format(Date()) }

    fun chooseSave(name: String): File? {
        val fd = FileDialog(null as Frame?, t("backup_export"), FileDialog.SAVE).apply { file = name; isVisible = true }
        return fd.files.firstOrNull()
    }

    Group {
        Text(t("backup_note", lib.favorites.size, lib.folders.size, lib.imported.size), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = {
                val f = chooseSave("wavestr-$stamp.json") ?: return@OutlinedButton
                msg = t(if (runCatching { f.writeText(lib.toBackup(System.currentTimeMillis()).toString()) }.isSuccess) "backup_exported" else "backup_write_error")
            }) { Text(t("backup_export")) }
            OutlinedButton(onClick = {
                val fd = FileDialog(null as Frame?, t("backup_import"), FileDialog.LOAD).apply { isVisible = true }
                val f = fd.files.firstOrNull() ?: return@OutlinedButton
                val other = runCatching { Library.fromBackup(NostrJson.parseToJsonElement(f.readText()) as? JsonObject) }.getOrNull()
                if (other == null) { msg = t("backup_invalid"); return@OutlinedButton }
                c.library.update { it.merge(other) }
                msg = t("backup_imported", other.favorites.size, other.folders.size, other.imported.size)
            }) { Text(t("backup_import")) }
            OutlinedButton(onClick = {
                val f = chooseSave("wavestr-$stamp.m3u") ?: return@OutlinedButton
                msg = t(if (runCatching { f.writeText(lib.toM3u()) }.isSuccess) "backup_m3u_exported" else "backup_write_error")
            }) { Text("M3U") }
        }
        msg?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun AboutSection() {
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf<String?>(null) }
    Group {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(AppInfo.name, style = MaterialTheme.typography.titleLarge)
            Text(t("version", AppInfo.version), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
        }
        Text(t("about_ai"), modifier = Modifier.padding(top = 4.dp))
        HorizontalDivider(color = Rs.colors.line, modifier = Modifier.padding(vertical = 10.dp))
        Text(t("about_license_title"), fontWeight = FontWeight.SemiBold)
        Text(t("about_license_body"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
        Text(t("about_warranty"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        HorizontalDivider(color = Rs.colors.line, modifier = Modifier.padding(vertical = 10.dp))
        listOf(
            t("about_source") to REPO_URL,
            t("about_bug") to "$REPO_URL/issues/new?labels=bug",
            t("about_idea") to "$REPO_URL/issues/new?labels=enhancement",
        ).forEach { (label, url) ->
            Row(Modifier.fillMaxWidth().clickable { runCatching { uri.openUri(url) } }.padding(vertical = 8.dp)) {
                Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("↗", color = Rs.colors.muted)
            }
        }
        HorizontalDivider(color = Rs.colors.line, modifier = Modifier.padding(vertical = 10.dp))
        Text(t("support_title"), fontWeight = FontWeight.SemiBold)
        Text(t("support_body"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
        listOf(
            "₿ Bitcoin" to "bc1pnvdct6hfnh67p7dcg2e5mdveyz8jlxuzv4yflkxqccc72qkuc8ls8raspa",
            "⚡ Lightning / zaps" to "oluengo@rizful.com",
        ).forEach { (label, addr) ->
            Column(Modifier.fillMaxWidth().clickable { clipboard.setText(AnnotatedString(addr)); copied = addr }.padding(vertical = 8.dp)) {
                Text(label + if (copied == addr) " · " + t("copied") else "", fontWeight = FontWeight.SemiBold)
                Text(addr, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = Rs.colors.muted)
            }
        }
    }
}
