package io.github.oluengo.wavestr.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oluengo.wavestr.AppContainer
import io.github.oluengo.wavestr.R
import io.github.oluengo.wavestr.data.AppLanguage
import io.github.oluengo.wavestr.data.AppSettings
import io.github.oluengo.wavestr.data.LoginMethod
import io.github.oluengo.wavestr.data.ThemeMode
import io.github.oluengo.wavestr.nostr.Nip19
import io.github.oluengo.wavestr.nostr.RelayPool
import io.github.oluengo.wavestr.ui.common.LoginButtons
import io.github.oluengo.wavestr.ui.common.ScreenHeader
import io.github.oluengo.wavestr.ui.common.SectionLabel
import io.github.oluengo.wavestr.ui.theme.Rs
import kotlinx.coroutines.launch

/** Categorías de Ajustes: la pantalla principal es un índice; cada una se abre en su propia pantalla. */
private enum class Section(@StringRes val title: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    ACCOUNT(R.string.settings_account, Icons.Outlined.Person),
    PLAYBACK(R.string.settings_playback, Icons.Outlined.PlayCircle),
    WALLET(R.string.wallet_title, Icons.Outlined.Bolt),
    APPEARANCE(R.string.settings_appearance_lang, Icons.Outlined.Palette),
    DATA(R.string.settings_data, Icons.Outlined.FolderOpen),
    RELAYS(R.string.settings_relays, Icons.Outlined.Hub),
    ABOUT(R.string.settings_about, Icons.Outlined.Info),
}

@Composable
fun SettingsScreen(c: AppContainer, onOpenMe: () -> Unit) {
    var section by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Section?>(null) }
    androidx.activity.compose.BackHandler(enabled = section != null) { section = null }
    when (val sec = section) {
        null -> SettingsIndex(c, onOpenMe) { section = it }
        else -> SettingsDetail(c, sec, onOpenMe) { section = null }
    }
}

