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
 * Champ de texte actif d'une Android TV. Les touches-lettres d'un clavier à l'écran (Gboard TV) sont ignorées : la
 * saisie passe par la modification de texte du protocole (`RemoteImeBatchEdit`), qui exige les compteurs annoncés
 * par la télé quand un champ prend le focus. Sans champ annoncé, on retombe sur des appuis de touches.
 */
internal class AndroidTvTextField {
    @Volatile private var fieldCounter: Long? = null

    @Volatile private var value = ""

    @Volatile private var imeCounter = 0L

    fun onMessage(message: ProtoMessage) {
        // Comme la bibliothèque de référence (androidtvremote2) : les compteurs sont ceux du dernier
        // RemoteImeBatchEdit envoyé par la télé, réutilisés tels quels (jamais incrémentés par nous).
        message.message(AndroidTvMessages.REMOTE_IME_BATCH_EDIT)?.let {
            imeCounter = it.long(1) ?: 0L
            fieldCounter = it.long(2) ?: 0L
        }
        val status =
            (
                message.message(AndroidTvMessages.REMOTE_IME_KEY_INJECT)
                    ?: message.message(AndroidTvMessages.REMOTE_IME_SHOW_REQUEST)
            )?.message(2)
                ?: return
        if (message.message(AndroidTvMessages.REMOTE_IME_BATCH_EDIT) == null) fieldCounter = status.long(1) ?: 0L
        value = status.bytes(2)?.toString(Charsets.UTF_8).orEmpty()
    }

    fun clear() {
        fieldCounter = null
        imeCounter = 0L
        value = ""
    }

    /** Trames à envoyer pour [text] (`\b` efface, `\n` valide). */
    fun frames(text: String): List<ByteArray> {
        val counter = fieldCounter ?: return AndroidTvText.keyCodes(text).map(AndroidTvMessages::remoteKeyCode)
        val frames = mutableListOf<ByteArray>()
        var current = value
        var pending = false

        fun flush() {
            if (!pending) return
            frames += AndroidTvMessages.remoteImeInsert(imeCounter, counter, current)
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
