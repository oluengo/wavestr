package io.github.oluengo.wavestr.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.oluengo.wavestr.desktop.DesktopContainer
import io.github.oluengo.wavestr.desktop.I18n
import io.github.oluengo.wavestr.desktop.t
import io.github.oluengo.wavestr.nostr.Event
import io.github.oluengo.wavestr.nostr.EventSigning
import io.github.oluengo.wavestr.nostr.Kinds
import io.github.oluengo.wavestr.nostr.Nip19
import io.github.oluengo.wavestr.nostr.Profile
import io.github.oluengo.wavestr.nostr.Reaction
import io.github.oluengo.wavestr.nostr.RelayPool
import io.github.oluengo.wavestr.nostr.totalSats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.NumberFormat

val QUICK_REACTIONS = listOf("❤️", "🔥", "😂", "🤙", "👏", "🫂")

/** Chat, zaps y reacciones del directo que suena (igual que en Android), cada 15 s. */
class ChatModel(private val c: DesktopContainer, private val scope: CoroutineScope) {
    data class Line(
        val id: String?, val pubkey: String?, val time: Long, val author: String, val text: String, val sats: Long?,
        val zapSats: Long = 0, val reactions: List<Pair<String, Int>> = emptyList(), val mine: Set<String> = emptySet(),
    )
    data class State(
        val address: String? = null, val host: String? = null, val lines: List<Line> = emptyList(), val totalSats: Long = 0,
        val streamReactions: List<Pair<String, Int>> = emptyList(), val mineStream: Set<String> = emptySet(), val loaded: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    private var job: Job? = null
    private val names = HashMap<String, Profile>()
    private val local = ArrayList<Reaction>()

    fun watch(address: String?, host: String?) {
        if (address == _state.value.address) return
        job?.cancel(); local.clear()
        _state.value = State(address, host)
        if (address == null) return
        job = scope.launch {
            while (isActive) { refresh(address); delay(15_000) }
        }
    }

    fun refreshNow() { _state.value.address?.let { a -> scope.launch { refresh(a) } } }

    suspend fun publish(ev: Event): Boolean {
        val relays = runCatching { c.nostr.relaysFor(ev.pubkey) }.getOrDefault(RelayPool.DEFAULT)
        return c.relays.publish(relays, ev) > 0
    }

    fun addLocal(ev: Event, author: String) {
        _state.update { it.copy(lines = (it.lines + Line(ev.id, ev.pubkey, ev.createdAt, author, ev.content, null)).takeLast(80)) }
    }

    suspend fun react(emoji: String, line: Line?): Boolean {
        val st = _state.value
        val address = st.address ?: return false
        val me = c.settings.current.pubkey ?: return false
        val target = line?.pubkey ?: st.host ?: return false
        val signed = c.signer.sign(EventSigning.unsigned(me, Kinds.REACTION, Reaction.tags(address, target, line?.id), emoji)) ?: return false
        if (!publish(signed)) return false
        local += Reaction(signed.id, me, emoji, line?.id, signed.createdAt)
        refresh(address)
        return true
    }

    private suspend fun refresh(address: String) {
        try {
            val viewer = c.settings.current.pubkey
            val act = c.nostr.activityFull(address, System.currentTimeMillis() / 1000 - 12 * 3600, viewer)
            val who = (act.chat.map { it.pubkey } + act.zaps.mapNotNull { it.senderPubkey }).distinct().filter { it !in names }
            if (who.isNotEmpty()) names.putAll(c.nostr.profiles(who, viewer))
            fun name(pk: String?) = pk?.let { names[it]?.bestName ?: Nip19.shortNpub(it) } ?: "?"
            val reactions = (act.reactions + local).distinctBy { Triple(it.pubkey, it.eventId, it.emoji) }
            fun counts(l: List<Reaction>) = l.groupingBy { it.emoji }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value }
            val byMsg = reactions.filter { it.eventId != null }.groupBy { it.eventId }
            val zapsByMsg = act.zaps.filter { it.eventId != null }.groupBy { it.eventId }
            val ids = act.chat.map { it.id }.toSet()
            val lines = (act.chat.map { m ->
                val r = byMsg[m.id].orEmpty()
                Line(m.id, m.pubkey, m.createdAt, name(m.pubkey), m.content, null, zapsByMsg[m.id].orEmpty().totalSats(), counts(r), r.filter { it.pubkey == viewer }.map { it.emoji }.toSet())
            } + act.zaps.filter { it.eventId == null || it.eventId !in ids }.map { z -> Line(null, z.senderPubkey, z.createdAt, name(z.senderPubkey), z.comment, z.amountSats) })
                .sortedBy { it.time }.takeLast(80)
            val streamR = reactions.filter { it.eventId == null }
            _state.update {
                if (it.address != address) it else it.copy(
                    lines = lines, totalSats = act.zaps.totalSats(), loaded = true,
                    streamReactions = counts(streamR), mineStream = streamR.filter { r -> r.pubkey == viewer }.map { r -> r.emoji }.toSet(),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.update { it.copy(loaded = true) }
        }
    }
}

@Composable
fun ChatHeader(m: ChatModel, canReact: Boolean) {
    val st by m.state.collectAsState()
    val fmt = remember { NumberFormat.getIntegerInstance(I18n.locale) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        HorizontalDivider(color = Rs.colors.line)
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("live_chat_title") + if (st.totalSats > 0) " · ⚡ " + fmt.format(st.totalSats) + " sats" else "",
                color = Rs.colors.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            st.streamReactions.take(4).forEach { (e, n) ->
                Text("$e $n", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 6.dp),
                    fontWeight = if (e in st.mineStream) FontWeight.Bold else FontWeight.Normal)
            }
            if (canReact) ReactButton(m, null)
        }
    }
}

