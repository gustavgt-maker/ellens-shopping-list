package de.ellens.shoppinglist.ui.common

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.ellens.shoppinglist.core.ShakeDetector
import de.ellens.shoppinglist.data.settings.ShakeSensitivity

/**
 * Lauscht auf Schütteln, solange der Bildschirm sichtbar und die App im Vordergrund ist.
 * Der Sensor wird beim Verlassen der App sofort abgemeldet (kein Hintergrundverbrauch).
 * Keine Berechtigung nötig.
 */
@Composable
fun ShakeEffect(
    sensitivity: ShakeSensitivity,
    enabled: Boolean,
    onShake: () -> Unit,
) {
    val context = LocalContext.current
    val currentOnShake by rememberUpdatedState(onShake)
    val active = enabled && sensitivity != ShakeSensitivity.OFF

    LifecycleResumeEffect(active, sensitivity) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (!active || manager == null || sensor == null) {
            onPauseOrDispose { }
        } else {
            val detector = ShakeDetector(thresholdG = sensitivity.thresholdG)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val v = event.values
                    if (detector.onSample(v[0], v[1], v[2], SystemClock.elapsedRealtime())) currentOnShake()
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            onPauseOrDispose { manager.unregisterListener(listener) }
        }
    }
}
