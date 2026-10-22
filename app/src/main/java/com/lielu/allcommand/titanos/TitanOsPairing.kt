package com.lielu.allcommand.titanos

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object TitanOsPairing {
    private const val HMAC_ALGORITHM = "HmacSHA256"

    /**
     * Réponse au défi de l'appairage : HMAC-SHA256 du texte du défi, avec le code PIN comme clé, en hexadécimal
     * minuscule. Validé sur une Philips 55PUS8009 : le défi (chaîne base64) est signé tel quel, sans décodage.
     */
    fun signChallenge(
        pin: String,
        challenge: String,
    ): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(pin.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM))
        return mac.doFinal(challenge.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