@Composable
fun ChatLines(m: ChatModel, canReact: Boolean, onZap: (ChatModel.Line) -> Unit) {
    val st by m.state.collectAsState()
    val fmt = remember { NumberFormat.getIntegerInstance(I18n.locale) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (st.loaded && st.lines.isEmpty()) Text(t("live_chat_empty"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
        st.lines.forEach { l -> ChatLineRow(m, l, fmt, canReact, onZap) }
    }
}

@Composable
private fun ChatLineRow(m: ChatModel, l: ChatModel.Line, fmt: NumberFormat, canReact: Boolean, onZap: (ChatModel.Line) -> Unit) {
    val zap = l.sats != null
    var menu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Box {
        Column(
            (if (zap) Modifier.fillMaxWidth().background(Rs.colors.zapSoft, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp) else Modifier.fillMaxWidth())
                .then(if (l.id != null) Modifier.clickable { menu = true } else Modifier).padding(vertical = 2.dp),
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
        DropdownMenu(menu, { menu = false }) {
            if (canReact) Row(Modifier.padding(horizontal = 8.dp)) {
                QUICK_REACTIONS.forEach { e ->
                    TextButton(onClick = { menu = false; scope.launch { m.react(e, l) } }, modifier = Modifier.size(44.dp), contentPadding = PaddingValues(0.dp)) { Text(e, fontSize = 20.sp) }
                }
            }
            if (l.pubkey != null) DropdownMenuItem(
                text = { Text(t("zap_message", l.author)) },
                leadingIcon = { Icon(Icons.Filled.Bolt, null, tint = Rs.colors.zap) },
                onClick = { menu = false; onZap(l) },
            )
            if (!canReact) Text(t("react_needs_signer"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun Pill(text: String, mine: Boolean, zap: Boolean = false) {
    val bg = when { zap -> Rs.colors.zapSoft; mine -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f); else -> MaterialTheme.colorScheme.surfaceContainerHigh }
    Text(text, style = MaterialTheme.typography.labelSmall, color = if (zap) Rs.colors.zap else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.background(bg, RoundedCornerShape(10.dp)).padding(horizontal = 7.dp, vertical = 2.dp))
}

@Composable
private fun ReactButton(m: ChatModel, line: ChatModel.Line?) {
    var open by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.AddReaction, t("react"), tint = Rs.colors.muted, modifier = Modifier.size(20.dp)) }
        DropdownMenu(open, { open = false }) {
            Row(Modifier.padding(horizontal = 8.dp)) {
                QUICK_REACTIONS.forEach { e ->
                    TextButton(onClick = { open = false; scope.launch { m.react(e, line) } }, modifier = Modifier.size(44.dp), contentPadding = PaddingValues(0.dp)) { Text(e, fontSize = 20.sp) }
                }
            }
        }
    }
}

/** Escribir en el chat (kind 1311), firmado con el bunker. Intro envía; Mayús+Intro, salto de línea. */
@Composable
fun ChatInput(c: DesktopContainer, m: ChatModel) {
    val st by m.state.collectAsState()
    val s by c.settings.flow.collectAsState()
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val address = st.address ?: return
    val me = s.pubkey
    if (me == null || !s.bunker) {
        Text(t("chat_needs_amber"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp))
        return
    }
    fun send() {
        val content = text.trim()
        if (content.isEmpty() || sending) return
        sending = true; failed = false
        scope.launch {
            val signed = c.signer.sign(EventSigning.unsigned(me, Kinds.LIVE_CHAT, listOf(listOf("a", address, "", "root")), content))
            val ok = signed != null && m.publish(signed)
            if (ok) { m.addLocal(signed!!, c.nostr.profiles(listOf(me), me)[me]?.bestName ?: Nip19.shortNpub(me)); text = "" } else failed = true
            sending = false
        }
    }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        if (failed) Text(t("chat_failed"), color = Rs.colors.live, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                text, { text = it.take(500) }, placeholder = { Text(t("chat_hint")) }, maxLines = 3, textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && !e.isShiftPressed) { send(); true } else false
                },
            )
            FilledIconButton(onClick = ::send, enabled = text.isNotBlank() && !sending) { Icon(Icons.AutoMirrored.Outlined.Send, t("chat_send")) }
        }
    }
}
