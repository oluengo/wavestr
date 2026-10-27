package io.github.oluengo.radiostr

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import io.github.oluengo.radiostr.ui.RadiostrRoot
import io.github.oluengo.radiostr.ui.theme.RadiostrTheme

/** AppCompatActivity para que el selector de idioma por app funcione también en Android < 13. */
class MainActivity : AppCompatActivity() {

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        container.player.connect()
        setContent {
            RadiostrTheme {
                RadiostrRoot(container)
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && savedInstanceState == null) {
            // Necesario para ver los controles del reproductor en la notificación.
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
