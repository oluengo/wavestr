package io.github.oluengo.radiostr.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oluengo.radiostr.AppContainer
import io.github.oluengo.radiostr.R
import io.github.oluengo.radiostr.data.Library
import io.github.oluengo.radiostr.nostr.NostrJson
import io.github.oluengo.radiostr.ui.theme.Rs
import kotlinx.serialization.json.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ajustes → Datos y copias: copia completa en JSON, importarla (se combina) y radios en M3U. */
@Composable
fun BackupSection(c: AppContainer) {
    val ctx = LocalContext.current
    val lib by c.library.library.collectAsStateWithLifecycle()
    fun toast(id: Int, vararg args: Any) = Toast.makeText(ctx, ctx.getString(id, *args), Toast.LENGTH_LONG).show()

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(lib.toBackup(System.currentTimeMillis()).toString().toByteArray()) }
        }.isSuccess
        toast(if (ok) R.string.backup_exported else R.string.backup_write_error)
    }
    val exportM3u = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching { ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(lib.toM3u().toByteArray()) } }.isSuccess
        toast(if (ok) R.string.backup_m3u_exported else R.string.backup_write_error)
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val other = runCatching {
            val text = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().take(5_000_000).toByteArray().toString(Charsets.UTF_8) }
            Library.fromBackup(NostrJson.parseToJsonElement(text) as? JsonObject)
        }.getOrNull()
        if (other == null) {
            toast(R.string.backup_invalid)
        } else {
            c.library.update { it.merge(other) }
            toast(R.string.backup_imported, other.favorites.size, other.folders.size, other.imported.size)
        }
    }
    val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())

    Group {
        Row1(Icons.Outlined.FileDownload, R.string.backup_export, stringResource(R.string.backup_export_sub)) { exportJson.launch("radiostr-$stamp.json") }
        HorizontalDivider(color = Rs.colors.line)
        Row1(Icons.Outlined.FileUpload, R.string.backup_import, stringResource(R.string.backup_import_sub)) {
            importJson.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
        HorizontalDivider(color = Rs.colors.line)
        Row1(Icons.AutoMirrored.Outlined.QueueMusic, R.string.backup_m3u, stringResource(R.string.backup_m3u_sub)) { exportM3u.launch("radiostr-radios.m3u") }
    }
    Text(
        stringResource(R.string.backup_note, lib.favorites.size, lib.folders.size, lib.imported.size),
        color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun Row1(icon: ImageVector, title: Int, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), fontWeight = FontWeight.SemiBold)
            Text(sub, color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
