package io.github.oluengo.wavestr.ui.common

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oluengo.wavestr.AppContainer
import io.github.oluengo.wavestr.R
import io.github.oluengo.wavestr.data.AppSettings
import io.github.oluengo.wavestr.nostr.Event
import io.github.oluengo.wavestr.nostr.EventSigning
import io.github.oluengo.wavestr.nostr.Kinds
import io.github.oluengo.wavestr.nostr.canSign
import io.github.oluengo.wavestr.nostr.followEdit
import kotlinx.coroutines.launch

/**
 * Seguir / Siguiendo a [pubkey] (NIP-02). Se edita tu lista de seguidos más reciente (kind 3)
 * conservando todo lo demás, se firma con Amber o bunker y se publica en tus relays. Si tu lista no
 * aparece en los relays no se toca sin preguntar, para no dejarte sin seguidos.
 * Sin sesión, o en tu propio perfil, no se muestra.
 */
@Composable
fun FollowButton(c: AppContainer, pubkey: String, modifier: Modifier = Modifier) {
    val s by c.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val me = s.pubkey ?: return
    if (me == pubkey) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var following by remember(me, pubkey) { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmNew by remember { mutableStateOf(false) }
    val needsSigner = stringResource(R.string.follow_needs_signer)
    val failed = stringResource(R.string.follow_failed)

    LaunchedEffect(me, pubkey) {
        following = runCatching { pubkey in c.nostr.follows(me) }.getOrNull() ?: false
    }

    suspend fun publish(current: Event?, follow: Boolean) {
        val (tags, content) = followEdit(current, pubkey, follow)
        val signed = c.signer.sign(EventSigning.unsigned(me, Kinds.CONTACTS, tags, content)) ?: return
        val relays = runCatching { (c.nostr.ownRelays(me) + c.nostr.relaysFor(me)).distinct() }
            .getOrDefault(io.github.oluengo.wavestr.nostr.RelayPool.DEFAULT)
        if (c.relays.publish(relays, signed) > 0) {
            c.nostr.rememberFollows(me, signed)
            following = follow
        } else Toast.makeText(ctx, failed, Toast.LENGTH_LONG).show()
    }

    fun toggle() {
        if (!s.loginMethod.canSign) { Toast.makeText(ctx, needsSigner, Toast.LENGTH_LONG).show(); return }
        val follow = following != true
        busy = true
        scope.launch {
            try {
                val current = runCatching { c.nostr.contactList(me) }.getOrNull()
                when {
                    current != null -> publish(current, follow)
                    follow -> confirmNew = true
                    else -> Toast.makeText(ctx, failed, Toast.LENGTH_LONG).show()
                }
            } finally { busy = false }
        }
    }

    val isOn = following == true
    val small = Modifier.height(32.dp)
    val pad = PaddingValues(horizontal = 14.dp)
    val label = stringResource(if (isOn) R.string.following_button else R.string.follow_button)
    if (isOn) FilledTonalButton(onClick = ::toggle, enabled = !busy, modifier = modifier.then(small), contentPadding = pad) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    } else Button(
        onClick = ::toggle, enabled = !busy && following != null, modifier = modifier.then(small), contentPadding = pad,
        colors = ButtonDefaults.buttonColors(),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }

    if (confirmNew) AlertDialog(
        onDismissRequest = { confirmNew = false },
        title = { Text(stringResource(R.string.follow_new_list_title)) },
        text = { Text(stringResource(R.string.follow_new_list_body)) },
        confirmButton = {
            TextButton(onClick = {
                confirmNew = false; busy = true
                scope.launch { try { publish(null, true) } finally { busy = false } }
            }) { Text(stringResource(R.string.follow_create)) }
        },
        dismissButton = { TextButton(onClick = { confirmNew = false }) { Text(stringResource(R.string.cancel)) } },
    )
}
