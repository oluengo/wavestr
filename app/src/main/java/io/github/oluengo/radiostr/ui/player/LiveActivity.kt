package io.github.oluengo.radiostr.ui.player

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.oluengo.radiostr.AppContainer
import io.github.oluengo.radiostr.R
import io.github.oluengo.radiostr.nostr.ChatMessage
import io.github.oluengo.radiostr.nostr.Nip19
import io.github.oluengo.radiostr.nostr.Profile
import io.github.oluengo.radiostr.nostr.ZapReceipt
import io.github.oluengo.radiostr.nostr.totalSats
import io.github.oluengo.radiostr.ui.theme.Rs
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
fun LiveChat(vm: LiveActivityViewModel) {
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
        Text(stringResource(R.string.live_chat_readonly), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
    }
}
