package de.ellens.shoppinglist.core

import kotlin.math.sqrt

/**
 * Erkennt bewusstes Schütteln aus Beschleunigungswerten (m/s², inkl. Erdanziehung).
 *
 * Gegen Fehlauslösung (Gehen, ruckelnder Einkaufswagen, Tasche) muss die Beschleunigung
 * [requiredPeaks]-mal innerhalb von [windowMs] über der Schwelle liegen, mit mindestens
 * [minPeakGapMs] Abstand zwischen den Spitzen. Nach dem Auslösen ruht die Erkennung
 * für [cooldownMs]. Reine Logik ohne Android – vollständig testbar.
 */
class ShakeDetector(
    private val thresholdG: Float,
    private val requiredPeaks: Int = 3,
    private val windowMs: Long = 1_200,
    private val minPeakGapMs: Long = 90,
    private val cooldownMs: Long = 2_000,
) {
    private val peaks = ArrayDeque<Long>()
    private var lastTrigger = Long.MIN_VALUE / 2

    /** @return true, wenn mit diesem Messwert ein Schütteln erkannt wurde. */
    fun onSample(x: Float, y: Float, z: Float, timeMs: Long): Boolean {
        if (timeMs - lastTrigger < cooldownMs) return false
        val g = sqrt(x * x + y * y + z * z) / EARTH_GRAVITY
        if (g < thresholdG) return false
        if (peaks.isNotEmpty() && timeMs - peaks.last() < minPeakGapMs) return false
        peaks.addLast(timeMs)
        while (peaks.isNotEmpty() && timeMs - peaks.first() > windowMs) peaks.removeFirst()
        if (peaks.size >= requiredPeaks) {
            peaks.clear()
            lastTrigger = timeMs
            return true
        }
        return false
    }

    fun reset() {
        peaks.clear()
    }

    companion object {
        const val EARTH_GRAVITY = 9.80665f
    }
}
