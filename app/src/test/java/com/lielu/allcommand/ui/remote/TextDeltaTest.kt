package com.lielu.allcommand.ui.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextDeltaTest {
    @Test
    fun `une lettre ajoutee n'envoie que cette lettre`() {
        assertEquals("o", textDelta("hell", "hello"))
    }

    @Test
    fun `tout effacer envoie un effacement par caractere`() {
        assertEquals("\b\b\b\b\b", textDelta("hello", ""))
    }

    @Test
    fun `une correction efface la fin differente puis tape la nouvelle`() {
        assertEquals("\blo", textDelta("helo", "hello"))
    }

    @Test
    fun `aucun changement ne donne rien`() {
        assertNull(textDelta("abc", "abc"))
    }
}
