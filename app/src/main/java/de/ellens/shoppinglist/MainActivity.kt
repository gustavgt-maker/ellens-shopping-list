package de.ellens.shoppinglist

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import de.ellens.shoppinglist.data.settings.AppSettings
import de.ellens.shoppinglist.ui.AppNavHost
import de.ellens.shoppinglist.ui.theme.EllenTheme
import de.ellens.shoppinglist.ui.theme.isAppInDarkTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container get() = (application as EllenApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Nur beim echten Start auswerten, nicht bei einer Wiederherstellung (sonst doppelter Import).
        if (savedInstanceState == null) handleShareIntent(intent)
        enableEdgeToEdge()
        setContent {
            val settings: AppSettings? by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = null)
            val s = settings
            if (s == null) {
                // Einstellungen werden in Millisekunden geladen; bis dahin neutraler Hintergrund.
                Box(Modifier.fillMaxSize().background(Color.Transparent))
            } else {
                LaunchedEffect(s.keepScreenOn) { applyKeepScreenOn(s.keepScreenOn) }
                val dark = isAppInDarkTheme(s)
                LaunchedEffect(dark) {
                    // Statusleisten-Symbole an das gewählte App-Design anpassen.
                    val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                EllenTheme(settings = s) {
                    AppNavHost(container = container, settings = s)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    /** Text, der aus einer anderen App (z. B. WhatsApp) an diese App geteilt wurde. */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (!text.isNullOrBlank()) container.shareInbox.value = text.take(20_000)
    }

    override fun onStart() {
        super.onStart()
        // Tägliche Sicherung beim Öffnen; läuft im Hintergrund-Thread und blockiert die UI nicht.
        lifecycleScope.launch {
            try {
                container.backupManager.runDailyBackupIfDue()
            } catch (e: Exception) {
                Log.w("EllenApp", "Automatische Sicherung fehlgeschlagen", e)
            }
        }
    }

    /**
     * FLAG_KEEP_SCREEN_ON wirkt nur, solange dieses Fenster sichtbar ist. Andere Apps und
     * die Systemeinstellungen bleiben unberührt; es ist keine Berechtigung nötig.
     */
    private fun applyKeepScreenOn(enabled: Boolean) {
        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
