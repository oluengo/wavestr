package io.github.oluengo.wavestr

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oluengo.wavestr.ui.WavestrRoot
import io.github.oluengo.wavestr.ui.theme.WavestrTheme

/** AppCompatActivity para que el selector de idioma por app funcione también en Android < 13. */
class MainActivity : AppCompatActivity() {

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        container.player.connect()
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            val s by container.settings.flow.collectAsStateWithLifecycle<io.github.oluengo.wavestr.data.AppSettings?>(initialValue = null)
            WavestrTheme(accent = s?.accent ?: io.github.oluengo.wavestr.data.Accent.VIOLET) {
                WavestrRoot(container)
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && savedInstanceState == null) {
            // Necesario para ver los controles del reproductor en la notificación.
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Enlace compartido (https://oluengo.github.io/wavestr/s/#… o wavestr://s#…): vista previa para importar. */
    private fun handleShare(intent: android.content.Intent?) {
        val data = intent?.takeIf { it.action == android.content.Intent.ACTION_VIEW }?.dataString ?: return
        val code = io.github.oluengo.wavestr.data.Shared.codeFrom(data) ?: return
        val shared = io.github.oluengo.wavestr.data.Shared.decode(code)
        if (shared == null) {
            android.widget.Toast.makeText(this, R.string.share_bad_link, android.widget.Toast.LENGTH_LONG).show()
        } else {
            container.incomingShare.value = shared
        }
    }
}
