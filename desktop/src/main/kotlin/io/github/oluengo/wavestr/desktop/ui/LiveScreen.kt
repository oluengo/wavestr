package io.github.oluengo.wavestr.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.oluengo.wavestr.desktop.DesktopContainer
import io.github.oluengo.wavestr.desktop.t
import io.github.oluengo.wavestr.nostr.LiveStream
import io.github.oluengo.wavestr.nostr.Nip19
import io.github.oluengo.wavestr.nostr.Profile
import kotlinx.coroutines.CancellationException

private data class LiveItem(val stream: LiveStream, val host: Profile?)

@Composable
fun LiveScreen(c: DesktopContainer) {
    val s by c.settings.flow.collectAsState()
    val ps by c.player.state.collectAsState()
    val nav = LocalNav.current
    var following by remember(s.pubkey) { mutableStateOf(s.pubkey != null) }
    var reload by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<LiveItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(following, s.pubkey, reload) {
        loading = true; failed = false
        try {
            val pk = s.pubkey
            val streams = if (following && pk != null) c.nostr.liveFromFollows(pk, refresh = reload > 0) else c.nostr.liveGlobal(pk)
            val hosts = c.nostr.profiles(streams.map { it.hostPubkey }.distinct(), pk)
            items = streams.map { LiveItem(it, hosts[it.hostPubkey]) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failed = true
        }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(t("live_title"), t(if (following) "live_sub_following" else "live_sub_global")) {
            IconButton(onClick = { reload++ }) { Icon(Icons.Outlined.Refresh, t("refresh")) }
            HeaderActions(c)
        }
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = following, onClick = { following = true }, label = { Text(t("chip_following")) })
            FilterChip(selected = !following, onClick = { following = false }, label = { Text(t("chip_global")) })
        }
        LazyVerticalGrid(
            GridCells.Adaptive(330.dp), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                following && s.pubkey == null -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text(t("login_prompt_title"), fontWeight = FontWeight.SemiBold)
                        Text(t("desk_login_npub_only"), color = Rs.colors.muted)
                        TextButton(onClick = nav.openAccount) { Text(t("login_npub")) }
                    }
                }
                loading && items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                failed -> item(span = { GridItemSpan(maxLineSpan) }) { EmptyNote(t("live_error")) }
                items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyNote(t(if (following) "live_empty_following" else "live_empty_global"))
                }
                else -> items(items, key = { it.stream.address }) { li ->
                    LiveCard(li, selected = ps.nowPlaying?.id == li.stream.address) {
                        c.player.play(liveNowPlaying(li.stream, li.host)); nav.openPlayer()
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveCard(item: LiveItem, selected: Boolean, onClick: () -> Unit) {
    val s = item.stream
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Rs.colors.line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(s.image ?: item.host?.picture, s.address, s.title.ifBlank { item.host?.bestName ?: "?" }, Modifier.size(52.dp), corner = 10.dp)
        Column(Modifier.weight(1f)) {
            Text(s.title.ifBlank { t("untitled_stream") }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val who = item.host?.bestName ?: Nip19.shortNpub(s.hostPubkey)
            val line = listOfNotNull(who, s.participants?.let { t("listeners", it) }).joinToString(" · ")
            Text(line, color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LiveBadge()
    }
}
