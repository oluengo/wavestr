package io.github.oluengo.wavestr.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.oluengo.wavestr.R
import io.github.oluengo.wavestr.container
import io.github.oluengo.wavestr.ui.theme.WavestrTheme
import kotlinx.coroutines.launch

/** Al añadir (o reconfigurar) el widget de favoritos: elegir qué carpeta muestra. */
class FavoritesWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        fun choose(folderId: String) {
            lifecycleScope.launch {
                runCatching {
                    val glanceId = GlanceAppWidgetManager(this@FavoritesWidgetConfigActivity).getGlanceIdBy(widgetId)
                    updateAppWidgetState(this@FavoritesWidgetConfigActivity, glanceId) { it[FavoritesWidget.FOLDER] = folderId }
                    FavoritesWidget().update(this@FavoritesWidgetConfigActivity, glanceId)
                }
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                finish()
            }
        }

        setContent {
            val s by container.settings.flow.collectAsStateWithLifecycle(initialValue = null)
            val lib by container.library.library.collectAsStateWithLifecycle()
            WavestrTheme(accent = s?.accent ?: io.github.oluengo.wavestr.data.Accent.VIOLET) {
                Surface(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize().padding(20.dp)) {
                        item {
                            Text(stringResource(R.string.widget_config_title), style = MaterialTheme.typography.headlineMedium)
                            Text(stringResource(R.string.widget_config_sub), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
                        }
                        item {
                            Row(stringResource(R.string.widget_config_all), lib.favorites.size, 0) { choose("") }
                        }
                        items(lib.tree()) { (f, depth) ->
                            Row(f.name, lib.itemsIn(f.id).size, depth) { choose(f.id) }
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun Row(label: String, count: Int, depth: Int, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = (depth * 18).dp, top = 12.dp, bottom = 12.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Text(count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}
