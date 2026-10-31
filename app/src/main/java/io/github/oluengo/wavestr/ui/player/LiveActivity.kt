@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.oluengo.wavestr.ui.player

import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.oluengo.wavestr.AppContainer
import io.github.oluengo.wavestr.R
import io.github.oluengo.wavestr.nostr.ChatMessage
import io.github.oluengo.wavestr.nostr.Nip19
import io.github.oluengo.wavestr.nostr.Profile
import io.github.oluengo.wavestr.nostr.ZapReceipt
import io.github.oluengo.wavestr.nostr.totalSats
import io.github.oluengo.wavestr.ui.theme.Rs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.NumberFormat

/** Chat y zaps del directo que suena, refrescados cada 15 s mientras el reproductor está abierto. */
class LiveActivityViewModel(private val c: AppContainer) : ViewModel() {
    data class Line(val time: Long, val author: String, val text: String, val sats: Long?)
    data class UiState(val address: String? = null, val lines: List<Line> = emptyList(), val totalSats: Long = 0, val loaded: Boolean = false)

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    private val names = HashMap<String, Profile>()

    fun watch(address: String?) {
        if (address == _state.value.address) return
        job?.cancel()
        _state.value = UiState(address)
        if (address == null) return
        job = viewModelScope.launch {
            val since = System.currentTimeMillis() / 1000 - 12 * 3600
            while (isActive) {
                refreshOnce(address, since)
                delay(15_000)
            }
        }
    }

    /** Mensaje propio recién enviado: se ve al momento, sin esperar a la siguiente consulta. */
    fun addLocal(author: String, text: String) {
        _state.update { it.copy(lines = (it.lines + Line(System.currentTimeMillis() / 1000, author, text, null)).takeLast(40)) }
    }

    /** Publica un mensaje ya firmado (kind 1311) en los relays del usuario y los de serie. */
    suspend fun publish(ev: io.github.oluengo.wavestr.nostr.Event, viewer: String): Boolean {
        val relays = runCatching { c.nostr.relaysFor(viewer) }.getOrDefault(io.github.oluengo.wavestr.nostr.RelayPool.DEFAULT)
        return c.relays.publish(relays, ev) > 0
    }

    fun refreshNow() {
        val a = _state.value.address ?: return
        viewModelScope.launch { refreshOnce(a, System.currentTimeMillis() / 1000 - 12 * 3600) }
    }

    private suspend fun refreshOnce(address: String, since: Long) {
        try {
            val viewer = c.settings.current().pubkey
            val (chat, zaps) = c.nostr.activity(address, since, viewer)
            val who = (chat.map { it.pubkey } + zaps.mapNotNull { it.senderPubkey }).distinct().filter { it !in names }
            if (who.isNotEmpty()) names.putAll(c.nostr.profiles(who, viewer))
            fun name(pk: String?) = pk?.let { names[it]?.bestName ?: Nip19.shortNpub(it) } ?: "?"
            val lines = (chat.map { m: ChatMessage -> Line(m.createdAt, name(m.pubkey), m.content, null) } +
                zaps.map { z: ZapReceipt -> Line(z.createdAt, name(z.senderPubkey), z.comment, z.amountSats) })
                .sortedBy { it.time }.takeLast(40)
            _state.update { if (it.address == address) it.copy(lines = lines, totalSats = zaps.totalSats(), loaded = true) else it }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.update { it.copy(loaded = true) }
        }
    }
}

