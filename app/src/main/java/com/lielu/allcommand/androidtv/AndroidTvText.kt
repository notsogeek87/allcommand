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
 * saisie passe par la modification de texte du protocole (`RemoteImeBatchEdit`). Ses deux compteurs doivent être ceux
 * du champ qui a le focus, sinon la télé ignore la saisie tout en affichant « Utilisez le clavier sur votre appareil
 * mobile ». Comme la bibliothèque de référence androidtvremote2 (0.3.x), ils viennent du `RemoteImeKeyInject`
 * (`app_info.counter`, `text_field_status.counter_field`) et du `RemoteImeShowRequest` (`counter_field`) ; ceux d'un
 * `RemoteImeBatchEdit` de la télé ne servent qu'à défaut (0 tant que rien n'est reçu). La télé INSÈRE le texte reçu
 * au curseur : on n'envoie que les caractères nouveaux, jamais le texte déjà saisi.
 */
internal class AndroidTvTextField {
    @Volatile private var imeCounter = 0L

    @Volatile private var fieldCounter = 0L

    @Volatile private var imeCounterFromFocus = false

    @Volatile private var fieldCounterFromFocus = false

    /** Vrai dès que la télé a annoncé un champ de texte sur la liaison en cours. */
    @Volatile var isAnnounced = false
        private set

    fun onMessage(message: ProtoMessage) {
        message.message(AndroidTvMessages.REMOTE_IME_KEY_INJECT)?.let { inject ->
            imeCounter = inject.message(KEY_INJECT_APP_INFO)?.long(APP_INFO_COUNTER) ?: 0L
            fieldCounter = inject.message(KEY_INJECT_FIELD_STATUS)?.long(FIELD_STATUS_COUNTER) ?: 0L
            imeCounterFromFocus = imeCounter != 0L
            fieldCounterFromFocus = fieldCounter != 0L
            isAnnounced = true
        }
        message.message(AndroidTvMessages.REMOTE_IME_SHOW_REQUEST)?.let { show ->
            show.message(SHOW_REQUEST_FIELD_STATUS)?.let { status ->
                fieldCounter = status.long(FIELD_STATUS_COUNTER) ?: 0L
                fieldCounterFromFocus = true
            }
            isAnnounced = true
        }
        message.message(AndroidTvMessages.REMOTE_IME_BATCH_EDIT)?.let { edit ->
            if (!imeCounterFromFocus) imeCounter = edit.long(1) ?: 0L
            if (!fieldCounterFromFocus) fieldCounter = edit.long(2) ?: 0L
            isAnnounced = true
        }
    }

    fun clear() {
        imeCounter = 0L
        fieldCounter = 0L
        imeCounterFromFocus = false
        fieldCounterFromFocus = false
        isAnnounced = false
    }

    /** Trames à envoyer pour [text] (`\b` efface un caractère, `\n` valide). */
    fun frames(text: String): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()
        val chunk = StringBuilder()

        fun flush() {
            if (chunk.isEmpty()) return
            frames += AndroidTvMessages.remoteImeInsert(imeCounter, fieldCounter, chunk.toString())
            chunk.clear()
        }
        for (c in text) {
            when (c) {
                '\b' -> {
                    flush()
                    frames += AndroidTvMessages.remoteKeyCode(KEYCODE_DEL)
                }
                '\n' -> {
                    flush()
                    frames += AndroidTvMessages.remoteKeyCode(KEYCODE_ENTER)
                }
                else -> chunk.append(c)
            }
        }
        flush()
        return frames
    }

    private companion object {
        const val KEYCODE_DEL = 67
        const val KEYCODE_ENTER = 66

        // RemoteImeKeyInject { app_info = 1; text_field_status = 2 }, RemoteAppInfo { counter = 1 },
        // RemoteTextFieldStatus { counter_field = 1 }, RemoteImeShowRequest { remote_text_field_status = 2 }.
        const val KEY_INJECT_APP_INFO = 1
        const val KEY_INJECT_FIELD_STATUS = 2
        const val APP_INFO_COUNTER = 1
        const val FIELD_STATUS_COUNTER = 1
        const val SHOW_REQUEST_FIELD_STATUS = 2
    }
}
