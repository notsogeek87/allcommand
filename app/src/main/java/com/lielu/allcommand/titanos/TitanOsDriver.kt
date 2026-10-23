package com.lielu.allcommand.titanos

import com.lielu.allcommand.device.Device
import com.lielu.allcommand.device.DeviceDriver
import com.lielu.allcommand.device.DeviceException
import com.lielu.allcommand.device.DeviceType
import com.lielu.allcommand.device.PairingSession
import com.lielu.allcommand.device.RemoteConnection
import com.lielu.allcommand.device.RemoteKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.UUID

/** Clés du dictionnaire [Device.credentials] d'une Titan OS. */
internal object TitanOsCredentials {
    const val USERNAME = "username"
    const val PASSWORD = "password"
    const val PINNED_KEY = "pinnedKey"
}

/** Pilote des Philips Titan OS : MQTT v5 sur TLS, appairage par code à 4 chiffres. */
class TitanOsDriver : DeviceDriver {
    override val type: DeviceType = DeviceType.TITAN_OS

    /** Une Titan OS accepte la connexion MQTT anonyme (c'est ainsi que l'appairage commence) ; un autre serveur non. */
    override suspend fun probe(host: String): Boolean {
        val client = TitanOsClient(host, PinnedTrust(pinnedKey = null), username = null, password = null)
        return try {
            withTimeoutOrNull(PROBE_TIMEOUT_MILLIS) { client.connect() } != null
        } catch (_: Exception) {
            false
        } finally {
            client.close()
        }
    }

    override suspend fun startPairing(host: String): PairingSession {
        val trust = PinnedTrust(pinnedKey = null)
        val client = TitanOsClient(host, trust, username = null, password = null)
        try {
            client.connect()
            val data = client.request("pairing/start", JSONObject().put("type", "mobile"))
            val challenge = data.optString("challenge")
            if (challenge.isEmpty()) throw DeviceException("Réponse inattendue de la télé.")
            return TitanOsPairingSession(client, trust, challenge)
        } catch (e: Exception) {
            client.close()
            throw e
        }
    }

    override fun connect(device: Device): RemoteConnection = TitanOsConnection(device)

    private companion object {
        const val PROBE_TIMEOUT_MILLIS = 4_000L
    }
}

private class TitanOsPairingSession(
    private val client: TitanOsClient,
    private val trust: PinnedTrust,
    private val challenge: String,
) : PairingSession {
    override suspend fun complete(code: String): Map<String, String> {
        val pin = code.trim()
        if (!PIN_FORMAT.matches(pin)) throw DeviceException("Saisissez le code à 4 chiffres affiché sur la télé.")
        val username = UUID.randomUUID().toString()
        try {
            val data =
                client.request(
                    "pairing/finish",
                    JSONObject()
                        .put("username", username)
                        .put("challenge_result", TitanOsPairing.signChallenge(pin, challenge)),
                )
            val grantedUser = data.optString("username")
            val grantedPassword = data.optString("password")
            val pinnedKey = trust.observedKey
            if (grantedUser.isEmpty() || grantedPassword.isEmpty() || pinnedKey == null) {
                throw DeviceException("Réponse inattendue de la télé.")
            }
            return mapOf(
                TitanOsCredentials.USERNAME to grantedUser,
                TitanOsCredentials.PASSWORD to grantedPassword,
                TitanOsCredentials.PINNED_KEY to pinnedKey,
            )
        } finally {
            client.close()
        }
    }

    override fun cancel() = client.close()

    private companion object {
        val PIN_FORMAT = Regex("\\d{4}")
    }
}

/**
 * Connexion de télécommande : s'ouvre au premier envoi et se rouvre une fois si elle est tombée (veille de la
 * télé, changement de réseau).
 */
private class TitanOsConnection(
    private val device: Device,
) : RemoteConnection {
    private val lock = Mutex()
    private var client: TitanOsClient? = null

    override suspend fun open() {
        lock.withLock { ensureClient() }
    }

    override suspend fun reconnect() {
        lock.withLock {
            resetClient()
            ensureClient()
        }
    }

    override suspend fun send(key: RemoteKey) {
        val payload = JSONObject().put("key_code", key.titanKeyCode())
        lock.withLock {
            try {
                ensureClient().publish("rc", payload)
            } catch (e: Exception) {
                // Connexion probablement tombée : on repart d'une connexion neuve, une seule fois.
                resetClient()
                ensureClient().publish("rc", payload)
            }
        }
    }

    override fun close() = resetClient()

    private suspend fun ensureClient(): TitanOsClient {
        client?.takeIf { it.isConnected }?.let { return it }
        resetClient()
        val user = device.credentials[TitanOsCredentials.USERNAME]
        val password = device.credentials[TitanOsCredentials.PASSWORD]
        val pinnedKey = device.credentials[TitanOsCredentials.PINNED_KEY]
        if (user == null || password == null || pinnedKey == null) {
            throw DeviceException("Cet appareil n'est pas appairé : supprimez-le puis ajoutez-le de nouveau.")
        }
        val created = TitanOsClient(device.host, PinnedTrust(pinnedKey), user, password)
        created.connect()
        client = created
        return created
    }

    private fun resetClient() {
        client?.close()
        client = null
    }
}
