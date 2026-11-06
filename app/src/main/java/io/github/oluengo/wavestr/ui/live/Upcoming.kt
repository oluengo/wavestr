package io.github.oluengo.wavestr.ui.live

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oluengo.wavestr.AppContainer
import io.github.oluengo.wavestr.R
import io.github.oluengo.wavestr.nostr.Nip19
import io.github.oluengo.wavestr.nostr.Profile
import io.github.oluengo.wavestr.nostr.Scheduled
import io.github.oluengo.wavestr.reminder.Reminders
import io.github.oluengo.wavestr.ui.common.Artwork
import io.github.oluengo.wavestr.ui.theme.Rs
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Hora de inicio en tu zona («Hoy, 21:00», «sáb 8 nov, 21:00») y, si es otra, en la del organizador. */
@Composable
fun scheduleTime(s: Scheduled): String {
    val zone = remember { ZoneId.systemDefault() }
    val locale = LocalContext.current.resources.configuration.locales[0] ?: Locale.getDefault()
    val time = remember(locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale) }
    val day = remember(locale) { DateTimeFormatter.ofPattern("EEE d MMM", locale) }
    val at = Instant.ofEpochSecond(s.startsAt).atZone(zone)
    val today = LocalDate.now(zone)
    val d = when (at.toLocalDate()) {
        today -> stringResource(R.string.time_today)
        today.plusDays(1) -> stringResource(R.string.time_tomorrow)
        else -> day.format(at)
    }
    val base = d + ", " + time.format(at)
    val other = s.otherZone(zone) ?: return base
    val city = other.id.substringAfterLast('/').replace('_', ' ')
    return base + " · " + stringResource(R.string.time_their_zone, time.format(Instant.ofEpochSecond(s.startsAt).atZone(other)), city)
}

/** Un directo o evento programado, con botón de recordatorio. Tocarlo abre el perfil del organizador. */
@Composable
fun UpcomingRow(c: AppContainer, s: Scheduled, host: Profile?, onOpenProfile: (String) -> Unit) {
    val reminders by c.reminders.all.collectAsStateWithLifecycle()
    val current = reminders.firstOrNull { it.address == s.address }
    var menu by remember { mutableStateOf(false) }
    var pendingLead by remember { mutableStateOf<Int?>(null) }
    val ctx = LocalContext.current
    val hostName = host?.bestName ?: Nip19.shortNpub(s.hostPubkey)
    val saved = stringResource(R.string.reminder_saved)
    fun save(lead: Int) {
        c.reminders.set(s, hostName, lead)
        Toast.makeText(ctx, saved, Toast.LENGTH_SHORT).show()
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        pendingLead?.let(::save); pendingLead = null
    }

    Row(
        Modifier.fillMaxWidth().clickable { onOpenProfile(s.hostPubkey) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(s.image ?: host?.picture, s.address, Modifier.size(48.dp), corner = 10.dp)
        Column(Modifier.weight(1f)) {
            Text(s.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(hostName, color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(scheduleTime(s), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
        Box {
            IconButton(onClick = { menu = true }) {
                if (current != null) Icon(Icons.Filled.NotificationsActive, stringResource(R.string.reminder_edit), tint = MaterialTheme.colorScheme.primary)
                else Icon(Icons.Outlined.NotificationAdd, stringResource(R.string.reminder_add), tint = Rs.colors.muted)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                Text(
                    stringResource(R.string.reminder_title), style = MaterialTheme.typography.labelMedium, color = Rs.colors.muted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                Reminders.LEADS.forEach { lead ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (lead == 0) stringResource(R.string.reminder_at_start) else stringResource(R.string.reminder_before, leadLabel(lead)),
                                fontWeight = if (current?.leadMin == lead) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            menu = false
                            if (Build.VERSION.SDK_INT >= 33 &&
                                ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) { pendingLead = lead; askPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                            else save(lead)
                        },
                    )
                }
                if (current != null) {
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(stringResource(R.string.reminder_remove), color = Rs.colors.live) }, onClick = {
                        menu = false; c.reminders.remove(s.address)
                    })
                }
            }
        }
    }
}

@Composable
private fun leadLabel(min: Int): String = if (min >= 60) stringResource(R.string.lead_hours, min / 60) else stringResource(R.string.lead_minutes, min)
