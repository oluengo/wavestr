package io.github.oluengo.wavestr.ui.common

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.oluengo.wavestr.AppContainer
import io.github.oluengo.wavestr.R
import io.github.oluengo.wavestr.data.LoginMethod
import io.github.oluengo.wavestr.nostr.Amber
import io.github.oluengo.wavestr.nostr.Nip19
import kotlinx.coroutines.launch

/** Botones de acceso: Amber (NIP-55), firmante remoto (NIP-46, nsecBunker…) o npub en solo lectura. */
@Composable
fun LoginButtons(c: AppContainer, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var askNpub by remember { mutableStateOf(false) }
    var askBunker by remember { mutableStateOf(false) }
    val failed = stringResource(R.string.amber_failed)

    val amber = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val login = Amber.parseLogin(res.data)
        if (login != null) {
            scope.launch { c.settings.login(login.pubkeyHex, LoginMethod.AMBER, login.signerPackage) }
        } else {
            Toast.makeText(ctx, failed, Toast.LENGTH_LONG).show()
        }
    }
    val missing = stringResource(R.string.amber_missing)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                if (Amber.isInstalled(ctx)) amber.launch(Amber.loginIntent())
                else Toast.makeText(ctx, missing, Toast.LENGTH_LONG).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.login_amber)) }
        OutlinedButton(onClick = { askBunker = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.login_bunker))
        }
        OutlinedButton(onClick = { askNpub = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.login_npub))
        }
    }

    if (askBunker) BunkerDialog(c, onDone = { askBunker = false })

    if (askNpub) NpubDialog(
        onDismiss = { askNpub = false },
        onSave = { hex -> scope.launch { c.settings.login(hex, LoginMethod.READ_ONLY) }; askNpub = false },
    )
}

@Composable
private fun NpubDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.login_npub)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.npub_explain))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = false },
                    placeholder = { Text("npub1…") },
                    singleLine = true,
                    isError = error,
                    supportingText = { if (error) Text(stringResource(R.string.npub_invalid)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val hex = Nip19.normalizePubkey(text)
                if (hex == null) error = true else onSave(hex)
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Pegar el enlace `bunker://…` del firmante remoto y conectar (puede pedir aprobarlo en su web). */
@Composable
private fun BunkerDialog(c: AppContainer, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val bad = stringResource(R.string.bunker_bad_uri)
    val failed = stringResource(R.string.bunker_failed)
    AlertDialog(
        onDismissRequest = { if (!working) onDone() },
        title = { Text(stringResource(R.string.login_bunker)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.bunker_explain))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.trim(); error = null },
                    placeholder = { Text("bunker://…") },
                    singleLine = true, enabled = !working,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    trailingIcon = {
                        TextButton(onClick = { clipboard.getText()?.text?.let { text = it.trim(); error = null } }, enabled = !working) {
                            Text(stringResource(R.string.paste))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (working) Text(stringResource(R.string.bunker_connecting), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = !working && text.isNotBlank(), onClick = {
                working = true
                scope.launch {
                    when (val r = c.signer.loginBunker(text)) {
                        is io.github.oluengo.wavestr.nostr.Signer.BunkerResult.Ok -> onDone()
                        io.github.oluengo.wavestr.nostr.Signer.BunkerResult.BadUri -> error = bad
                        is io.github.oluengo.wavestr.nostr.Signer.BunkerResult.Failed -> error = failed + (r.message?.let { " ($it)" } ?: "")
                    }
                    working = false
                }
            }) { Text(stringResource(R.string.bunker_connect)) }
        },
        dismissButton = { TextButton(onClick = onDone, enabled = !working) { Text(stringResource(R.string.cancel)) } },
    )
}
