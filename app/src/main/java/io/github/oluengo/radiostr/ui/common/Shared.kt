package io.github.oluengo.radiostr.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oluengo.radiostr.AppContainer
import io.github.oluengo.radiostr.R
import io.github.oluengo.radiostr.data.AppSettings
import io.github.oluengo.radiostr.nostr.Profile
import io.github.oluengo.radiostr.radio.Station
import io.github.oluengo.radiostr.ui.theme.Rs
import java.util.Locale

/** Tu perfil Nostr (kind 0), cacheado en el repositorio. null mientras carga o sin sesión. */
@Composable
fun rememberMyProfile(c: AppContainer): Pair<String?, Profile?> {
    val s by c.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val pk = s.pubkey
    var profile by remember(pk) { mutableStateOf(pk?.let { c.nostr.cachedProfile(it) }) }
    LaunchedEffect(pk) {
        if (pk != null && profile == null) profile = runCatching { c.nostr.profiles(listOf(pk), pk)[pk] }.getOrNull()
    }
    return pk to profile
}

/** Botón de la cabecera: tu avatar si has iniciado sesión; si no, un icono de persona. */
@Composable
fun MeButton(c: AppContainer, onClick: () -> Unit) {
    val (pk, profile) = rememberMyProfile(c)
    IconButton(onClick = onClick) {
        if (pk == null) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).border(1.dp, Rs.colors.line, CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Person, stringResource(R.string.me_title), tint = Rs.colors.muted, modifier = Modifier.size(20.dp)) }
        } else {
            Box(Modifier.size(34.dp)) {
                Artwork(profile?.picture, pk, Modifier.size(34.dp), corner = 17.dp)
                if (profile?.picture == null) {
                    Text(
                        initials(profile?.bestName ?: "?"), color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}

fun initials(name: String): String =
    name.split(' ', '_', '.').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase().ifEmpty { "?" }

/** Nombre del país en el idioma de la app a partir del código ISO. */
@Composable
fun countryName(code: String?): String? {
    if (code.isNullOrBlank()) return null
    val loc = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    return Locale("", code).getDisplayCountry(loc).takeIf { it.isNotBlank() && it != code } ?: code
}

/** Fila de emisora: logo, nombre, país · formato · estilo, y ♥ o aviso de que no responde. */
@Composable
fun StationRow(
    st: Station,
    isFav: Boolean,
    onClick: () -> Unit,
    onToggleFav: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(52.dp)) {
            Artwork(st.favicon, st.id, Modifier.size(52.dp))
            if (st.favicon == null) Text(initials(st.name), color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
        }
        Column(Modifier.weight(1f)) {
            Text(st.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = listOfNotNull(countryName(st.countryCode) ?: st.country, st.best.label, st.tags.firstOrNull()).joinToString(" · ")
            Text(line, color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!st.ok) {
            Text(
                stringResource(R.string.radio_down), color = Rs.colors.live, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.background(Rs.colors.live.copy(alpha = 0.12f), RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
        if (trailing != null) trailing() else {
            IconButton(onClick = onToggleFav) {
                Icon(
                    if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    stringResource(if (isFav) R.string.fav_remove else R.string.fav_add),
                    tint = if (isFav) Rs.colors.live else Rs.colors.muted,
                )
            }
        }
    }
}
