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

class AndroidTvTextFieldTest {
    private fun announce(
        field: AndroidTvTextField,
        counter: Long,
        value: String,
    ) {
        val frame =
            ProtoWriter()
                .message(AndroidTvMessages.REMOTE_IME_KEY_INJECT) {
                    message(2) {
                        varint(1, counter)
                        string(2, value)
                    }
                }.toFrame()
        field.onMessage(ProtoMessage.readFrame(java.io.ByteArrayInputStream(frame)))
    }

    private fun decode(frame: ByteArray) = ProtoMessage.readFrame(java.io.ByteArrayInputStream(frame))

    @Test
    fun `sans champ annonce la saisie part en modification de texte avec des compteurs a zero`() {
        val edit = decode(AndroidTvTextField().frames("ab").single()).message(21)
        assertEquals(listOf(0L, 0L), listOf(edit?.long(1), edit?.long(2)))
    }

    @Test
    fun `le champ annonce donne le texte deja present mais pas les compteurs`() {
        val field = AndroidTvTextField()
        announce(field, counter = 3, value = "x")
        val edit = decode(field.frames("é").single()).message(21)
        assertEquals(0L, edit?.long(2))
        assertEquals(
            "xé",
            edit
                ?.message(3)
                ?.message(2)
                ?.bytes(3)
                ?.toString(Charsets.UTF_8),
        )
    }

    @Test
    fun `la frappe suivante complete le texte deja saisi`() {
        val field = AndroidTvTextField()
        announce(field, counter = 1, value = "")
        field.frames("ab")
        val edit = decode(field.frames("c").single()).message(21)
        assertEquals(
            "abc",
            edit
                ?.message(3)
                ?.message(2)
                ?.bytes(3)
                ?.toString(Charsets.UTF_8),
        )
    }

    @Test
    fun `effacer et valider utilisent les touches DEL et ENTER`() {
        val field = AndroidTvTextField()
        announce(field, counter = 1, value = "ab")
        val frames = field.frames("\b\n")
        assertEquals(listOf(67L, 66L), frames.map { decode(it).message(10)?.long(1) })
    }

    @Test
    fun `les compteurs du RemoteImeBatchEdit de la tele sont reutilises tels quels`() {
        val field = AndroidTvTextField()
        val frame =
            ProtoWriter()
                .message(AndroidTvMessages.REMOTE_IME_BATCH_EDIT) {
                    varint(1, 7)
                    varint(2, 5)
                }.toFrame()
        field.onMessage(decode(frame))
        announce(field, counter = 99, value = "")
        val first = decode(field.frames("a").single()).message(21)
        val second = decode(field.frames("b").single()).message(21)
        assertEquals(listOf(7L, 7L), listOf(first?.long(1), second?.long(1)))
        assertEquals(5L, first?.long(2))
    }
}
