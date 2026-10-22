package com.lielu.allcommand.titanos

import com.lielu.allcommand.device.DeviceException
import org.json.JSONException
import org.json.JSONObject

internal object TitanOsMessages {
    private const val ERROR_INVALID_AUTHENTICATION = 7
    private const val ERROR_PAIRING_IN_PROGRESS = 8

    /**
     * Les réponses MQTT de la télé sont `{"data": …}` ou `{"error": {"code": N, "message": "…"}}`.
     * Renvoie le contenu de `data`, ou lève une [DeviceException] au message lisible pour l'utilisateur.
     */
    fun parseReply(text: String): JSONObject {
        val json =
            try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw DeviceException("Réponse illisible de la télé.", e)
            }
        val error = json.optJSONObject("error")
        if (error != null) throw DeviceException(describeError(error.optInt("code", -1), error.optString("message")))
        return json.optJSONObject("data") ?: throw DeviceException("Réponse inattendue de la télé.")
    }

    private fun describeError(
        code: Int,
        message: String,
    ): String =
        when (code) {
            ERROR_INVALID_AUTHENTICATION -> "Code incorrect, ou délai dépassé : recommencez l'appairage."
            ERROR_PAIRING_IN_PROGRESS ->
                "Un appairage est déjà en cours sur la télé : attendez une minute puis réessayez."
            else -> "La télé a refusé la demande (code $code) : $message"
        }
}
