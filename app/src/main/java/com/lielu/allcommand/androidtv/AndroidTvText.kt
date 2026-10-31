package com.lielu.allcommand.androidtv

import java.text.Normalizer

/**
 * Traduit un texte en codes de touche Android (`KEYCODE_*`) : l'Android TV reçoit la saisie comme des appuis sur un
 * clavier. Les accents sont retirés ; les caractères sans touche (majuscules, ponctuation rare) sont ignorés.
 * `\b` donne `KEYCODE_DEL` et `\n` `KEYCODE_ENTER`.
 */
internal object AndroidTvText {
    fun keyCodes(text: String): List<Int> =
        Normalizer
            .normalize(text, Normalizer.Form.NFD)
            .filter { it.code < ASCII_LIMIT }
            .mapNotNull { keyCode(it.lowercaseChar()) }

    private fun keyCode(c: Char): Int? =
        when (c) {
            in 'a'..'z' -> KEYCODE_A + (c - 'a')
            in '0'..'9' -> KEYCODE_0 + (c - '0')
            ' ' -> 62
            '.' -> 56
            ',' -> 55
            '-' -> 69
            '\b' -> 67
            '\n' -> 66
            else -> null
        }

    private const val KEYCODE_0 = 7
    private const val KEYCODE_A = 29
    private const val ASCII_LIMIT = 128
}

/**
 * Champ de texte actif d'une Android TV. Les touches-lettres sont ignorées par le clavier à l'écran (Gboard TV) : la
 * saisie passe par la modification de texte du protocole (`RemoteImeBatchEdit`), avec les compteurs du dernier
 * `RemoteImeBatchEdit` reçu de la télé, comme la bibliothèque de référence androidtvremote2 (0 tant qu'aucun
 * n'est reçu).
 */
internal class AndroidTvTextField {
    @Volatile private var imeCounter = 0L

    @Volatile private var fieldCounter = 0L

    @Volatile private var value = ""

    /** Vrai dès que la télé a annoncé un champ de texte sur la liaison en cours. */
    @Volatile var isAnnounced = false
        private set

    fun onMessage(message: ProtoMessage) {
        message.message(AndroidTvMessages.REMOTE_IME_BATCH_EDIT)?.let { edit ->
            imeCounter = edit.long(1) ?: 0L
            fieldCounter = edit.long(2) ?: 0L
            edit
                .message(3)
                ?.message(2)
                ?.bytes(3)
                ?.let { value = it.toString(Charsets.UTF_8) }
            isAnnounced = true
        }
        // RemoteImeKeyInject / RemoteImeShowRequest : un champ prend le focus. Ses compteurs ne sont pas ceux à
        // renvoyer (seul le RemoteImeBatchEdit les donne) ; on n'en retient que le texte déjà présent.
        val status =
            (
                message.message(AndroidTvMessages.REMOTE_IME_KEY_INJECT)
                    ?: message.message(AndroidTvMessages.REMOTE_IME_SHOW_REQUEST)
            )?.message(2) ?: return
        value = status.bytes(2)?.toString(Charsets.UTF_8).orEmpty()
        isAnnounced = true
    }

    fun clear() {
        imeCounter = 0L
        fieldCounter = 0L
        value = ""
        isAnnounced = false
    }

    /** Trames à envoyer pour [text] (`\b` efface, `\n` valide). */
    fun frames(text: String): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()
        var current = value
        var pending = false

        fun flush() {
            if (!pending) return
            frames += AndroidTvMessages.remoteImeInsert(imeCounter, fieldCounter, current)
            pending = false
        }
        for (c in text) {
            when (c) {
                '\b' -> {
                    flush()
                    current = current.dropLast(1)
                    frames += AndroidTvMessages.remoteKeyCode(KEYCODE_DEL)
                }
                '\n' -> {
                    flush()
                    frames += AndroidTvMessages.remoteKeyCode(KEYCODE_ENTER)
                }
                else -> {
                    current += c
                    pending = true
                }
            }
        }
        flush()
        value = current
        return frames
    }

    private companion object {
        const val KEYCODE_DEL = 67
        const val KEYCODE_ENTER = 66
    }
}
