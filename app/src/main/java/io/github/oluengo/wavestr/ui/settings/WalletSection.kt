package io.github.oluengo.wavestr.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oluengo.wavestr.AppContainer
import io.github.oluengo.wavestr.R
import io.github.oluengo.wavestr.nostr.NwcConnection
import io.github.oluengo.wavestr.ui.theme.Rs
import kotlinx.coroutines.launch
import java.text.NumberFormat

/** Ajustes → Zaps: conectar la cartera por NWC, límite mensual e importe por defecto. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WalletSection(c: AppContainer) {
    val w by c.wallet.state.collectAsStateWithLifecycle()
    val fmt = remember { NumberFormat.getIntegerInstance() }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var connecting by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }

    Group {
        val conn = w.connection
        if (conn == null) {
            Text(stringResource(R.string.wallet_none), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.wallet_none_sub), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { connecting = true }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.wallet_connect)) }
        } else {
            Text(conn.label, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.wallet_connected_sub, fmt.format(w.spentThisMonth), fmt.format(w.budgetSats)), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = {
                    testing = true
                    scope.launch {
                        val ok = c.wallet.test()
                        testing = false
                        Toast.makeText(ctx, ctx.getString(if (ok) R.string.wallet_test_ok else R.string.wallet_test_fail), Toast.LENGTH_LONG).show()
                    }
                }, enabled = !testing) { Text(stringResource(R.string.wallet_test)) }
                if (testing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                TextButton(onClick = { c.wallet.disconnect() }) { Text(stringResource(R.string.wallet_disconnect), color = Rs.colors.live) }
            }
        }
        HorizontalDivider(color = Rs.colors.line, modifier = Modifier.padding(vertical = 10.dp))
        Text(stringResource(R.string.wallet_budget), fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1_000L, 10_000L, 50_000L, 100_000L).forEach { b ->
                FilterChip(selected = w.budgetSats == b, onClick = { c.wallet.setBudget(b) }, label = { Text(fmt.format(b)) })
            }
        }
        Text(stringResource(R.string.wallet_budget_sub), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(color = Rs.colors.line, modifier = Modifier.padding(vertical = 10.dp))
        Text(stringResource(R.string.wallet_default_zap), fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(21L, 100L, 500L, 1_000L).forEach { a ->
                FilterChip(selected = w.defaultZap == a, onClick = { c.wallet.setDefaultZap(a) }, label = { Text(fmt.format(a)) })
            }
        }
    }

    if (connecting) ConnectDialog(onDismiss = { connecting = false }, onSave = { uri ->
        val conn = c.wallet.connect(uri)
        if (conn != null) {
            connecting = false
            scope.launch {
                val ok = c.wallet.test(conn)
                Toast.makeText(ctx, ctx.getString(if (ok) R.string.wallet_connected_ok else R.string.wallet_saved_untested), Toast.LENGTH_LONG).show()
            }
            true
        } else false
    })
}

@Composable
private fun ConnectDialog(onDismiss: () -> Unit, onSave: (String) -> Boolean) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wallet_connect)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.wallet_connect_help), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = text, onValueChange = { text = it.trim(); error = false },
                    placeholder = { Text("nostr+walletconnect://…") }, isError = error, minLines = 2, maxLines = 4,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    supportingText = { if (error) Text(stringResource(R.string.wallet_invalid)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { clipboard.getText()?.text?.let { text = it.trim(); error = false } }) { Text(stringResource(R.string.paste)) }
                Text(stringResource(R.string.wallet_security_note), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (NwcConnection.parse(text) == null || !onSave(text)) error = true }) { Text(stringResource(R.string.wallet_connect_short)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