@Composable
fun LiveChat(vm: LiveActivityViewModel, c: AppContainer) {
    val st by vm.state.collectAsStateWithLifecycle()
    val fmt = remember { NumberFormat.getIntegerInstance() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider(color = Rs.colors.line)
        Text(
            stringResource(R.string.live_chat_title) + if (st.totalSats > 0) " · ⚡ " + fmt.format(st.totalSats) + " sats" else "",
            color = Rs.colors.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp),
        )
        if (st.loaded && st.lines.isEmpty()) Text(stringResource(R.string.live_chat_empty), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
        st.lines.forEach { l ->
            val zap = l.sats != null
            Row(
                (if (zap) Modifier.fillMaxWidth().background(Rs.colors.zapSoft, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 5.dp) else Modifier.fillMaxWidth()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(l.author, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                if (zap) Text("⚡ " + fmt.format(l.sats), color = Rs.colors.zap, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                if (l.text.isNotBlank()) Text(l.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
        }
        ChatInput(vm, c, st.address)
    }
}

/** Escribir en el chat del stream (kind 1311), firmado con Amber. */
@Composable
private fun ChatInput(vm: LiveActivityViewModel, c: AppContainer, address: String?) {
    val s by c.settings.flow.collectAsStateWithLifecycle(initialValue = null)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var text by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    var sending by remember { androidx.compose.runtime.mutableStateOf(false) }
    var pending by remember { androidx.compose.runtime.mutableStateOf<io.github.oluengo.wavestr.nostr.Event?>(null) }
    val me = s?.pubkey
    val canWrite = me != null && s?.loginMethod == io.github.oluengo.wavestr.data.LoginMethod.AMBER && address != null
    val failed = stringResource(R.string.chat_failed)
    val myName = io.github.oluengo.wavestr.ui.common.rememberMyProfile(c).second?.bestName ?: me?.let { Nip19.shortNpub(it) } ?: ""

    suspend fun publish(ev: io.github.oluengo.wavestr.nostr.Event) {
        val ok = vm.publish(ev, me!!)
        sending = false
        if (ok) { vm.addLocal(myName, ev.content); text = "" }
        else android.widget.Toast.makeText(ctx, failed, android.widget.Toast.LENGTH_LONG).show()
    }

    val amber = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { res ->
        val u = pending ?: return@rememberLauncherForActivityResult
        pending = null
        val signed = io.github.oluengo.wavestr.nostr.Amber.parseSigned(res.data, u)
        if (signed == null) { sending = false; return@rememberLauncherForActivityResult }
        scope.launch { publish(signed) }
    }

    if (!canWrite) {
        Text(
            stringResource(if (me == null || s?.loginMethod != io.github.oluengo.wavestr.data.LoginMethod.AMBER) R.string.chat_needs_amber else R.string.live_chat_empty),
            color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        return
    }
    // Al escribir, la caja se desplaza a la vista cuando aparece el teclado.
    val bring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    var focused by remember { androidx.compose.runtime.mutableStateOf(false) }
    val imeOpen = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    androidx.compose.runtime.LaunchedEffect(focused, imeOpen) {
        if (focused && imeOpen) { kotlinx.coroutines.delay(120); bring.bringIntoView() }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp)
            .then(Modifier.bringIntoViewRequester(bring))
            .onFocusChanged { focused = it.hasFocus },
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.material3.OutlinedTextField(
            value = text, onValueChange = { text = it.take(500) },
            placeholder = { Text(stringResource(R.string.chat_hint)) }, singleLine = false, maxLines = 3,
            textStyle = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.FilledIconButton(
            enabled = text.isNotBlank() && !sending,
            onClick = {
                val content = text.trim()
                val unsigned = io.github.oluengo.wavestr.nostr.EventSigning.unsigned(
                    me!!, io.github.oluengo.wavestr.nostr.Kinds.LIVE_CHAT, listOf(listOf("a", address!!, "", "root")), content,
                )
                sending = true
                scope.launch {
                    // Primero en silencio (si Amber lo tiene permitido); si no, se abre Amber.
                    val silent = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        io.github.oluengo.wavestr.nostr.Amber.signSilently(ctx, s?.signerPackage, unsigned, me)
                    }
                    if (silent != null) publish(silent)
                    else {
                        pending = unsigned
                        runCatching { amber.launch(io.github.oluengo.wavestr.nostr.Amber.signIntent(unsigned, me, s?.signerPackage)) }
                            .onFailure { sending = false; pending = null }
                    }
                }
            },
        ) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Outlined.Send, stringResource(R.string.chat_send))
        }
    }
}
