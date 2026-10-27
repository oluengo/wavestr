package io.github.oluengo.wavestr.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.oluengo.wavestr.desktop.DesktopContainer
import io.github.oluengo.wavestr.desktop.I18n
import io.github.oluengo.wavestr.desktop.t
import io.github.oluengo.wavestr.nostr.Profile
import io.github.oluengo.wavestr.radio.SiteIcons
import io.github.oluengo.wavestr.radio.Station
import java.util.Locale
import kotlin.math.absoluteValue

@Composable
fun ScreenHeader(title: String, subtitle: String?, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Rs.colors.muted)
        }
        actions()
    }
}

@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).background(Rs.colors.live).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
        Text(t("live_badge"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

fun keyGradient(key: String): Brush {
    val palette = listOf(
        Color(0xFF6D28D9) to Color(0xFFB79CF7), Color(0xFF3B4FD1) to Color(0xFFA7B3FF),
        Color(0xFFC2410C) to Color(0xFFFDBA74), Color(0xFF9D174D) to Color(0xFFF9A8D4),
        Color(0xFF334155) to Color(0xFF94A3B8), Color(0xFF0E7C6B) to Color(0xFF7CC6B5),
    )
    val (a, b) = palette[key.hashCode().absoluteValue % palette.size]
    return Brush.linearGradient(listOf(a, b))
}

fun initials(name: String): String =
    name.split(' ', '_', '.').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase().ifEmpty { "?" }

/** Imagen remota sobre un degradado; si no hay imagen, iniciales. */
@Composable
fun Artwork(url: String?, key: String, label: String?, modifier: Modifier, corner: Dp = 12.dp) {
    var failed by remember(url) { mutableStateOf(false) }
    Box(modifier.clip(RoundedCornerShape(corner)).background(keyGradient(key)), contentAlignment = Alignment.Center) {
        if (url != null && !failed) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), onError = { failed = true })
        } else if (label != null) {
            Text(initials(label), color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

/** Carátula de emisora: logo del directorio si es bueno; si no, se busca en su web (como en Android). */
@Composable
fun StationArtwork(c: DesktopContainer, st: Station, modifier: Modifier, corner: Dp = 12.dp) {
    val cachedVal = remember(st.id) { c.artwork.cached(st) }
    var url by remember(st.id) {
        mutableStateOf(
            when {
                !cachedVal.isNullOrEmpty() -> cachedVal
                SiteIcons.goodDirectoryIcon(st.favicon) -> st.favicon
                cachedVal == "" -> st.favicon
                else -> null
            }
        )
    }
    var failed by remember(st.id) { mutableStateOf(emptySet<String>()) }
    var searched by remember(st.id) { mutableStateOf(cachedVal != null) }
    LaunchedEffect(st.id, url) {
        if (url == null && !searched) {
            searched = true
            url = c.artwork.resolve(st, failed) ?: st.favicon?.takeIf { it !in failed }
        }
    }
    Box(modifier.clip(RoundedCornerShape(corner)).background(keyGradient(st.id)), contentAlignment = Alignment.Center) {
        val u = url
        if (u != null) {
            AsyncImage(
                model = u, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize(),
                onError = { failed = failed + u; url = null; searched = false },
            )
        } else {
            Text(initials(st.name), color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

/** País en el idioma de la app. */
fun countryName(code: String?): String? {
    if (code.isNullOrBlank()) return null
    return Locale("", code).getDisplayCountry(I18n.locale).takeIf { it.isNotBlank() && it != code } ?: code
}

@Composable
fun StationRow(c: DesktopContainer, st: Station, isFav: Boolean, selected: Boolean, onClick: () -> Unit, onToggleFav: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Rs.colors.line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StationArtwork(c, st, Modifier.size(48.dp), corner = 10.dp)
        Column(Modifier.weight(1f)) {
            Text(st.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = listOfNotNull(countryName(st.countryCode) ?: st.country, st.best.label, st.tags.firstOrNull()).joinToString(" · ")
            Text(line, color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!st.ok) {
            Text(
                t("radio_down"), color = Rs.colors.live, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.background(Rs.colors.live.copy(alpha = 0.12f), RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
        FavButton(isFav, onToggleFav)
    }
}

@Composable
fun FavButton(isFav: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            t(if (isFav) "fav_remove" else "fav_add"),
            tint = if (isFav) Rs.colors.live else Rs.colors.muted,
        )
    }
}

/** Tu avatar (o un icono si no hay sesión); lleva a Ajustes → Cuenta. */
@Composable
fun MeButton(pubkey: String?, profile: Profile?, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        if (pubkey == null) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).border(1.dp, Rs.colors.line, CircleShape).background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Person, t("me_title"), tint = Rs.colors.muted, modifier = Modifier.size(20.dp)) }
        } else {
            Artwork(profile?.picture, pubkey, profile?.bestName ?: "?", Modifier.size(34.dp), corner = 17.dp)
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(I18n.locale), color = Rs.colors.muted, style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(top = 22.dp, bottom = 8.dp),
    )
}

@Composable
fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, Rs.colors.line, RoundedCornerShape(14.dp)).padding(14.dp),
        content = content,
    )
}

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Rs.colors.muted, style = MaterialTheme.typography.bodyMedium, modifier = modifier.padding(vertical = 24.dp))
}
