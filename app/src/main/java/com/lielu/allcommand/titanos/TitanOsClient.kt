package com.lielu.allcommand.titanos

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.lielu.allcommand.device.DeviceException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.UUID

/**
 * Client MQTT v5 du serveur d'une Philips Titan OS (port 8883, TLS) — le protocole de l'app officielle Titan OS.
 *
 * Schéma requête/réponse de la télé : on s'abonne à `<sujet>/<uuid>`, puis on publie sur `<sujet>` avec cette
 * adresse en « Response Topic » ; la réponse (`{"data": …}` ou `{"error": …}`) arrive sur l'adresse d'abonnement.
 *
 * @param username `null` pour la connexion anonyme de l'appairage.
 */
internal class TitanOsClient(
    private val host: String,
    private val trust: PinnedTrust,
    private val username: String?,
    private val password: String?,
) {
    private var client: Mqtt5AsyncClient? = null

    suspend fun connect() {
        val mqtt =
            MqttClient
                .builder()
                .identifier(CLIENT_ID_PREFIX + (username ?: UUID.randomUUID().toString()))
                .serverHost(host)
                .serverPort(PORT)
                .useMqttVersion5()
                .sslConfig()
                .trustManagerFactory(trust.factory)
                // On se connecte par adresse IP : le nom d'hôte ne correspond jamais au certificat. L'identité
                // du serveur est garantie par l'empreinte de clé de PinnedTrust, pas par le nom.
                .hostnameVerifier { _, _ -> true }
                .applySslConfig()
                .buildAsync()
        try {
            if (username != null && password != null) {
                mqtt
                    .connectWith()
                    .keepAlive(KEEP_ALIVE_SECONDS)
                    .simpleAuth()
                    .username(username)
                    .password(password.toByteArray(Charsets.UTF_8))
                    .applySimpleAuth()
                    .send()
                    .await()
            } else {
                mqtt
                    .connectWith()
                    .keepAlive(KEEP_ALIVE_SECONDS)
                    .send()
                    .await()
            }
        } catch (e: Exception) {
            if (trust.mismatch) {
                throw DeviceException(
                    "La clé de sécurité de la télé a changé : supprimez cet appareil puis appairez-le de nouveau.",
                    e,
                )
            }
            throw DeviceException("Connexion impossible à la télé (${e.message ?: e.javaClass.simpleName}).", e)
        }
        client = mqtt
    }

    /** Envoie une requête et attend sa réponse ; renvoie le contenu de `data`. */
    suspend fun request(
        topic: String,
        payload: JSONObject,
    ): JSONObject {
        val mqtt = client ?: throw DeviceException("Pas de connexion à la télé.")
        val id = UUID.randomUUID().toString()
        val responseTopic = "$topic/$id"
        val reply = CompletableDeferred<String>()
        try {
            mqtt
                .subscribeWith()
                .topicFilter(responseTopic)
                .qos(MqttQos.AT_MOST_ONCE)
                .callback { publish -> reply.complete(String(publish.payloadAsBytes, Charsets.UTF_8)) }
                .send()
                .await()
            mqtt
                .publishWith()
                .topic(topic)
                .qos(MqttQos.AT_MOST_ONCE)
                .payload(payload.toString().toByteArray(Charsets.UTF_8))
                .responseTopic(responseTopic)
                .correlationData(id.toByteArray(Charsets.UTF_8))
                .send()
                .await()
        } catch (e: Exception) {
            throw DeviceException("Échange impossible avec la télé (${e.message ?: e.javaClass.simpleName}).", e)
        }
        val text =
            withTimeoutOrNull(REPLY_TIMEOUT_MS) { reply.await() }
                ?: throw DeviceException("La télé n'a pas répondu.")
        return TitanOsMessages.parseReply(text)
    }

    /**
     * Publie sans attendre de réponse : une touche de télécommande n'en a pas besoin. L'adresse de réponse est
     * tout de même fournie, comme le fait l'app officielle (et comme lors de l'essai validé sur la télé).
     */
    suspend fun publish(
        topic: String,
        payload: JSONObject,
    ) {
        val mqtt = client ?: throw DeviceException("Pas de connexion à la télé.")
        val id = UUID.randomUUID().toString()
        mqtt
            .publishWith()
            .topic(topic)
            .qos(MqttQos.AT_MOST_ONCE)
            .payload(payload.toString().toByteArray(Charsets.UTF_8))
            .responseTopic("$topic/$id")
            .correlationData(id.toByteArray(Charsets.UTF_8))
            .send()
            .await()
    }

    fun close() {
        val mqtt = client ?: return
        client = null
        runCatching { mqtt.disconnect() }
    }

    private companion object {
        const val PORT = 8883
        const val CLIENT_ID_PREFIX = "eos-"
        const val KEEP_ALIVE_SECONDS = 30
        const val REPLY_TIMEOUT_MS = 10_000L
    }
}
