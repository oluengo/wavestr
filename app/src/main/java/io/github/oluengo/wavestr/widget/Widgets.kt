package io.github.oluengo.wavestr.widget

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.oluengo.wavestr.MainActivity
import io.github.oluengo.wavestr.R
import io.github.oluengo.wavestr.container
import io.github.oluengo.wavestr.ui.common.SurprisePicker
import io.github.oluengo.wavestr.ui.theme.accentSchemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------- Los cuatro widgets ----------

/** A · Barra (4×1): lo que suena y play/pausa. */
class BarWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { WidgetFrame(context) { BarContent(it) } }
}

/** B · Reproductor (4×2): carátula grande, canción, play/pausa, dado y temporizador. */
class PlayerWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { WidgetFrame(context) { PlayerContent(it) } }
}

/** C · Favoritos (4×2): lo que suena arriba y tus favoritos (o los de una carpeta) para tocar y escuchar. */
class FavoritesWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { WidgetFrame(context) { FavoritesContent(it) } }

    companion object {
        /** Carpeta elegida al añadir el widget: "" o sin valor = todos los favoritos. */
        val FOLDER = stringPreferencesKey("folder")
    }
}

/** D · Cuadrado (2×2): carátula, play/pausa y dado. */
class SquareWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { WidgetFrame(context) { SquareContent(it) } }
}

class BarWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = BarWidget() }
class PlayerWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = PlayerWidget() }
class FavoritesWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = FavoritesWidget() }
class SquareWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = SquareWidget() }

// ---------- Piezas comunes ----------

private val LiveRed = ColorProvider(Color(0xFFE5483B))

@Composable
private fun WidgetFrame(context: Context, content: @Composable (WidgetModel) -> Unit) {
    val m by context.widgetSync.model.collectAsState()
    val (light, dark) = accentSchemes(m.accent)
    GlanceTheme(colors = ColorProviders(light = light, dark = dark)) {
        Box(
            GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(24.dp).background(GlanceTheme.colors.surface),
        ) { content(m) }
    }
}

private val openApp get() = actionStartActivity<MainActivity>()

@Composable
private fun Art(m: WidgetModel, size: Dp, corner: Dp, fill: Boolean = false) {
    val mod = (if (fill) GlanceModifier.fillMaxSize() else GlanceModifier.size(size)).cornerRadius(corner).clickable(openApp)
    val art = m.art
    if (art != null) {
        Image(ImageProvider(art), m.title, mod, contentScale = ContentScale.Crop)
    } else {
        Box(mod.background(GlanceTheme.colors.primaryContainer), contentAlignment = Alignment.Center) {
            if (m.idle && m.title.isEmpty()) {
                Image(ImageProvider(R.drawable.ic_launcher_foreground), null, GlanceModifier.size(size * 0.8f),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primary))
            } else {
                Text(initials(m.title), style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = (size.value / 3.2f).sp))
            }
        }
    }
}

@Composable
private fun PlayButton(m: WidgetModel, size: Dp = 40.dp) {
    val ctx = LocalContext.current
    Box(
        GlanceModifier.size(size).cornerRadius(size / 2).background(GlanceTheme.colors.primary).clickable(actionRunCallback<ToggleAction>()),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            ImageProvider(if (m.playing) R.drawable.ic_w_pause else R.drawable.ic_w_play),
            ctx.getString(if (m.playing) R.string.pause else R.string.play),
            GlanceModifier.size(size * 0.5f), colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
        )
    }
}

@Composable
private fun DiceButton(size: Dp = 34.dp) {
    Box(
        GlanceModifier.size(size).cornerRadius(size / 2).background(GlanceTheme.colors.primaryContainer).clickable(actionRunCallback<SurpriseAction>()),
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(R.drawable.ic_w_dice), LocalContext.current.getString(R.string.surprise), GlanceModifier.size(size * 0.55f),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary))
    }
}

