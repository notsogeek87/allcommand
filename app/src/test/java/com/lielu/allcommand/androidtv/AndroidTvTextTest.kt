package com.lielu.allcommand.androidtv

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidTvTextTest {
    @Test
    fun `les lettres et chiffres deviennent des KEYCODE`() {
        assertEquals(listOf(29, 30, 7, 16), AndroidTvText.keyCodes("ab09"))
    }

    @Test
    fun `les accents sont retires et les majuscules abaissees`() {
        assertEquals(AndroidTvText.keyCodes("ecole"), AndroidTvText.keyCodes("École"))
    }

    @Test
    fun `effacer et valider ont leur touche`() {
        assertEquals(listOf(67, 66), AndroidTvText.keyCodes("\b\n"))
    }

    @Test
    fun `les caracteres sans touche sont ignores`() {
        assertEquals(listOf(29), AndroidTvText.keyCodes("a€#"))
    }
}
