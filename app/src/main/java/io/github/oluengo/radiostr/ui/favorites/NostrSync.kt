package io.github.oluengo.radiostr.ui.favorites

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oluengo.radiostr.AppContainer
import io.github.oluengo.radiostr.R
import io.github.oluengo.radiostr.data.AppSettings
import io.github.oluengo.radiostr.data.FavoritesSync
import io.github.oluengo.radiostr.data.LoginMethod
import io.github.oluengo.radiostr.ui.theme.Rs
import kotlinx.coroutines.launch

/** Abre Amber cuando la sincronización necesita su pantalla (cifrar, descifrar o firmar). */
@Composable
fun AmberRequestHost(c: AppContainer) {
    val call by c.favSync.pending.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        call?.complete(res.data)
    }
    LaunchedEffect(call) {
        val cl = call ?: return@LaunchedEffect
        runCatching { launcher.launch(cl.intent) }.onFailure { cl.complete(null) }
    }
}

/** Tras entrar con Amber, se pregunta una vez si guardar los favoritos en Nostr. */
@Composable
fun SyncAskDialog(c: AppContainer) {
    val s by c.settings.flow.collectAsStateWithLifecycle<AppSettings?>(initialValue = null)
    val st = s ?: return
    if (st.pubkey == null || st.loginMethod != LoginMethod.AMBER || st.favSync != null) return
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.fav_sync_ask_title)) },
        text = { Text(stringResource(R.string.fav_sync_ask_body)) },
        confirmButton = {
            TextButton(onClick = { scope.launch { c.settings.setFavSync(true); c.favSync.syncNow() } }) { Text(stringResource(R.string.fav_sync_yes)) }
        },
        dismissButton = {
            TextButton(onClick = { scope.launch { c.settings.setFavSync(false) } }) { Text(stringResource(R.string.fav_sync_no)) }
        },
    )
}

@Composable
fun syncStatusText(st: FavoritesSync.State): String = when (val x = st.status) {
    FavoritesSync.Status.Off -> ""
    FavoritesSync.Status.Working -> stringResource(R.string.fav_sync_working)
    FavoritesSync.Status.NeedsAmber -> stringResource(R.string.fav_sync_needs_amber)
    is FavoritesSync.Status.Failed -> stringResource(
        when (x.reason) {
            FavoritesSync.Reason.OFFLINE -> R.string.fav_sync_offline
            FavoritesSync.Reason.TOO_BIG -> R.string.fav_sync_too_big
            FavoritesSync.Reason.UNREADABLE -> R.string.fav_sync_unreadable
        }
    )
    FavoritesSync.Status.Idle, FavoritesSync.Status.Done ->
        if (st.lastSync > 0) stringResource(R.string.fav_sync_done, DateUtils.getRelativeTimeSpanString(st.lastSync, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString())
        else stringResource(R.string.fav_sync_never)
}

/** Ajustes → Favoritos en Nostr. */
@Composable
fun FavSyncSection(c: AppContainer) {
    val s by c.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val st by c.favSync.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    io.github.oluengo.radiostr.ui.settings.Group {
        if (s.loginMethod != LoginMethod.AMBER) {
            Text(stringResource(R.string.fav_sync_toggle), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.fav_sync_needs_login), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
            return@Group
        }
        val on = s.favSync == true
        Row(
            Modifier.fillMaxWidth().selectable(selected = on, role = Role.Switch) {
                scope.launch { c.settings.setFavSync(!on); if (!on) c.favSync.syncNow() }
            }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.fav_sync_toggle), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.fav_sync_toggle_sub), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = on, onCheckedChange = null)
        }
        if (on) {
            Text(syncStatusText(st), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = { c.favSync.syncNow() },
                enabled = st.status != FavoritesSync.Status.Working,
                modifier = Modifier.padding(top = 8.dp),
            ) { Text(stringResource(R.string.fav_sync_now)) }
        }
    }
}

/** Aviso en Favoritos cuando la sincronización está parada esperando a Amber o ha fallado. */
@Composable
fun SyncBanner(c: AppContainer, modifier: Modifier = Modifier) {
    val st by c.favSync.state.collectAsStateWithLifecycle()
    val show = st.status == FavoritesSync.Status.NeedsAmber || st.status is FavoritesSync.Status.Failed
    if (!show) return
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(syncStatusText(st), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        TextButton(onClick = { c.favSync.syncNow() }) { Text(stringResource(R.string.fav_sync_now)) }
    }
}
