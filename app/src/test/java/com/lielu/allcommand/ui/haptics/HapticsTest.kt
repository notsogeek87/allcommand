package com.lielu.allcommand.ui.haptics

import android.view.HapticFeedbackConstants
import com.lielu.allcommand.data.settings.HapticIntensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HapticsTest {
    private val modern = 33
    private val legacy = 28

    @Test
    fun `une validation est plus marquee qu'un appui ordinaire a chaque intensite`() {
        HapticIntensity.entries.forEach { intensity ->
            assertNotEquals(
                intensity.name,
                Haptics.constantFor(HapticKind.KEY, intensity, modern),
                Haptics.constantFor(HapticKind.CONFIRM, intensity, modern),
            )
        }
    }

    @Test
    fun `l'intensite moyenne utilise le clic standard pour une touche`() {
        assertEquals(
            HapticFeedbackConstants.VIRTUAL_KEY,
            Haptics.constantFor(HapticKind.KEY, HapticIntensity.MEDIUM, modern),
        )
    }

    @Test
    fun `la faible intensite reste un simple tic`() {
        assertEquals(
            HapticFeedbackConstants.CLOCK_TICK,
            Haptics.constantFor(HapticKind.KEY, HapticIntensity.LOW, legacy),
        )
    }

    @Test
    fun `avant Android 11 on n'utilise pas la constante CONFIRM`() {
        HapticIntensity.entries.forEach { intensity ->
            HapticKind.entries.forEach { kind ->
                assertNotEquals(
                    HapticFeedbackConstants.CONFIRM,
                    Haptics.constantFor(kind, intensity, legacy),
                )
            }
        }
    }

    @Test
    fun `une valeur inconnue donne l'intensite moyenne`() {
        assertEquals(HapticIntensity.MEDIUM, HapticIntensity.fromStorageValue("n'importe quoi"))
        assertEquals(HapticIntensity.MEDIUM, HapticIntensity.fromStorageValue(null))
        assertEquals(HapticIntensity.HIGH, HapticIntensity.fromStorageValue("HIGH"))
    }
}
