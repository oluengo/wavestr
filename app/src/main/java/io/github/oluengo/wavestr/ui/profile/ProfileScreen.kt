package io.github.oluengo.wavestr.ui.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.oluengo.wavestr.AppContainer
import io.github.oluengo.wavestr.R
import io.github.oluengo.wavestr.nostr.LiveStream
import io.github.oluengo.wavestr.nostr.Nip19
import io.github.oluengo.wavestr.nostr.Profile
import io.github.oluengo.wavestr.player.NowPlaying
import io.github.oluengo.wavestr.ui.common.Artwork
import io.github.oluengo.wavestr.ui.common.EmptyNote
import io.github.oluengo.wavestr.ui.common.LiveBadge
import io.github.oluengo.wavestr.ui.common.SectionLabel
import io.github.oluengo.wavestr.ui.common.keyGradient
import io.github.oluengo.wavestr.ui.theme.Rs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class ProfileViewModel(private val c: AppContainer, val pubkey: String) : ViewModel() {
    data class UiState(
        val profile: Profile? = null,
        val live: List<LiveStream> = emptyList(),
        val past: List<LiveStream> = emptyList(),
        val loading: Boolean = true,
    )

    private val _state = MutableStateFlow(UiState(profile = c.nostr.cachedProfile(pubkey)))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val viewer = c.settings.current().pubkey
            val profile = runCatching { c.nostr.profiles(listOf(pubkey), viewer)[pubkey] }.getOrNull()
            _state.update { it.copy(profile = profile ?: it.profile) }
            val streams = runCatching { c.nostr.streamsOf(pubkey, viewer) }.getOrDefault(emptyList())
            val now = System.currentTimeMillis() / 1000
            _state.update {
                it.copy(
                    live = streams.filter { s -> s.isLiveNow(now) },
                    past = streams.filter { s -> s.hasRecording },
                    loading = false,
                )
            }
        }
    }
}

@Composable
fun ProfileScreen(c: AppContainer, pubkey: String, onBack: () -> Unit, onPlay: (NowPlaying) -> Unit) {
    val vm: ProfileViewModel = viewModel(key = pubkey, factory = viewModelFactory { initializer { ProfileViewModel(c, pubkey) } })
    val st by vm.state.collectAsStateWithLifecycle()
    val p = st.profile
    val name = p?.bestName ?: Nip19.shortNpub(pubkey)
    val untitled = stringResource(R.string.untitled_stream)
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    val copied = stringResource(R.string.copied)

    fun nowPlaying(s: LiveStream, recording: Boolean) = NowPlaying(
        id = s.address + if (recording) ":rec" else "",
        url = (if (recording) s.recordingUrl else s.streamingUrl)!!,
        title = s.title.ifBlank { untitled },
        subtitle = name,
        artworkUrl = s.image ?: p?.picture,
        hostPubkey = pubkey,
        isLive = !recording,
    )

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillMaxWidth().height(140.dp).background(keyGradient(pubkey))) {
                Artwork(p?.banner, pubkey, Modifier.fillMaxSize(), corner = 0.dp)
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.statusBarsPadding().padding(8.dp),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0x59000000), contentColor = Color.White),
                ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Artwork(
                    p?.picture, pubkey,
                    Modifier.offset(y = (-40).dp).size(84.dp).border(4.dp, MaterialTheme.colorScheme.background, CircleShape),
                    corner = 42.dp,
                )
                Column(Modifier.offset(y = (-40).dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(name, style = MaterialTheme.typography.headlineMedium)
                    p?.nip05?.let { Text("✓ $it", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
                    p?.about?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    p?.lud16?.let { ln ->
                        Row(
                            Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                                .padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("⚡ $ln", fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString(ln))
                                Toast.makeText(ctx, copied, Toast.LENGTH_SHORT).show()
                            }) { Text(stringResource(R.string.copy)) }
                        }
                    }
                }
            }
        }
        if (st.loading) item {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        if (st.live.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.profile_live_now), Modifier.padding(horizontal = 20.dp)) }
            items(st.live, key = { it.address }) { s ->
                StreamLine(s, untitled, live = true) { onPlay(nowPlaying(s, recording = false)) }
            }
        }
        if (!st.loading) {
            item { SectionLabel(stringResource(R.string.profile_past), Modifier.padding(horizontal = 20.dp)) }
            if (st.past.isEmpty()) item { EmptyNote(stringResource(R.string.profile_no_past), Modifier.padding(horizontal = 20.dp)) }
            items(st.past, key = { it.address + ":rec" }) { s ->
                StreamLine(s, untitled, live = false) { onPlay(nowPlaying(s, recording = true)) }
            }
        }
        item { Box(Modifier.size(32.dp)) }
    }
}

@Composable
private fun StreamLine(s: LiveStream, untitled: String, live: Boolean, onClick: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Artwork(s.image, s.address, Modifier.size(52.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title.ifBlank { untitled }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val date = (s.startsAt ?: s.updatedAt).let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it * 1000)) }
                Text(
                    if (live) date else "$date · " + stringResource(R.string.recording),
                    color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall,
                )
            }
            if (live) LiveBadge()
        }
        HorizontalDivider(color = Rs.colors.line)
    }
}
