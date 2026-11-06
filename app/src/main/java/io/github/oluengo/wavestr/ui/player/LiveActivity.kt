@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.oluengo.wavestr.ui.player

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.oluengo.wavestr.AppContainer
import io.github.oluengo.wavestr.R
import io.github.oluengo.wavestr.nostr.Event
import io.github.oluengo.wavestr.nostr.EventSigning
import io.github.oluengo.wavestr.nostr.Kinds
import io.github.oluengo.wavestr.nostr.Nip19
import io.github.oluengo.wavestr.nostr.Profile
import io.github.oluengo.wavestr.nostr.Reaction
import io.github.oluengo.wavestr.nostr.RelayPool
import io.github.oluengo.wavestr.nostr.canSign
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

/** Emojis rápidos para reaccionar a un mensaje o al stream. */
val QUICK_REACTIONS = listOf("❤️", "🔥", "😂", "🤙", "👏", "🫂")

/** Chat, zaps y reacciones del directo que suena, refrescados cada 15 s mientras el reproductor está abierto. */
class LiveActivityViewModel(private val c: AppContainer) : ViewModel() {
    /**
     * Una línea del chat: mensaje ([id] != null) o zap al stream ([sats] != null). Los zaps y reacciones
     * a un mensaje se suman en él ([zapSats], [reactions]).
     */
    data class Line(
        val id: String?, val pubkey: String?, val time: Long, val author: String, val text: String, val sats: Long?,
        val zapSats: Long = 0, val reactions: List<Pair<String, Int>> = emptyList(), val mine: Set<String> = emptySet(),
    )
    data class UiState(
        val address: String? = null, val host: String? = null, val lines: List<Line> = emptyList(), val totalSats: Long = 0,
        val streamReactions: List<Pair<String, Int>> = emptyList(), val myStreamReactions: Set<String> = emptySet(), val loaded: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    private val names = HashMap<String, Profile>()
    /** Reacciones propias recién enviadas (hasta que los relays las devuelvan). */
    private val localReactions = ArrayList<Reaction>()

    fun watch(address: String?, host: String?) {
        if (address == _state.value.address) return
        job?.cancel()
        localReactions.clear()
        _state.value = UiState(address, host)
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
    fun addLocal(ev: Event, author: String) {
        _state.update { it.copy(lines = (it.lines + Line(ev.id, ev.pubkey, ev.createdAt, author, ev.content, null)).takeLast(60)) }
    }

    /** Publica un evento ya firmado en los relays del usuario y los de serie. */
    suspend fun publish(ev: Event, viewer: String): Boolean {
        val relays = runCatching { c.nostr.relaysFor(viewer) }.getOrDefault(RelayPool.DEFAULT)
        return c.relays.publish(relays, ev) > 0
    }

    /** Reacciona con [emoji] a un mensaje ([line]) o, si es null, al stream. */
    suspend fun react(emoji: String, line: Line?): Boolean {
        val st = _state.value
        val address = st.address ?: return false
        val me = c.settings.current().pubkey ?: return false
        val target = line?.pubkey ?: st.host ?: return false
        val unsigned = EventSigning.unsigned(me, Kinds.REACTION, Reaction.tags(address, target, line?.id), emoji)
        val signed = c.signer.sign(unsigned) ?: return false
        if (!publish(signed, me)) return false
        localReactions += Reaction(signed.id, me, emoji, line?.id, signed.createdAt)
        refreshNow()
        return true
    }

    fun refreshNow() {
        val a = _state.value.address ?: return
        viewModelScope.launch { refreshOnce(a, System.currentTimeMillis() / 1000 - 12 * 3600) }
    }

    private suspend fun refreshOnce(address: String, since: Long) {
        try {
            val viewer = c.settings.current().pubkey
            val act = c.nostr.activityFull(address, since, viewer)
            val who = (act.chat.map { it.pubkey } + act.zaps.mapNotNull { it.senderPubkey }).distinct().filter { it !in names }
            if (who.isNotEmpty()) names.putAll(c.nostr.profiles(who, viewer))
            fun name(pk: String?) = pk?.let { names[it]?.bestName ?: Nip19.shortNpub(it) } ?: "?"
            val reactions = (act.reactions + localReactions).distinctBy { Triple(it.pubkey, it.eventId, it.emoji) }
            fun counts(list: List<Reaction>) = list.groupingBy { it.emoji }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value }
            val byMsg = reactions.filter { it.eventId != null }.groupBy { it.eventId }
            val zapsByMsg = act.zaps.filter { it.eventId != null }.groupBy { it.eventId }
            val msgIds = act.chat.map { it.id }.toSet()
            val lines = (
                act.chat.map { m ->
                    val r = byMsg[m.id].orEmpty()
                    Line(
                        m.id, m.pubkey, m.createdAt, name(m.pubkey), m.content, null,
                        zapSats = zapsByMsg[m.id].orEmpty().totalSats(), reactions = counts(r),
                        mine = r.filter { it.pubkey == viewer }.map { it.emoji }.toSet(),
                    )
                } + act.zaps.filter { it.eventId == null || it.eventId !in msgIds }
                    .map { z -> Line(null, z.senderPubkey, z.createdAt, name(z.senderPubkey), z.comment, z.amountSats) }
                ).sortedBy { it.time }.takeLast(60)
            val streamR = reactions.filter { it.eventId == null }
            _state.update {
                if (it.address != address) it
                else it.copy(
                    lines = lines, totalSats = act.zaps.totalSats(), loaded = true,
                    streamReactions = counts(streamR), myStreamReactions = streamR.filter { r -> r.pubkey == viewer }.map { r -> r.emoji }.toSet(),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.update { it.copy(loaded = true) }
        }
    }
}

/** Cabecera del chat: total de sats, reacciones al stream y botón para reaccionar. */
@Composable
fun ChatHeader(vm: LiveActivityViewModel, c: AppContainer) {
    val st by vm.state.collectAsStateWithLifecycle()
    val fmt = remember { NumberFormat.getIntegerInstance() }
    val s by c.settings.flow.collectAsStateWithLifecycle(initialValue = null)
    val canReact = s?.loginMethod?.canSign == true
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        HorizontalDivider(color = Rs.colors.line)
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.live_chat_title) + if (st.totalSats > 0) " · ⚡ " + fmt.format(st.totalSats) + " sats" else "",
                color = Rs.colors.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f),
            )
            st.streamReactions.take(4).forEach { (e, n) ->
                Text("$e $n", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 6.dp),
                    fontWeight = if (e in st.myStreamReactions) FontWeight.Bold else FontWeight.Normal)
            }
            if (canReact) ReactButton(vm, null, Modifier.size(40.dp))
        }
    }
}

