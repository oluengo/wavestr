package io.github.oluengo.wavestr.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.oluengo.wavestr.R
import io.github.oluengo.wavestr.ui.theme.Rs
import kotlin.math.absoluteValue

/** Cabecera de pestaña: título grande + subtítulo, con acciones a la derecha. */
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
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Rs.colors.muted,
        modifier = modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).background(Rs.colors.live).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
        Text(stringResource(R.string.live_badge), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Degradado estable a partir de una clave (pubkey, id) para cuando no hay imagen. */
fun keyGradient(key: String): Brush {
    val palette = listOf(
        Color(0xFF0E7C6B) to Color(0xFF7CC6B5), Color(0xFF3B4FD1) to Color(0xFFA7B3FF),
        Color(0xFFC2410C) to Color(0xFFFDBA74), Color(0xFF9D174D) to Color(0xFFF9A8D4),
        Color(0xFF334155) to Color(0xFF94A3B8), Color(0xFF166534) to Color(0xFF86EFAC),
    )
    val (a, b) = palette[key.hashCode().absoluteValue % palette.size]
    return Brush.linearGradient(listOf(a, b))
}

/** Imagen remota con degradado de respaldo. */
@Composable
fun Artwork(url: String?, key: String, modifier: Modifier, corner: Dp = 12.dp) {
    Box(modifier.clip(RoundedCornerShape(corner)).background(keyGradient(key))) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun ComingSoon(@StringRes header: Int, @StringRes title: Int, @StringRes body: Int, icon: ImageVector) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(header), null)
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(Rs.colors.accentSoft),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)) }
            Spacer(Modifier.size(16.dp))
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(body), color = Rs.colors.muted, textAlign = TextAlign.Center)
        }
    }
}

/** Estado vacío o de aviso dentro de una lista. */
@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Rs.colors.muted, style = MaterialTheme.typography.bodyMedium, modifier = modifier.padding(vertical = 24.dp))
}
