package io.github.oluengo.radiostr.ui.common

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
import io.github.oluengo.radiostr.AppContainer
import io.github.oluengo.radiostr.R
import io.github.oluengo.radiostr.data.LoginMethod
import io.github.oluengo.radiostr.nostr.Amber
import io.github.oluengo.radiostr.nostr.Nip19
import kotlinx.coroutines.launch

/** Botones de acceso: Amber (NIP-55) o npub en solo lectura. */
@Composable
fun LoginButtons(c: AppContainer, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var askNpub by remember { mutableStateOf(false) }
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
        OutlinedButton(onClick = { askNpub = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.login_npub))
        }
    }

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