@Composable
private fun SettingsIndex(c: AppContainer, onOpenMe: () -> Unit, onOpen: (Section) -> Unit) {
    val s by c.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val w by c.wallet.state.collectAsStateWithLifecycle()
    val profile = io.github.oluengo.wavestr.ui.common.rememberMyProfile(c).second
    val fmt = remember { java.text.NumberFormat.getIntegerInstance() }

    val themeLabel = stringResource(when (s.theme) { ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark; ThemeMode.SYSTEM -> R.string.theme_system })
    val accentLabel = stringResource(when (s.accent) {
        io.github.oluengo.wavestr.data.Accent.VIOLET -> R.string.accent_violet
        io.github.oluengo.wavestr.data.Accent.GREEN -> R.string.accent_green
        io.github.oluengo.wavestr.data.Accent.CORAL -> R.string.accent_coral
        io.github.oluengo.wavestr.data.Accent.BLUE -> R.string.accent_blue
        io.github.oluengo.wavestr.data.Accent.RASPBERRY -> R.string.accent_raspberry
    })
    val langLabel = when (AppLanguage.current()) { "ca" -> "Català"; "es" -> "Español"; "en" -> "English"; else -> stringResource(R.string.lang_system) }

    fun summary(sec: Section): String = when (sec) {
        Section.ACCOUNT -> s.pubkey?.let { pk ->
            (profile?.bestName ?: Nip19.shortNpub(pk)) + " · " + c.appContext.getString(if (s.loginMethod == LoginMethod.AMBER) R.string.account_amber else R.string.account_readonly)
        } ?: c.appContext.getString(R.string.account_none)
        Section.PLAYBACK -> c.appContext.getString(R.string.sum_buffer, bufferText(c, s.bufferMode))
        Section.WALLET -> w.connection?.let { it.label + " · " + c.appContext.getString(R.string.sum_wallet, fmt.format(w.spentThisMonth), fmt.format(w.budgetSats)) }
            ?: c.appContext.getString(R.string.wallet_none)
        Section.APPEARANCE -> "$themeLabel · $accentLabel · $langLabel"
        Section.DATA -> if (s.favSync == true) c.appContext.getString(R.string.fav_sync_title) else c.appContext.getString(R.string.sum_backup)
        Section.RELAYS -> c.appContext.resources.getQuantityString(R.plurals.sum_relays, (s.appRelays ?: RelayPool.BUILTIN).size, (s.appRelays ?: RelayPool.BUILTIN).size)
        Section.ABOUT -> c.appContext.getString(R.string.version, io.github.oluengo.wavestr.BuildConfig.VERSION_NAME)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(stringResource(R.string.tab_settings), null) { io.github.oluengo.wavestr.ui.common.MeButton(c, onOpenMe) }
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                listOf(Section.ACCOUNT, Section.WALLET),
                listOf(Section.PLAYBACK, Section.APPEARANCE),
                listOf(Section.DATA, Section.RELAYS),
                listOf(Section.ABOUT),
            ).forEach { block ->
                Group {
                    block.forEachIndexed { i, sec ->
                        if (i > 0) HorizontalDivider(color = Rs.colors.line)
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpen(sec) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            androidx.compose.foundation.layout.Box(
                                Modifier.size(38.dp).background(Rs.colors.accentSoft, androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Icon(sec.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(sec.title), fontWeight = FontWeight.SemiBold)
                                Text(summary(sec), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Rs.colors.muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDetail(c: AppContainer, sec: Section, onOpenMe: () -> Unit, onBack: () -> Unit) {
    val s by c.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(sec.title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (sec) {
                Section.ACCOUNT -> AccountGroup(c, s, onOpenMe)
                Section.PLAYBACK -> Group {
                    ToggleRow(R.string.set_audio_screen_off, R.string.set_audio_screen_off_sub, s.audioOnScreenOff) { scope.launch { c.settings.setAudioOnScreenOff(it) } }
                    HorizontalDivider(color = Rs.colors.line)
                    ToggleRow(R.string.set_audio_cell, R.string.set_audio_cell_sub, s.audioOnCellular) { scope.launch { c.settings.setAudioOnCellular(it) } }
                    HorizontalDivider(color = Rs.colors.line)
                    BufferPicker(s.bufferMode) { m -> scope.launch { c.settings.setBufferMode(m) } }
                }
                Section.WALLET -> WalletSection(c)
                Section.APPEARANCE -> {
                    Group {
                        Text(stringResource(R.string.settings_theme), fontWeight = FontWeight.SemiBold)
                        val themes = listOf(ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark, ThemeMode.SYSTEM to R.string.theme_system)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            themes.forEachIndexed { i, (mode, label) ->
                                SegmentedButton(selected = s.theme == mode, onClick = { scope.launch { c.settings.setTheme(mode) } }, shape = SegmentedButtonDefaults.itemShape(i, themes.size)) { Text(stringResource(label)) }
                            }
                        }
                    }
                    AccentPicker(s.accent) { a -> scope.launch { c.settings.setAccent(a) } }
                    SectionLabel(stringResource(R.string.settings_language))
                    LanguagePicker()
                }
                Section.DATA -> {
                    SectionLabel(stringResource(R.string.fav_sync_title))
                    io.github.oluengo.wavestr.ui.favorites.FavSyncSection(c)
                    SectionLabel(stringResource(R.string.backup_title))
                    BackupSection(c)
                }
                Section.RELAYS -> RelaysSection(c)
                Section.ABOUT -> AboutSection()
            }
        }
    }
}

@Composable
private fun AccountGroup(c: AppContainer, s: AppSettings, onOpenMe: () -> Unit) {
    val scope = rememberCoroutineScope()
    Group {
        val pk = s.pubkey
        if (pk == null) {
            Text(stringResource(R.string.account_none), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.login_prompt_body), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
            LoginButtons(c, Modifier.padding(top = 10.dp))
        } else {
            val profile = io.github.oluengo.wavestr.ui.common.rememberMyProfile(c).second
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpenMe).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                io.github.oluengo.wavestr.ui.common.MeButton(c, onOpenMe)
                Column(Modifier.weight(1f)) {
                    Text(profile?.bestName ?: Nip19.shortNpub(pk), fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(if (s.loginMethod == LoginMethod.AMBER) R.string.account_amber else R.string.account_readonly),
                        color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text("›", color = Rs.colors.muted)
            }
            OutlinedButton(onClick = { scope.launch { c.settings.logout() } }, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.logout))
            }
        }
    }
}

private fun bufferText(c: AppContainer, m: io.github.oluengo.wavestr.player.BufferMode): String = when (m) {
    io.github.oluengo.wavestr.player.BufferMode.AUTO -> c.appContext.getString(R.string.buffer_auto)
    io.github.oluengo.wavestr.player.BufferMode.LIVE -> c.appContext.getString(R.string.buffer_live)
    else -> c.appContext.getString(R.string.buffer_seconds, (io.github.oluengo.wavestr.player.BufferPolicy.marginMs(m) / 1000).toInt())
}

@Composable
private fun LanguagePicker() {
    // Se lee al componer; AppCompat recrea la Activity al cambiar, así que no hace falta observarlo.
    var current by remember { mutableStateOf(AppLanguage.current()) }
    val options = listOf<Pair<String?, String>>(
        null to stringResource(R.string.lang_system),
        "ca" to "Català",
        "es" to "Español",
        "en" to "English",
    )
    Group {
        options.forEach { (tag, label) ->
            Row(
                Modifier.fillMaxWidth()
                    .selectable(selected = current == tag, role = Role.RadioButton, onClick = {
                        current = tag
                        AppLanguage.set(tag)
                    })
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = current == tag, onClick = null)
                Text(label, modifier = Modifier.padding(start = 12.dp))
            }
        }
        Text(stringResource(R.string.lang_note), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ToggleRow(@StringRes title: Int, @StringRes sub: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = checked, role = Role.Switch) { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), fontWeight = FontWeight.SemiBold)
            Text(stringResource(sub), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun Group(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, Rs.colors.line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun AccentPicker(current: io.github.oluengo.wavestr.data.Accent, onPick: (io.github.oluengo.wavestr.data.Accent) -> Unit) {
    val swatches = listOf(
        io.github.oluengo.wavestr.data.Accent.VIOLET to (androidx.compose.ui.graphics.Color(0xFF6D28D9) to R.string.accent_violet),
        io.github.oluengo.wavestr.data.Accent.GREEN to (androidx.compose.ui.graphics.Color(0xFF0E7C6B) to R.string.accent_green),
        io.github.oluengo.wavestr.data.Accent.CORAL to (androidx.compose.ui.graphics.Color(0xFFD9480F) to R.string.accent_coral),
        io.github.oluengo.wavestr.data.Accent.BLUE to (androidx.compose.ui.graphics.Color(0xFF1D4ED8) to R.string.accent_blue),
        io.github.oluengo.wavestr.data.Accent.RASPBERRY to (androidx.compose.ui.graphics.Color(0xFFBE185D) to R.string.accent_raspberry),
    )
    Group {
        Text(stringResource(R.string.settings_accent), fontWeight = FontWeight.SemiBold)
        Text(stringResource(swatches.first { it.first == current }.second.second), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 10.dp)) {
            swatches.forEach { (a, pair) ->
                val (color, label) = pair
                val name = stringResource(label)
                androidx.compose.foundation.layout.Box(
                    Modifier.size(34.dp)
                        .border(if (a == current) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, androidx.compose.foundation.shape.CircleShape)
                        .padding(if (a == current) 5.dp else 0.dp)
                        .background(color, androidx.compose.foundation.shape.CircleShape)
                        .selectable(selected = a == current, role = Role.RadioButton, onClick = { onPick(a) })
                        .semantics { contentDescription = name },
                )
            }
        }
    }
}

/** Margen contra cortes: Auto, Directo, 10, 20 o 30 s. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BufferPicker(current: io.github.oluengo.wavestr.player.BufferMode, onPick: (io.github.oluengo.wavestr.player.BufferMode) -> Unit) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Text(stringResource(R.string.buffer_title), fontWeight = FontWeight.SemiBold)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            io.github.oluengo.wavestr.player.BufferMode.entries.forEach { m ->
                androidx.compose.material3.FilterChip(selected = current == m, onClick = { onPick(m) }, label = { Text(bufferLabel(m)) })
            }
        }
        Text(stringResource(R.string.buffer_sub), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun bufferLabel(m: io.github.oluengo.wavestr.player.BufferMode): String = when (m) {
    io.github.oluengo.wavestr.player.BufferMode.AUTO -> stringResource(R.string.buffer_auto)
    io.github.oluengo.wavestr.player.BufferMode.LIVE -> stringResource(R.string.buffer_live)
    else -> stringResource(R.string.buffer_seconds, (io.github.oluengo.wavestr.player.BufferPolicy.marginMs(m) / 1000).toInt())
}
