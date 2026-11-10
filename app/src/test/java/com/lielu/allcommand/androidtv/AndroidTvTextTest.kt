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

    private fun text(frame: ByteArray) =
        decode(frame)
            .message(21)
            ?.message(3)
            ?.message(2)
            ?.bytes(3)
            ?.toString(Charsets.UTF_8)

    @Test
    fun `seuls les caracteres nouveaux sont envoyes, jamais le texte deja saisi`() {
        val field = AndroidTvTextField()
        announce(field, counter = 1, value = "x")
        assertEquals("é", text(field.frames("é").single()))
        assertEquals("ab", text(field.frames("ab").single()))
        assertEquals("c", text(field.frames("c").single()))
    }

    @Test
    fun `effacer puis taper envoie DEL puis seulement le nouveau texte`() {
        val frames = AndroidTvTextField().frames("\b\bxy")
        assertEquals(listOf(67L, 67L), frames.take(2).map { decode(it).message(10)?.long(1) })
        assertEquals("xy", text(frames[2]))
    }

    @Test
    fun `effacer et valider utilisent les touches DEL et ENTER`() {
        val field = AndroidTvTextField()
        announce(field, counter = 1, value = "ab")
        val frames = field.frames("\b\n")
        assertEquals(listOf(67L, 66L), frames.map { decode(it).message(10)?.long(1) })
    }

    private fun batchEdit(
        ime: Long,
        field: Long,
    ) = decode(
        ProtoWriter()
            .message(AndroidTvMessages.REMOTE_IME_BATCH_EDIT) {
                varint(1, ime)
                varint(2, field)
            }.toFrame(),
    )

    private fun counters(frame: ByteArray) = decode(frame).message(21).let { listOf(it?.long(1), it?.long(2)) }

    @Test
    fun `les compteurs viennent du champ qui a le focus`() {
        val field = AndroidTvTextField()
        val frame =
            ProtoWriter()
                .message(AndroidTvMessages.REMOTE_IME_KEY_INJECT) {
                    message(1) { varint(1, 12) }
                    message(2) { varint(1, 34) }
                }.toFrame()
        field.onMessage(decode(frame))
        field.onMessage(batchEdit(ime = 7, field = 5))
        assertEquals(listOf(12L, 34L), counters(field.frames("a").single()))
    }

    @Test
    fun `le RemoteImeShowRequest donne le compteur du champ`() {
        val field = AndroidTvTextField()
        val frame =
            ProtoWriter()
                .message(AndroidTvMessages.REMOTE_IME_SHOW_REQUEST) {
                    message(2) { varint(1, 9) }
                }.toFrame()
        field.onMessage(decode(frame))
        field.onMessage(batchEdit(ime = 7, field = 5))
        assertEquals(listOf(7L, 9L), counters(field.frames("a").single()))
    }

    @Test
    fun `sans compteur du focus ceux du RemoteImeBatchEdit servent`() {
        val field = AndroidTvTextField()
        announce(field, counter = 0, value = "")
        field.onMessage(batchEdit(ime = 7, field = 5))
        assertEquals(listOf(7L, 5L), counters(field.frames("a").single()))
        assertEquals(listOf(7L, 5L), counters(field.frames("b").single()))
    }

    @Test
    fun `le curseur est place apres le texte insere`() {
        val obj = decode(AndroidTvTextField().frames("abc").single()).message(21)?.message(3)?.message(2)
        assertEquals(listOf(3L, 3L), listOf(obj?.long(1), obj?.long(2)))
    }
}