/** Mensajes del chat. Tocar uno: reaccionar o hacerle un zap a su autor. */
@Composable
fun ChatLines(vm: LiveActivityViewModel, c: AppContainer, onZapMessage: (LiveActivityViewModel.Line) -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val fmt = remember { NumberFormat.getIntegerInstance() }
    val s by c.settings.flow.collectAsStateWithLifecycle(initialValue = null)
    val canReact = s?.loginMethod?.canSign == true
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (st.loaded && st.lines.isEmpty()) Text(stringResource(R.string.live_chat_empty), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
        st.lines.forEach { l -> ChatLine(vm, l, fmt, canReact, onZapMessage) }
    }
}

@Composable
private fun ChatLine(vm: LiveActivityViewModel, l: LiveActivityViewModel.Line, fmt: NumberFormat, canReact: Boolean, onZap: (LiveActivityViewModel.Line) -> Unit) {
    val zap = l.sats != null
    var menu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val failed = stringResource(R.string.react_failed)
    Box {
        Column(
            (if (zap) Modifier.fillMaxWidth().background(Rs.colors.zapSoft, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 5.dp) else Modifier.fillMaxWidth())
                .then(if (l.id != null) Modifier.combinedClickable(onClick = { menu = true }, onLongClick = { menu = true }) else Modifier)
                .padding(vertical = 2.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(l.author, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                if (zap) Text("⚡ " + fmt.format(l.sats), color = Rs.colors.zap, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                if (l.text.isNotBlank()) Text(l.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
            if (l.reactions.isNotEmpty() || l.zapSats > 0) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                if (l.zapSats > 0) Pill("⚡ " + fmt.format(l.zapSats), mine = false, zap = true)
                l.reactions.forEach { (e, n) -> Pill("$e $n", mine = e in l.mine) }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (canReact) Row(Modifier.padding(horizontal = 8.dp)) {
                QUICK_REACTIONS.forEach { e ->
                    TextButton(onClick = {
                        menu = false
                        scope.launch { if (!vm.react(e, l)) Toast.makeText(ctx, failed, Toast.LENGTH_SHORT).show() }
                    }, modifier = Modifier.size(44.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text(e, fontSize = 20.sp) }
                }
            }
            if (l.pubkey != null) androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.zap_message, l.author)) },
                leadingIcon = { Icon(Icons.Filled.Bolt, null, tint = Rs.colors.zap) },
                onClick = { menu = false; onZap(l) },
            )
            if (!canReact) Text(
                stringResource(R.string.react_needs_signer), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Pill(text: String, mine: Boolean, zap: Boolean = false) {
    val bg = when {
        zap -> Rs.colors.zapSoft
        mine -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Text(
        text, style = MaterialTheme.typography.labelSmall, color = if (zap) Rs.colors.zap else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.background(bg, RoundedCornerShape(10.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** Botón «reaccionar» (al stream si [line] es null). */
@Composable
fun ReactButton(vm: LiveActivityViewModel, line: LiveActivityViewModel.Line?, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val failed = stringResource(R.string.react_failed)
    Box {
        IconButton(onClick = { open = true }, modifier = modifier) {
            Icon(Icons.Outlined.AddReaction, stringResource(R.string.react), tint = Rs.colors.muted, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Row(Modifier.padding(horizontal = 8.dp)) {
                QUICK_REACTIONS.forEach { e ->
                    TextButton(onClick = {
                        open = false
                        scope.launch { if (!vm.react(e, line)) Toast.makeText(ctx, failed, Toast.LENGTH_SHORT).show() }
                    }, modifier = Modifier.size(44.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text(e, fontSize = 20.sp) }
                }
            }
        }
    }
}

/** Escribir en el chat del stream (kind 1311), firmado con Amber o con el firmante remoto. */
@Composable
fun ChatInput(vm: LiveActivityViewModel, c: AppContainer) {
    val st by vm.state.collectAsStateWithLifecycle()
    val address = st.address
    val s by c.settings.flow.collectAsStateWithLifecycle(initialValue = null)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val me = s?.pubkey
    val canWrite = me != null && s?.loginMethod?.canSign == true && address != null
    val failed = stringResource(R.string.chat_failed)
    val myName = io.github.oluengo.wavestr.ui.common.rememberMyProfile(c).second?.bestName ?: me?.let { Nip19.shortNpub(it) } ?: ""

    if (!canWrite) {
        if (s != null && address != null) Text(
            stringResource(R.string.chat_needs_amber),
            color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        )
        return
    }
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text, onValueChange = { text = it.take(500) },
            placeholder = { Text(stringResource(R.string.chat_hint)) }, singleLine = false, maxLines = 3,
            textStyle = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
        )
        FilledIconButton(
            enabled = text.isNotBlank() && !sending,
            onClick = {
                val content = text.trim()
                val unsigned = EventSigning.unsigned(me!!, Kinds.LIVE_CHAT, listOf(listOf("a", address!!, "", "root")), content)
                sending = true
                scope.launch {
                    val signed = c.signer.sign(unsigned)
                    val ok = signed != null && vm.publish(signed, me)
                    sending = false
                    if (ok) { vm.addLocal(signed!!, myName); text = "" }
                    else if (signed != null) Toast.makeText(ctx, failed, Toast.LENGTH_LONG).show()
                }
            },
        ) {
            Icon(Icons.AutoMirrored.Outlined.Send, stringResource(R.string.chat_send))
        }
    }
}
