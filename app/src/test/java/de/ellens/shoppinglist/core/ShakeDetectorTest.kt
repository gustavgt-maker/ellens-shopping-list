package de.ellens.shoppinglist.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeDetectorTest {

    private val g = ShakeDetector.EARTH_GRAVITY

    /** Liefert die Anzahl Auslösungen für eine Folge (Zeit ms, Beschleunigung in g). */
    private fun run(detector: ShakeDetector, samples: List<Pair<Long, Float>>): Int =
        samples.count { (t, a) -> detector.onSample(a * g, 0f, 0f, t) }

    @Test fun lyingStillNeverTriggers() {
        val d = ShakeDetector(thresholdG = 2.6f)
        assertEquals(0, run(d, (0L..5_000L step 20).map { it to 1.0f }))
    }

    @Test fun singleBumpDoesNotTrigger() {
        // z. B. Einkaufswagen stößt an: ein einzelner Ausschlag
        val d = ShakeDetector(thresholdG = 2.6f)
        assertEquals(0, run(d, listOf(0L to 1f, 100L to 4f, 200L to 1f, 3_000L to 4f)))
    }

    @Test fun threeStrongShakesTriggerOnce() {
        val d = ShakeDetector(thresholdG = 2.6f)
        val shakes = listOf(0L to 3f, 50L to 1f, 200L to 3.2f, 250L to 1f, 400L to 3f, 450L to 1f)
        assertEquals(1, run(d, shakes))
    }

    @Test fun cooldownPreventsDoubleTrigger() {
        val d = ShakeDetector(thresholdG = 2.6f)
        val burst = (0 until 12).map { i -> (i * 150L) to 3.5f }
        assertEquals(1, run(d, burst))
    }

    @Test fun peaksTooFarApartDoNotTrigger() {
        val d = ShakeDetector(thresholdG = 2.6f)
        assertEquals(0, run(d, listOf(0L to 3f, 900L to 3f, 1_800L to 3f, 2_700L to 3f)))
    }

    @Test fun sensitivityLevelsDiffer() {
        val moderate = listOf(0L to 2.3f, 200L to 2.3f, 400L to 2.3f)
        assertEquals(1, run(ShakeDetector(thresholdG = 2.0f), moderate))   // Leicht
        assertEquals(0, run(ShakeDetector(thresholdG = 2.6f), moderate))   // Mittel
        assertEquals(0, run(ShakeDetector(thresholdG = 3.3f), moderate))   // Stark
    }

    @Test fun vibrationNoiseIsIgnored() {
        // Sehr schnelle Ausschläge (< 90 ms Abstand) zählen nur einmal
        val d = ShakeDetector(thresholdG = 2.6f)
        assertFalse((0L..60L step 10).any { d.onSample(3f * g, 0f, 0f, it) })
    }
}
