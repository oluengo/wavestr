package io.github.oluengo.radiostr.ui.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oluengo.radiostr.AppContainer
import io.github.oluengo.radiostr.R
import io.github.oluengo.radiostr.data.AppSettings
import io.github.oluengo.radiostr.data.LoginMethod
import io.github.oluengo.radiostr.nostr.Nip19
import io.github.oluengo.radiostr.ui.common.Artwork
import io.github.oluengo.radiostr.ui.common.LoginButtons
import io.github.oluengo.radiostr.ui.common.SectionLabel
import io.github.oluengo.radiostr.ui.common.initials
import io.github.oluengo.radiostr.ui.common.keyGradient
import io.github.oluengo.radiostr.ui.common.rememberMyProfile
import io.github.oluengo.radiostr.ui.theme.Rs
import kotlinx.coroutines.launch

/** Tu perfil Nostr, básico y de solo lectura: Radiostr no lo modifica. */
@Composable
fun MeScreen(c: AppContainer, onBack: () -> Unit) {
    val s by c.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val (pk, profile) = rememberMyProfile(c)
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    val copied = stringResource(R.string.copied)
    var follows by remember(pk) { mutableStateOf<Int?>(null) }
    var relays by remember(pk) { mutableStateOf<Int?>(null) }
    LaunchedEffect(pk) {
        if (pk != null) {
            follows = runCatching { c.nostr.follows(pk).size }.getOrNull()
            relays = runCatching { c.nostr.relaysFor(pk).size }.getOrNull()
        }
    }
    fun copy(text: String) { clipboard.setText(AnnotatedString(text)); Toast.makeText(ctx, copied, Toast.LENGTH_SHORT).show() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(130.dp).background(keyGradient(pk ?: "me"))) {
            Artwork(profile?.banner, pk ?: "me", Modifier.fillMaxSize(), corner = 0.dp)
            IconButton(
                onClick = onBack, modifier = Modifier.statusBarsPadding().padding(8.dp),
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0x59000000), contentColor = Color.White),
            ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
        }
        Column(Modifier.padding(horizontal = 20.dp).offset(y = (-40).dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(84.dp).border(4.dp, MaterialTheme.colorScheme.background, CircleShape)) {
                Artwork(profile?.picture, pk ?: "me", Modifier.size(84.dp), corner = 42.dp)
                if (profile?.picture == null) Text(initials(profile?.bestName ?: "?"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp, modifier = Modifier.align(Alignment.Center))
            }
            if (pk == null) {
                Text(stringResource(R.string.me_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.login_prompt_body), color = Rs.colors.muted)
                LoginButtons(c, Modifier.padding(top = 4.dp))
                return@Column
            }
            Text(profile?.bestName ?: Nip19.shortNpub(pk), style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                profile?.name?.let { Text("@$it", color = Rs.colors.muted) }
                profile?.nip05?.let { Text("✓ $it", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            profile?.about?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(follows?.toString() ?: "…", stringResource(R.string.me_following), Modifier.weight(1f))
                Stat(relays?.toString() ?: "…", stringResource(R.string.me_relays), Modifier.weight(1f))
                Stat(
                    if (s.loginMethod == LoginMethod.AMBER) "Amber" else "npub",
                    stringResource(if (s.loginMethod == LoginMethod.AMBER) R.string.me_signer else R.string.account_readonly),
                    Modifier.weight(1f),
                )
            }
            SectionLabel(stringResource(R.string.me_pubkey))
            CopyBox(Nip19.hexToNpub(pk)) { copy(Nip19.hexToNpub(pk)) }
            profile?.lud16?.let { ln ->
                SectionLabel(stringResource(R.string.me_lightning))
                CopyBox("⚡ $ln") { copy(ln) }
            }
            Text(stringResource(R.string.me_readonly_note), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            OutlinedButton(onClick = { scope.launch { c.settings.logout(); onBack() } }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.logout))
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, fontFamily = FontFamily.Monospace, maxLines = 1)
        Text(label, color = Rs.colors.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CopyBox(text: String, onCopy: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        TextButton(onClick = onCopy) { Text(stringResource(R.string.copy)) }
    }
}
