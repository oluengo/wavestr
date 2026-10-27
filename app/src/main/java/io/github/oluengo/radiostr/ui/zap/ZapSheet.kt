package io.github.oluengo.radiostr.ui.zap

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.oluengo.radiostr.AppContainer
import io.github.oluengo.radiostr.R
import io.github.oluengo.radiostr.data.AppSettings
import io.github.oluengo.radiostr.data.LoginMethod
import io.github.oluengo.radiostr.nostr.Amber
import io.github.oluengo.radiostr.ui.theme.Rs
import java.text.NumberFormat

private val PRESETS = listOf(21L, 100L, 500L, 1_000L, 5_000L)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZapSheet(c: AppContainer, target: ZapViewModel.Target, onDismiss: () -> Unit, onZapped: () -> Unit) {
    val vm: ZapViewModel = viewModel(key = "zap:" + target.hostPubkey, factory = viewModelFactory { initializer { ZapViewModel(c) } })
    val step by vm.step.collectAsStateWithLifecycle()
    val wallet by c.wallet.state.collectAsStateWithLifecycle()
    val settings by c.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val fmt = remember { NumberFormat.getIntegerInstance() }
    var amount by rememberSaveable { mutableStateOf(wallet.defaultZap) }
    var custom by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val amber = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val s = vm.step.value as? ZapViewModel.Step.NeedsSignature
        vm.onSigned(s?.let { Amber.parseSigned(res.data, it.unsigned) })
    }
    var openedWallet by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(step) {
        when (val s = step) {
            is ZapViewModel.Step.NeedsSignature -> runCatching { amber.launch(Amber.signIntent(s.unsigned, settings.pubkey!!, s.signerPackage)) }
                .onFailure { vm.onSigned(null) }
            is ZapViewModel.Step.PayExternally -> if (openedWallet != s.invoice) {
                openedWallet = s.invoice
                try {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("lightning:" + s.invoice)))
                } catch (_: ActivityNotFoundException) {
                }
            }
            is ZapViewModel.Step.Done -> onZapped()
            else -> Unit
        }
    }

    ModalBottomSheet(onDismissRequest = { vm.reset(); onDismiss() }, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.zap_title, target.hostName), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(target.title, color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)

            when (val s = step) {
                is ZapViewModel.Step.Done -> {
                    Text(stringResource(R.string.zap_sent, fmt.format(s.sats), target.hostName), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.zap_sent_note), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { vm.reset(); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.close)) }
                    return@Column
                }
                is ZapViewModel.Step.PayExternally -> {
                    Text(stringResource(R.string.zap_pay_external), style = MaterialTheme.typography.bodyMedium)
                    Text(s.invoice, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, color = Rs.colors.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("lightning:" + s.invoice))) } catch (_: ActivityNotFoundException) {
                                Toast.makeText(ctx, ctx.getString(R.string.zap_no_wallet_app), Toast.LENGTH_LONG).show()
                            }
                        }) { Text(stringResource(R.string.zap_open_wallet)) }
                        OutlinedButton(onClick = {
                            clipboard.setText(AnnotatedString(s.invoice))
                            Toast.makeText(ctx, ctx.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                        }) { Text(stringResource(R.string.zap_copy_invoice)) }
                    }
                    Text(stringResource(R.string.zap_external_tip), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { vm.reset(); onDismiss() }) { Text(stringResource(R.string.close)) }
                    return@Column
                }
                else -> Unit
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESETS.forEach { p ->
                    FilterChip(selected = custom.isEmpty() && amount == p, onClick = { amount = p; custom = "" }, label = { Text(fmt.format(p)) })
                }
            }
            OutlinedTextField(
                value = custom,
                onValueChange = { v -> custom = v.filter(Char::isDigit).take(7); custom.toLongOrNull()?.let { amount = it } },
                label = { Text(stringResource(R.string.zap_other)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = note, onValueChange = { note = it.take(200) },
                label = { Text(stringResource(R.string.zap_comment)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            val conn = wallet.connection
            Text(
                if (conn != null) stringResource(R.string.zap_via_nwc, conn.label, fmt.format(wallet.leftThisMonth), fmt.format(wallet.budgetSats))
                else stringResource(R.string.zap_via_external),
                color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall,
            )
            if (settings.loginMethod != LoginMethod.AMBER) {
                Text(stringResource(R.string.zap_anonymous), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
            }
            (step as? ZapViewModel.Step.Failed)?.let { f -> Text(failureText(f.error), color = Rs.colors.live, style = MaterialTheme.typography.bodySmall) }
            val working = step is ZapViewModel.Step.Working || step is ZapViewModel.Step.NeedsSignature
            Button(
                onClick = { vm.send(target, amount, note) },
                enabled = !working && amount > 0,
                colors = ButtonDefaults.buttonColors(containerColor = Rs.colors.zap, contentColor = Color(0xFF1B1300)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF1B1300))
                else Text(stringResource(R.string.zap_send, fmt.format(amount)), fontWeight = FontWeight.Bold)
            }
            Text(stringResource(R.string.zap_custody_note), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun failureText(f: ZapViewModel.ZapFailure): String = when (f) {
    ZapViewModel.ZapFailure.NoAddress -> stringResource(R.string.zap_err_no_address)
    ZapViewModel.ZapFailure.AddressUnreachable -> stringResource(R.string.zap_err_unreachable)
    is ZapViewModel.ZapFailure.OutOfRange -> stringResource(R.string.zap_err_range, f.min, f.max)
    is ZapViewModel.ZapFailure.InvoiceFailed -> stringResource(R.string.zap_err_invoice) + (f.reason?.let { " ($it)" } ?: "")
    ZapViewModel.ZapFailure.InvoiceMismatch -> stringResource(R.string.zap_err_mismatch)
    ZapViewModel.ZapFailure.SignatureCancelled -> stringResource(R.string.zap_err_signature)
    is ZapViewModel.ZapFailure.OverBudget -> stringResource(R.string.zap_err_budget, f.left)
    is ZapViewModel.ZapFailure.Wallet -> stringResource(R.string.zap_err_wallet) + (f.message?.let { " ($it)" } ?: "")
    ZapViewModel.ZapFailure.Timeout -> stringResource(R.string.zap_err_timeout)
}