@Composable
private fun LiveTag() {
    Box(GlanceModifier.cornerRadius(4.dp).background(LiveRed).padding(horizontal = 4.dp, vertical = 1.dp)) {
        Text(LocalContext.current.getString(R.string.live_badge), style = TextStyle(color = ColorProvider(Color.White), fontSize = 9.sp, fontWeight = FontWeight.Bold))
    }
}

private fun mainStyle(size: TextUnit = 14.sp) = TextStyle(fontWeight = FontWeight.Bold, fontSize = size)

@Composable
private fun Title(text: String, size: TextUnit = 14.sp, lines: Int = 1) =
    Text(text, style = mainStyle(size).copy(color = GlanceTheme.colors.onSurface), maxLines = lines)

@Composable
private fun Muted(text: String, size: TextUnit = 11.5.sp) =
    Text(text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = size), maxLines = 1)

/** Segunda línea: «EN DIRECTO · Núria», «♪ canción» o el subtítulo. */
@Composable
private fun SecondLine(m: WidgetModel) {
    val ctx = LocalContext.current
    when {
        m.idle -> Muted(if (m.title.isEmpty()) ctx.getString(R.string.widget_idle_empty) else ctx.getString(R.string.widget_idle_last))
        m.isLive -> Row(verticalAlignment = Alignment.CenterVertically) { LiveTag(); Spacer(GlanceModifier.width(5.dp)); Muted(m.subtitle) }
        m.song != null -> Text("♪ " + m.song, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 11.5.sp), maxLines = 1)
        else -> Muted(m.subtitle)
    }
}

@Composable
private fun sleepLabel(m: WidgetModel): String? {
    val ctx = LocalContext.current
    return when (val at = m.sleepAt) {
        null -> null
        -1L -> "☾ " + ctx.getString(R.string.sleep_stream_end)
        else -> "☾ " + ctx.getString(R.string.widget_sleep_until, DateFormat.getTimeFormat(ctx).format(java.util.Date(at)))
    }
}

private fun initials(name: String): String =
    name.split(' ', '_', '.').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase().ifEmpty { "♪" }

// ---------- Contenido de cada widget ----------

@Composable
private fun BarContent(m: WidgetModel) {
    Row(GlanceModifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Art(m, 46.dp, 14.dp)
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight().clickable(openApp)) {
            Title(m.title.ifEmpty { "Wavestr" })
            SecondLine(m)
        }
        Spacer(GlanceModifier.width(8.dp))
        if (m.idle && m.title.isEmpty()) DiceButton(40.dp) else PlayButton(m)
    }
}

