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
