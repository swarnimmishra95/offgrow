package app.offgrow

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.offgrow.garden.GardenRenderer
import app.offgrow.ui.AppViewModel
import app.offgrow.ui.OffgrowRoot
import app.offgrow.ui.OffgrowTheme
import java.lang.ref.WeakReference

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            OffgrowTheme {
                OffgrowRoot(vm = vm, openUsageSettings = ::openUsageSettings, openAppInfo = ::openAppInfo)
            }
        }
        // Lend our window to the garden renderer while the app is open.
        (findViewById<ViewGroup>(android.R.id.content))?.let { GardenRenderer.host = WeakReference(it) }
    }

    override fun onResume() {
        super.onResume()
        vm.onAccessMaybeChanged()
    }

    override fun onDestroy() {
        val mine = findViewById<ViewGroup>(android.R.id.content)
        if (GardenRenderer.host?.get() === mine) GardenRenderer.host = null
        super.onDestroy()
    }

    private fun openAppInfo() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } catch (_: Exception) {
        }
    }

    private fun openUsageSettings() {
        val attempts = listOf(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).setData(Uri.fromParts("package", packageName, null)),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in attempts) {
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
    }
}