@Composable
private fun PlayerContent(m: WidgetModel) {
    val ctx = LocalContext.current
    Row(GlanceModifier.fillMaxSize().padding(12.dp)) {
        val side = (LocalSize.current.height - 24.dp).coerceIn(80.dp, 160.dp)
        Art(m, side, 16.dp)
        Spacer(GlanceModifier.width(12.dp))
        Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
            Muted(
                when { m.idle -> "Wavestr"; m.isNostr -> ctx.getString(R.string.widget_kind_stream); else -> ctx.getString(R.string.widget_kind_radio) }.uppercase(),
                10.sp,
            )
            Box(GlanceModifier.clickable(openApp)) { Title(m.title.ifEmpty { ctx.getString(R.string.widget_idle_empty) }, 15.sp, 2) }
            SecondLine(m)
            Spacer(GlanceModifier.defaultWeight())
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayButton(m)
                Spacer(GlanceModifier.width(8.dp))
                DiceButton()
                sleepLabel(m)?.let {
                    Spacer(GlanceModifier.width(8.dp))
                    Text(it, style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 10.5.sp), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun FavoritesContent(m: WidgetModel) {
    val ctx = LocalContext.current
    val prefs = currentState<Preferences>()
    val folder = prefs[FavoritesWidget.FOLDER]?.takeIf { it.isNotEmpty() }
    val size = LocalSize.current
    val tiles = (if (folder == null) m.favorites else m.favorites.filter { it.folderId == folder })
    val count = ((size.width - 24.dp) / 70.dp).toInt().coerceIn(2, 6)
    val tileSide = ((size.width - 24.dp - 8.dp * (count - 1)) / count).coerceAtMost((size.height - 84.dp).coerceAtLeast(40.dp))

    Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Art(m, 34.dp, 10.dp)
            Spacer(GlanceModifier.width(10.dp))
            Column(GlanceModifier.defaultWeight().clickable(openApp)) {
                if (m.idle) {
                    Title(ctx.getString(R.string.tab_favorites))
                    Muted(ctx.getString(R.string.widget_fav_hint))
                } else {
                    Title(m.title)
                    SecondLine(m)
                }
            }
            if (m.idle) DiceButton() else PlayButton(m, 36.dp)
        }
        Spacer(GlanceModifier.height(10.dp))
        if (tiles.isEmpty()) {
            Muted(ctx.getString(R.string.widget_fav_empty))
        } else Row(GlanceModifier.fillMaxWidth()) {
            tiles.take(count).forEachIndexed { i, t ->
                if (i > 0) Spacer(GlanceModifier.width(8.dp))
                Column(
                    GlanceModifier.width(tileSide).clickable(actionRunCallback<PlayFavoriteAction>(actionParametersOf(PlayFavoriteAction.KEY to t.key))),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        val art = t.art
                        if (art != null) Image(ImageProvider(art), t.title, GlanceModifier.size(tileSide).cornerRadius(14.dp), contentScale = ContentScale.Crop)
                        else Box(GlanceModifier.size(tileSide).cornerRadius(14.dp).background(GlanceTheme.colors.primaryContainer), contentAlignment = Alignment.Center) {
                            Text(initials(t.title), style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 13.sp))
                        }
                        // Streamer emitiendo: punto rojo.
                        if (t.live == true) Box(GlanceModifier.padding(5.dp)) { Box(GlanceModifier.size(10.dp).cornerRadius(5.dp).background(LiveRed)) {} }
                    }
                    Text(
                        t.title, maxLines = 1,
                        style = TextStyle(color = if (t.live == false) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface, fontSize = 10.5.sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SquareContent(m: WidgetModel) {
    Column(GlanceModifier.fillMaxSize().padding(10.dp)) {
        Box(GlanceModifier.fillMaxWidth().defaultWeight()) {
            Art(m, 100.dp, 16.dp, fill = true)
            if (m.isLive) Box(GlanceModifier.padding(6.dp)) { LiveTag() }
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PlayButton(m)
            Spacer(GlanceModifier.defaultWeight())
            DiceButton()
        }
    }
}

// ---------- Acciones (funcionan con la app cerrada) ----------

class ToggleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withContext(Dispatchers.Main) { context.container.player.togglePlay() }
    }
}

class SurpriseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val c = context.container
        c.library.ready()
        val s = c.settings.current()
        val pick = runCatching { c.surprise.pick(s.surpriseKind, s.surpriseFromFavorites) }.getOrNull()
        withContext(Dispatchers.Main) {
            if (pick == null) toast(context, if (s.surpriseFromFavorites) R.string.surprise_empty_favs else R.string.surprise_empty_all)
            else c.surprise.play(pick.second, context.getString(R.string.untitled_stream))
        }
    }
}

class PlayFavoriteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val key = parameters[KEY] ?: return
        val c = context.container
        c.library.ready()
        val item = c.library.library.value.favorites.firstOrNull { it.key == key } ?: return
        val st = item.station
        if (st != null) { withContext(Dispatchers.Main) { c.player.playStation(st) }; return }
        val host = item.hostPubkey ?: return
        val viewer = c.settings.current().pubkey
        val live = runCatching { c.nostr.liveOf(setOf(host), viewer) }.getOrDefault(emptyList()).firstOrNull()
        withContext(Dispatchers.Main) {
            if (live == null) toast(context, R.string.widget_not_streaming, item.title)
            else c.surprise.play(SurprisePicker.Pick.Live(live), context.getString(R.string.untitled_stream))
        }
    }

    companion object { val KEY = ActionParameters.Key<String>("key") }
}

private fun toast(context: Context, res: Int, vararg args: Any) =
    android.widget.Toast.makeText(context, context.getString(res, *args), android.widget.Toast.LENGTH_LONG).show()
