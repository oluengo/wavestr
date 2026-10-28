package io.github.oluengo.wavestr.ui.live

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.oluengo.wavestr.AppContainer
import io.github.oluengo.wavestr.R
import io.github.oluengo.wavestr.player.NowPlaying
import io.github.oluengo.wavestr.ui.common.Artwork
import io.github.oluengo.wavestr.ui.common.EmptyNote
import io.github.oluengo.wavestr.ui.common.LiveBadge
import io.github.oluengo.wavestr.ui.common.LoginButtons
import io.github.oluengo.wavestr.ui.common.ScreenHeader
import io.github.oluengo.wavestr.ui.common.SectionLabel
import io.github.oluengo.wavestr.ui.theme.Rs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(c: AppContainer, onPlay: (NowPlaying) -> Unit, onSignIn: () -> Unit, onOpenMe: () -> Unit) {
    val vm: LiveViewModel = viewModel(factory = viewModelFactory { initializer { LiveViewModel(c) } })
    val st by vm.state.collectAsStateWithLifecycle()
    val untitled = stringResource(R.string.untitled_stream)
    val play: (LiveViewModel.Item) -> Unit = { item ->
        // La lista entera, para anterior/siguiente stream.
        c.player.setStreamContext(st.items.filter { it.stream.streamingUrl != null }.map { it.toNowPlaying(untitled) })
        onPlay(item.toNowPlaying(untitled))
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            stringResource(R.string.live_title),
            stringResource(if (st.scope == LiveViewModel.Scope.FOLLOWING) R.string.live_sub_following else R.string.live_sub_global),
        ) {
            IconButton(onClick = { vm.load(refresh = true) }) {
                Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.refresh))
            }
            io.github.oluengo.wavestr.ui.common.SurpriseButton(c)
            io.github.oluengo.wavestr.ui.common.MeButton(c, onOpenMe)
        }
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = st.scope == LiveViewModel.Scope.FOLLOWING,
                onClick = { vm.setScope(LiveViewModel.Scope.FOLLOWING) },
                label = { Text(stringResource(R.string.chip_following)) },
            )
            FilterChip(
                selected = st.scope == LiveViewModel.Scope.GLOBAL,
                onClick = { vm.setScope(LiveViewModel.Scope.GLOBAL) },
                label = { Text(stringResource(R.string.chip_global)) },
            )
        }

        PullToRefreshBox(
            isRefreshing = st.loading,
            onRefresh = { vm.load(refresh = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                when {
                    st.scope == LiveViewModel.Scope.FOLLOWING && st.pubkey == null -> item {
                        Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.login_prompt_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.login_prompt_body), color = Rs.colors.muted)
                            LoginButtons(c, Modifier.padding(top = 8.dp))
                        }
                    }
                    st.failed -> item { EmptyNote(stringResource(R.string.live_error)) }
                    st.loaded && st.items.isEmpty() -> item {
                        EmptyNote(
                            stringResource(
                                if (st.scope == LiveViewModel.Scope.FOLLOWING) R.string.live_empty_following else R.string.live_empty_global
                            )
                        )
                    }
                    else -> {
                        st.items.firstOrNull()?.let { f ->
                            item { SectionLabel(stringResource(R.string.featured)) }
                            item { FeaturedCard(f, untitled) { play(f) } }
                        }
                        if (st.items.size > 1) {
                            item { SectionLabel(stringResource(R.string.also_live_count, st.items.size - 1)) }
                            items(st.items.drop(1), key = { it.stream.address }) { item ->
                                StreamRow(item, untitled) { play(item) }
                                HorizontalDivider(color = Rs.colors.line)
                            }
                        }
                    }
                }
                item { Box(Modifier.size(24.dp)) }
            }
        }
    }
}

fun LiveViewModel.Item.toNowPlaying(untitled: String) = NowPlaying(
    id = stream.address,
    url = stream.streamingUrl!!,
    title = stream.title.ifBlank { untitled },
    subtitle = host?.bestName ?: io.github.oluengo.wavestr.nostr.Nip19.shortNpub(stream.hostPubkey),
    artworkUrl = stream.image ?: host?.picture,
    hostPubkey = stream.hostPubkey,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeaturedCard(item: LiveViewModel.Item, untitled: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, Rs.colors.line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            Artwork(item.stream.image ?: item.host?.picture, item.stream.address, Modifier.fillMaxSize(), corner = 0.dp)
            LiveBadge(Modifier.align(Alignment.TopStart).padding(10.dp))
            item.stream.participants?.let {
                Text(
                    stringResource(R.string.listeners, it),
                    color = Color.White, fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                        .background(Color(0x8C000000), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.stream.title.ifBlank { untitled }, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.hostLine(), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StreamRow(item: LiveViewModel.Item, untitled: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(item.stream.image ?: item.host?.picture, item.stream.address, Modifier.size(52.dp))
        Column(Modifier.weight(1f)) {
            Text(item.stream.title.ifBlank { untitled }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.hostLine(), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        item.stream.hashtags.firstOrNull()?.let { tag ->
            Text(
                tag, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.background(Rs.colors.accentSoft, RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun LiveViewModel.Item.hostLine(): String {
    val name = host?.bestName ?: io.github.oluengo.wavestr.nostr.Nip19.shortNpub(stream.hostPubkey)
    return stream.participants?.let { "$name · " + stringResource(R.string.listeners, it) } ?: name
}
