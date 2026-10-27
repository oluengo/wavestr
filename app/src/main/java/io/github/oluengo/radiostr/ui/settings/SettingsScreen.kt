package io.github.oluengo.radiostr.ui.settings

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
import io.github.oluengo.radiostr.AppContainer
import io.github.oluengo.radiostr.R
import io.github.oluengo.radiostr.data.AppLanguage
import io.github.oluengo.radiostr.data.AppSettings
import io.github.oluengo.radiostr.data.LoginMethod
import io.github.oluengo.radiostr.data.ThemeMode
import io.github.oluengo.radiostr.nostr.Nip19
import io.github.oluengo.radiostr.nostr.RelayPool
import io.github.oluengo.radiostr.ui.common.LoginButtons
import io.github.oluengo.radiostr.ui.common.ScreenHeader
import io.github.oluengo.radiostr.ui.common.SectionLabel
import io.github.oluengo.radiostr.ui.theme.Rs
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(c: AppContainer, onOpenMe: () -> Unit) {
    val s by c.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(stringResource(R.string.tab_settings), null) { io.github.oluengo.radiostr.ui.common.MeButton(c, onOpenMe) }
        Column(Modifier.padding(horizontal = 20.dp)) {

            SectionLabel(stringResource(R.string.settings_appearance))
            Group {
                Text(stringResource(R.string.settings_theme), fontWeight = FontWeight.SemiBold)
                val themes = listOf(
                    ThemeMode.LIGHT to R.string.theme_light,
                    ThemeMode.DARK to R.string.theme_dark,
                    ThemeMode.SYSTEM to R.string.theme_system,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    themes.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = s.theme == mode,
                            onClick = { scope.launch { c.settings.setTheme(mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, themes.size),
                        ) { Text(stringResource(label)) }
                    }
                }
            }

            AccentPicker(s.accent) { a -> scope.launch { c.settings.setAccent(a) } }

            SectionLabel(stringResource(R.string.settings_language))
            LanguagePicker()

            SectionLabel(stringResource(R.string.settings_playback))
            Group {
                ToggleRow(R.string.set_audio_screen_off, R.string.set_audio_screen_off_sub, s.audioOnScreenOff) {
                    scope.launch { c.settings.setAudioOnScreenOff(it) }
                }
                HorizontalDivider(color = Rs.colors.line)
                ToggleRow(R.string.set_audio_cell, R.string.set_audio_cell_sub, s.audioOnCellular) {
                    scope.launch { c.settings.setAudioOnCellular(it) }
                }
            }

            SectionLabel(stringResource(R.string.settings_account))
            Group {
                val pk = s.pubkey
                if (pk == null) {
                    Text(stringResource(R.string.account_none), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.login_prompt_body), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
                    LoginButtons(c, Modifier.padding(top = 10.dp))
                } else {
                    val profile = io.github.oluengo.radiostr.ui.common.rememberMyProfile(c).second
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = onOpenMe).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        io.github.oluengo.radiostr.ui.common.MeButton(c, onOpenMe)
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

            SectionLabel(stringResource(R.string.wallet_title))
            WalletSection(c)

            SectionLabel(stringResource(R.string.fav_sync_title))
            io.github.oluengo.radiostr.ui.favorites.FavSyncSection(c)

            SectionLabel(stringResource(R.string.backup_title))
            BackupSection(c)

            SectionLabel(stringResource(R.string.settings_relays))
            Group {
                (RelayPool.DEFAULT + RelayPool.INDEXERS).forEach {
                    Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 3.dp))
                }
                Text(stringResource(R.string.relays_note), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }

            SectionLabel(stringResource(R.string.settings_about))
            AboutSection()
            Row(Modifier.padding(vertical = 24.dp)) {}
        }
    }
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
private fun AccentPicker(current: io.github.oluengo.radiostr.data.Accent, onPick: (io.github.oluengo.radiostr.data.Accent) -> Unit) {
    val swatches = listOf(
        io.github.oluengo.radiostr.data.Accent.VIOLET to (androidx.compose.ui.graphics.Color(0xFF6D28D9) to R.string.accent_violet),
        io.github.oluengo.radiostr.data.Accent.GREEN to (androidx.compose.ui.graphics.Color(0xFF0E7C6B) to R.string.accent_green),
        io.github.oluengo.radiostr.data.Accent.CORAL to (androidx.compose.ui.graphics.Color(0xFFD9480F) to R.string.accent_coral),
        io.github.oluengo.radiostr.data.Accent.BLUE to (androidx.compose.ui.graphics.Color(0xFF1D4ED8) to R.string.accent_blue),
        io.github.oluengo.radiostr.data.Accent.RASPBERRY to (androidx.compose.ui.graphics.Color(0xFFBE185D) to R.string.accent_raspberry),
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
