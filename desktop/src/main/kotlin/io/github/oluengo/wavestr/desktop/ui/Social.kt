@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.oluengo.wavestr.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.oluengo.wavestr.crypto.Secp256k1
import io.github.oluengo.wavestr.data.FavItem
import io.github.oluengo.wavestr.data.Shared
import io.github.oluengo.wavestr.desktop.DesktopContainer
import io.github.oluengo.wavestr.desktop.DesktopFavSync
import io.github.oluengo.wavestr.desktop.DesktopReminders
import io.github.oluengo.wavestr.desktop.DesktopSigner
import io.github.oluengo.wavestr.desktop.DesktopWallet
import io.github.oluengo.wavestr.desktop.I18n
import io.github.oluengo.wavestr.desktop.t
import io.github.oluengo.wavestr.nostr.EventSigning
import io.github.oluengo.wavestr.nostr.Kinds
import io.github.oluengo.wavestr.nostr.Lnurl
import io.github.oluengo.wavestr.nostr.Nip19
import io.github.oluengo.wavestr.nostr.Profile
import io.github.oluengo.wavestr.nostr.Scheduled
import io.github.oluengo.wavestr.nostr.ZapClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

// ======================= Cuenta =======================

/** Entrar con bunker (firma) o con npub (solo lectura); con sesión, quién eres y cómo entraste. */
@Composable
fun AccountSection(c: DesktopContainer, pubkey: String?) {
    val s by c.settings.flow.collectAsState()
    var npub by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    var bunkerOpen by remember { mutableStateOf(false) }
    Group {
        if (pubkey == null) {
            Text(t("account_none"), fontWeight = FontWeight.SemiBold)
            Text(t("bunker_explain"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { bunkerOpen = true }, modifier = Modifier.padding(top = 8.dp)) { Text(t("login_bunker")) }
            HorizontalDivider(color = Rs.colors.line, modifier = Modifier.padding(vertical = 10.dp))
            Text(t("npub_explain"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(
                    npub, { npub = it.trim(); invalid = false }, singleLine = true, isError = invalid,
                    placeholder = { Text("npub1…") }, modifier = Modifier.weight(1f),
                    supportingText = { if (invalid) Text(t("npub_invalid")) },
                )
                TextButton(onClick = {
                    val hex = Nip19.normalizePubkey(npub)
                    if (hex == null) invalid = true else { c.settings.login(hex); npub = "" }
                }) { Text(t("login_npub")) }
            }
        } else {
            val profile by produceState<Profile?>(null, pubkey) {
                value = runCatching { c.nostr.profiles(listOf(pubkey), pubkey)[pubkey] }.getOrNull()
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Artwork(profile?.picture, pubkey, profile?.bestName ?: "?", Modifier.size(44.dp), corner = 22.dp)
                Column(Modifier.weight(1f)) {
                    Text(profile?.bestName ?: Nip19.shortNpub(pubkey), fontWeight = FontWeight.SemiBold)
                    Text(t(if (s.bunker) "account_bunker" else "account_readonly"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = { c.settings.logout() }) { Text(t("logout")) }
            }
            Text(Nip19.hexToNpub(pubkey), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = Rs.colors.muted, modifier = Modifier.padding(top = 8.dp))
            if (!s.bunker) TextButton(onClick = { bunkerOpen = true }) { Text(t("login_bunker")) }
        }
    }
    if (bunkerOpen) BunkerDialog(c) { bunkerOpen = false }
}

@Composable
private fun BunkerDialog(c: DesktopContainer, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!working) onDone() },
        title = { Text(t("login_bunker")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(460.dp)) {
                Text(t("bunker_explain"))
                OutlinedTextField(
                    text, { text = it.trim(); error = null }, placeholder = { Text("bunker://…") }, singleLine = true, enabled = !working,
                    isError = error != null, supportingText = { error?.let { Text(it) } }, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { TextButton(onClick = { clipboard.getText()?.text?.let { text = it.trim() } }, enabled = !working) { Text(t("paste")) } },
                )
                if (working) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(t("bunker_connecting"), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !working && text.isNotBlank(), onClick = {
                working = true
                scope.launch {
                    when (val r = c.signer.login(text)) {
                        is DesktopSigner.Result.Ok -> onDone()
                        DesktopSigner.Result.BadUri -> error = t("bunker_bad_uri")
                        is DesktopSigner.Result.Failed -> error = t("bunker_failed") + (r.message?.let { " ($it)" } ?: "")
                    }
                    working = false
                }
            }) { Text(t("bunker_connect")) }
        },
        dismissButton = { TextButton(onClick = onDone, enabled = !working) { Text(t("cancel")) } },
    )
}

// ======================= Cartera NWC =======================

@Composable
fun WalletSection(c: DesktopContainer) {
    val w by c.wallet.state.collectAsState()
    val scope = rememberCoroutineScope()
    val fmt = remember { NumberFormat.getIntegerInstance(I18n.locale) }
    var uri by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    Group {
        val conn = w.connection
        if (conn == null) {
            Text(t("wallet_none"), fontWeight = FontWeight.SemiBold)
            Text(t("desk_wallet_none_sub"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
            Text(t("wallet_connect_help"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(uri, { uri = it.trim(); msg = null }, singleLine = true, placeholder = { Text("nostr+walletconnect://…") }, modifier = Modifier.weight(1f))
                TextButton(enabled = uri.isNotBlank() && !testing, onClick = {
                    val cn = c.wallet.connect(uri)
                    if (cn == null) { msg = t("wallet_invalid"); return@TextButton }
                    uri = ""; testing = true
                    scope.launch { msg = t(if (c.wallet.test(cn)) "wallet_connected_ok" else "wallet_saved_untested"); testing = false }
                }) { Text(t("wallet_connect_short")) }
            }
            Text(t("desk_wallet_security"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(conn.lud16 ?: "NWC", fontWeight = FontWeight.SemiBold)
                    Text(t("wallet_connected_sub", fmt.format(w.spentThisMonth), fmt.format(w.budgetSats)), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(enabled = !testing, onClick = { testing = true; scope.launch { msg = t(if (c.wallet.test()) "wallet_test_ok" else "wallet_test_fail"); testing = false } }) { Text(t("wallet_test")) }
                TextButton(onClick = { c.wallet.disconnect(); msg = null }) { Text(t("wallet_disconnect"), color = Rs.colors.live) }
            }
            HorizontalDivider(color = Rs.colors.line, modifier = Modifier.padding(vertical = 10.dp))
            Text(t("wallet_budget"), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                listOf(1_000L, 5_000L, 10_000L, 50_000L, 100_000L).forEach { b ->
                    FilterChip(selected = w.budgetSats == b, onClick = { c.wallet.setBudget(b) }, label = { Text(fmt.format(b)) })
                }
            }
            Text(t("wallet_budget_sub"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider(color = Rs.colors.line, modifier = Modifier.padding(vertical = 10.dp))
            Text(t("wallet_default_zap"), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                ZAP_AMOUNTS.forEach { a -> FilterChip(selected = w.defaultZap == a, onClick = { c.wallet.setDefaultZap(a) }, label = { Text(fmt.format(a)) }) }
            }
        }
        msg?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
    }
}

// ======================= Favoritos en Nostr =======================

@Composable
fun FavSyncSection(c: DesktopContainer) {
    val s by c.settings.flow.collectAsState()
    val st by c.favSync.state.collectAsState()
    Group {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("fav_sync_toggle"), fontWeight = FontWeight.SemiBold)
                Text(t(if (s.bunker) "desk_fav_sync_sub" else "desk_fav_sync_needs_bunker"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = s.favSync && s.bunker, onCheckedChange = { c.settings.setFavSync(it) }, enabled = s.bunker)
        }
        if (s.favSync && s.bunker) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Text(
                when (st.status) {
                    DesktopFavSync.Status.WORKING -> t("fav_sync_working")
                    DesktopFavSync.Status.DONE, DesktopFavSync.Status.IDLE -> if (st.lastSync > 0) t("desk_fav_sync_done", java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT, I18n.locale).format(java.util.Date(st.lastSync))) else ""
                    DesktopFavSync.Status.TOO_BIG -> t("desk_fav_sync_too_big")
                    DesktopFavSync.Status.NO_SIGNER -> t("desk_fav_sync_no_signer")
                    DesktopFavSync.Status.FAILED -> t("desk_fav_sync_failed")
                    DesktopFavSync.Status.OFF -> ""
                },
                color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { c.favSync.syncNow() }, enabled = st.status != DesktopFavSync.Status.WORKING) { Text(t("fav_sync_now")) }
        }
    }
}

// ======================= Zaps =======================

val ZAP_AMOUNTS = listOf(21L, 100L, 500L, 1_000L, 5_000L)

/** A quién va el zap: al stream o a un mensaje concreto del chat ([eventId]). */
data class ZapTarget(val pubkey: String, val name: String, val address: String?, val title: String, val eventId: String? = null)

/**
 * Zap: LNURL del destinatario → petición de zap (firmada con el bunker o, sin él, con una clave de un
 * solo uso: anónimo) → factura (se comprueba el importe) → pago con NWC, o código QR para pagar con la
 * cartera del móvil.
 */
@Composable
fun ZapDialog(c: DesktopContainer, target: ZapTarget, onDismiss: () -> Unit, onZapped: () -> Unit = {}) {
    val w by c.wallet.state.collectAsState()
    val s by c.settings.flow.collectAsState()
    val scope = rememberCoroutineScope()
    val fmt = remember { NumberFormat.getIntegerInstance(I18n.locale) }
    var amount by remember { mutableStateOf(w.defaultZap) }
    var other by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<Long?>(null) }
    var invoice by remember { mutableStateOf<String?>(null) }
    val sats = other.toLongOrNull()?.takeIf { it > 0 } ?: amount

    fun send() {
        working = true; error = null
        scope.launch {
            try {
                val me = s.pubkey
                val lud16 = c.nostr.profiles(listOf(target.pubkey), me)[target.pubkey]?.lud16
                val pay = c.zaps.payInfo(lud16)
                val msats = sats * 1000
                c.zaps.checkAmount(pay, msats)
                val lnurl = Lnurl.encode(Lnurl.endpointFor(lud16!!)!!)
                val note = comment.trim().take(200)
                val request = if (!pay.allowsNostr) null else {
                    val relays = c.nostr.relaysFor(me).take(5)
                    val tags = Lnurl.zapRequestTags(target.pubkey, target.address, msats, lnurl, relays, target.eventId)
                    if (me != null && s.bunker) c.signer.sign(EventSigning.unsigned(me, Kinds.ZAP_REQUEST, tags, note))
                        ?: run { error = t("desk_zap_err_signature"); working = false; return@launch }
                    else EventSigning.sign(Kinds.ZAP_REQUEST, tags, note, Secp256k1.randomSecret())
                }
                val inv = c.zaps.invoice(pay, msats, request, lnurl, note)
                if (w.connection == null) { invoice = inv; working = false; return@launch }
                when (val r = c.wallet.pay(inv, sats)) {
                    DesktopWallet.PayResult.Paid -> { done = sats; onZapped() }
                    is DesktopWallet.PayResult.OverBudget -> error = t("zap_err_budget", r.left)
                    is DesktopWallet.PayResult.WalletError -> error = t("zap_err_wallet") + (r.message?.let { " ($it)" } ?: "")
                    DesktopWallet.PayResult.Timeout -> error = t("zap_err_timeout")
                    DesktopWallet.PayResult.NotConnected -> invoice = inv
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = when (e) {
                    ZapClient.ZapError.NoLightningAddress -> t("zap_err_no_address")
                    is ZapClient.ZapError.AmountOutOfRange -> t("zap_err_range", e.minSats, e.maxSats)
                    is ZapClient.ZapError.InvoiceFailed -> t("zap_err_invoice")
                    ZapClient.ZapError.InvoiceMismatch -> t("zap_err_mismatch")
                    else -> t("zap_err_unreachable")
                }
            }
            working = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(t("zap_title", target.name)) },
        text = {
            Column(Modifier.width(440.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    done != null -> {
                        Text(t("zap_sent", fmt.format(done), target.name), fontWeight = FontWeight.SemiBold)
                        Text(t("zap_sent_note"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    invoice != null -> InvoiceView(invoice!!)
                    else -> {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ZAP_AMOUNTS.forEach { a -> FilterChip(selected = other.isBlank() && amount == a, onClick = { amount = a; other = "" }, label = { Text("⚡ " + fmt.format(a)) }) }
                        }
                        OutlinedTextField(other, { v -> other = v.filter(Char::isDigit).take(8) }, singleLine = true, label = { Text(t("zap_other")) }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(comment, { comment = it.take(200) }, label = { Text(t("zap_comment")) }, modifier = Modifier.fillMaxWidth())
                        val conn = w.connection
                        Text(
                            if (conn != null) t("zap_via_nwc", conn.lud16 ?: "NWC", fmt.format(w.leftThisMonth), fmt.format(w.budgetSats)) else t("desk_zap_via_qr"),
                            color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall,
                        )
                        if (!s.bunker) Text(t("desk_zap_anonymous"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
                        Text(t("zap_custody_note"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
                        error?.let { Text(it, color = Rs.colors.live) }
                    }
                }
            }
        },
        confirmButton = {
            if (done != null || invoice != null) TextButton(onClick = onDismiss) { Text(t("close")) }
            else TextButton(onClick = ::send, enabled = !working && sats > 0) {
                if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(t("zap_send", fmt.format(sats)))
            }
        },
        dismissButton = { if (done == null && invoice == null) TextButton(onClick = onDismiss, enabled = !working) { Text(t("cancel")) } },
    )
}

/** Factura para pagar fuera: QR (para la cartera del móvil), copiar y abrir con la cartera del ordenador. */
@Composable
private fun InvoiceView(invoice: String) {
    val clipboard = LocalClipboardManager.current
    val uri = LocalUriHandler.current
    var copied by remember { mutableStateOf(false) }
    Text(t("desk_zap_pay_qr"))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { QrCode("lightning:" + invoice.uppercase(), Modifier.size(240.dp)) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(invoice)); copied = true }) { Text(if (copied) t("copied") else t("zap_copy_invoice")) }
        OutlinedButton(onClick = { runCatching { uri.openUri("lightning:$invoice") } }) { Text(t("zap_open_wallet")) }
    }
    Text(t("zap_external_tip"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
}

/** Código QR (blanco y negro, con margen) dibujado a mano con qrcodegen. */
@Composable
fun QrCode(text: String, modifier: Modifier) {
    val qr = remember(text) { runCatching { io.nayuki.qrcodegen.QrCode.encodeText(text, io.nayuki.qrcodegen.QrCode.Ecc.MEDIUM) }.getOrNull() } ?: return
    Canvas(modifier.background(Color.White, RoundedCornerShape(8.dp)).padding(12.dp)) {
        val cell = size.minDimension / qr.size
        for (y in 0 until qr.size) for (x in 0 until qr.size) {
            if (qr.getModule(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

// ======================= Compartir =======================

/** Qué se comparte, en una línea: «12 emisoras y 2 streamers». */
fun sharedSummary(sh: Shared): String = listOfNotNull(
    sh.stations.takeIf { it > 0 }?.let { I18n.plural("share_stations", it, it) },
    sh.streamers.takeIf { it > 0 }?.let { I18n.plural("share_streamers", it, it) },
).joinToString(t("share_and"))

/** Compartir: enlace o texto (al portapapeles) o lista M3U (a un archivo). */
@Composable
fun ShareDialog(shared: Shared, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var msg by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("share_title", shared.name)) },
        text = {
            Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (shared.items.isEmpty()) { Text(t("share_empty")); return@Column }
                Text(sharedSummary(shared), color = Rs.colors.muted)
                ShareRow(t("share_link"), t("desk_share_link_sub")) {
                    clipboard.setText(AnnotatedString(t("share_link_text", shared.name, shared.link()))); msg = t("desk_share_copied")
                }
                ShareRow(t("share_m3u"), t(if (shared.stations == 0) "share_m3u_none" else "share_m3u_sub"), enabled = shared.stations > 0) {
                    val safe = shared.name.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().ifEmpty { "wavestr" }.take(40)
                    val fd = FileDialog(null as Frame?, t("share_m3u"), FileDialog.SAVE).apply { file = "$safe.m3u"; isVisible = true }
                    val f = fd.files.firstOrNull() ?: return@ShareRow
                    msg = t(if (runCatching { f.writeText(shared.toM3u()) }.isSuccess) "backup_m3u_exported" else "backup_write_error")
                }
                ShareRow(t("share_text"), t("share_text_sub")) { clipboard.setText(AnnotatedString(shared.toText())); msg = t("desk_share_copied") }
                msg?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("close")) } },
    )
}

@Composable
private fun ShareRow(title: String, sub: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, color = if (enabled) Color.Unspecified else Rs.colors.muted)
        Text(sub, color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
    }
}

/** Pegar un enlace de Wavestr para importarlo (en el ordenador no se abren solos). */
@Composable
fun ImportLinkDialog(c: DesktopContainer, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf(clipboard.getText()?.text?.takeIf { Shared.codeFrom(it) != null }.orEmpty()) }
    var bad by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("desk_import_link")) },
        text = {
            Column(Modifier.width(460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("desk_import_link_sub"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(text, { text = it.trim(); bad = false }, singleLine = true, isError = bad,
                    placeholder = { Text(Shared.LINK_BASE + "#…") }, supportingText = { if (bad) Text(t("share_bad_link")) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = {
                val sh = Shared.codeFrom(text)?.let(Shared::decode)
                if (sh == null) bad = true else { c.incomingShare.value = sh; onDismiss() }
            }) { Text(t("desk_import_preview")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } },
    )
}

/** Vista previa de lo recibido: marcar qué añadir (lo que ya es favorito sale atenuado). */
@Composable
fun IncomingShareHost(c: DesktopContainer) {
    val sh = c.incomingShare.collectAsState().value ?: return
    val lib by c.library.library.collectAsState()
    val already = remember(sh) { sh.items.filter { lib.isFavorite(it.key) }.map { it.key }.toSet() }
    var selected by remember(sh) { mutableStateOf(sh.items.map { it.key }.toSet() - already) }
    val src = remember(sh) { sh.asLibrary() }
    val name = sh.name.ifBlank { t("tab_favorites") }
    val wrapper = t("share_wrapper", name)
    val rows: List<Pair<String?, FavItem>> = remember(sh) {
        val out = ArrayList<Pair<String?, FavItem>>()
        src.itemsIn(null).forEach { out += null to it }
        fun walk(parent: String?) {
            src.subfolders(parent).forEach { f ->
                val path = src.path(f.id).joinToString(" / ") { p -> p.name }
                src.itemsIn(f.id).forEachIndexed { i, it -> out += (if (i == 0) path else null) to it }
                walk(f.id)
            }
        }
        walk(null); out
    }
    fun close() { c.incomingShare.value = null }
    AlertDialog(
        onDismissRequest = ::close,
        title = { Text(t("share_received", name)) },
        text = {
            Column(Modifier.width(480.dp)) {
                Text(sharedSummary(sh), color = Rs.colors.muted)
                if (sh.kind == Shared.Kind.ALL) Text(t("share_into_wrapper", wrapper), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
                if (rows.size > 1) Row {
                    TextButton(onClick = { selected = sh.items.map { it.key }.toSet() - already }) { Text(t("share_select_all")) }
                    TextButton(onClick = { selected = emptySet() }) { Text(t("share_select_none")) }
                }
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(rows, key = { it.second.key }) { (header, item) ->
                        val isFav = item.key in already
                        Column {
                            if (header != null) Row(Modifier.padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text(header, style = MaterialTheme.typography.labelMedium, color = Rs.colors.muted, modifier = Modifier.padding(start = 6.dp))
                            }
                            Row(
                                Modifier.fillMaxWidth().clickable(enabled = !isFav) { selected = if (item.key in selected) selected - item.key else selected + item.key }.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Checkbox(checked = item.key in selected || isFav, onCheckedChange = null, enabled = !isFav)
                                val st = item.station
                                if (st != null) StationArtwork(c, st, Modifier.size(32.dp), corner = 8.dp) else Artwork(item.image, item.key, item.title, Modifier.size(32.dp), corner = 16.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (isFav) Rs.colors.muted else Color.Unspecified)
                                    Text(t(when { isFav -> "share_already"; item.kind == FavItem.Kind.STREAMER -> "fav_kind_streamer"; else -> "fav_kind_station" }),
                                        color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selected.isNotEmpty(), onClick = {
                c.library.update { sh.importInto(it, selected, wrapper) { "f" + UUID.randomUUID().toString().take(8) } }
                close()
            }) { Text(t("share_add", selected.size)) }
        },
        dismissButton = { TextButton(onClick = ::close) { Text(t("cancel")) } },
    )
}

// ======================= Próximamente =======================

/** Hora de inicio en tu zona («Hoy, 21:00») y, si es otra, en la del organizador. */
fun scheduleTime(s: Scheduled): String {
    val zone = ZoneId.systemDefault()
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(I18n.locale)
    val at = Instant.ofEpochSecond(s.startsAt).atZone(zone)
    val today = LocalDate.now(zone)
    val d = when (at.toLocalDate()) {
        today -> t("time_today")
        today.plusDays(1) -> t("time_tomorrow")
        else -> DateTimeFormatter.ofPattern("EEE d MMM", I18n.locale).format(at)
    }
    val base = d + ", " + time.format(at)
    val other = s.otherZone(zone) ?: return base
    return base + " · " + t("time_their_zone", time.format(Instant.ofEpochSecond(s.startsAt).atZone(other)), other.id.substringAfterLast('/').replace('_', ' '))
}

/** Un directo o evento programado, con campana para el recordatorio. */
@Composable
fun UpcomingCard(c: DesktopContainer, s: Scheduled, host: Profile?) {
    val reminders by c.reminders.all.collectAsState()
    val current = reminders.firstOrNull { it.address == s.address }
    var menu by remember { mutableStateOf(false) }
    val hostName = host?.bestName ?: Nip19.shortNpub(s.hostPubkey)
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(s.image ?: host?.picture, s.address, s.title, Modifier.size(52.dp), corner = 10.dp)
        Column(Modifier.weight(1f)) {
            Text(s.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(hostName, color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            Text(scheduleTime(s), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
        Box {
            IconButton(onClick = { menu = true }) {
                if (current != null) Icon(Icons.Filled.NotificationsActive, t("reminder_edit"), tint = MaterialTheme.colorScheme.primary)
                else Icon(Icons.Outlined.NotificationAdd, t("reminder_add"), tint = Rs.colors.muted)
            }
            DropdownMenu(menu, { menu = false }) {
                Text(t("reminder_title"), style = MaterialTheme.typography.labelMedium, color = Rs.colors.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                DesktopReminders.LEADS.forEach { lead ->
                    DropdownMenuItem(
                        text = { Text(if (lead == 0) t("reminder_at_start") else t("reminder_before", if (lead >= 60) t("lead_hours", lead / 60) else t("lead_minutes", lead)),
                            fontWeight = if (current?.leadMin == lead) FontWeight.Bold else FontWeight.Normal) },
                        onClick = { menu = false; c.reminders.set(s, hostName, lead) },
                    )
                }
                if (current != null) {
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(t("reminder_remove"), color = Rs.colors.live) }, onClick = { menu = false; c.reminders.remove(s.address) })
                }
                Text(t("desk_reminder_note"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).width(260.dp))
            }
        }
    }
}

/** Recordatorio que salta: si ya está en directo, suena; si no, avisa de que aún no ha empezado. */
@Composable
fun ReminderHost(c: DesktopContainer, onOpenPlayer: () -> Unit) {
    val open = c.openStream.collectAsState().value
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(open) {
        val (address, _) = open ?: return@LaunchedEffect
        c.openStream.value = null
        val viewer = c.settings.current.pubkey
        val s = runCatching { c.nostr.stream(address, viewer) }.getOrNull()
        if (s != null && s.streamingUrl != null && s.isLiveNow(System.currentTimeMillis() / 1000)) {
            val p = runCatching { c.nostr.profiles(listOf(s.hostPubkey), viewer)[s.hostPubkey] }.getOrNull()
            c.player.play(liveNowPlaying(s, p)); onOpenPlayer()
        } else note = s?.title
    }
    note?.let { title ->
        AlertDialog(onDismissRequest = { note = null }, title = { Text(title) }, text = { Text(t("desk_reminder_not_live")) },
            confirmButton = { TextButton(onClick = { note = null }) { Text(t("close")) } })
    }
}
