package com.lielu.allcommand.androidtv

import com.lielu.allcommand.device.Device
import com.lielu.allcommand.device.DeviceDriver
import com.lielu.allcommand.device.DeviceException
import com.lielu.allcommand.device.DeviceType
import com.lielu.allcommand.device.PairingSession
import com.lielu.allcommand.device.RemoteConnection
import com.lielu.allcommand.device.RemoteKey
import com.lielu.allcommand.titanos.PinnedTrust
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/**
 * Pilote des Android TV (protocole « Android TV Remote v2 ») : appairage par code à 6 caractères sur le port 6467,
 * puis touches sur le port 6466, le tout en TLS avec un certificat client auto-signé.
 */
class AndroidTvDriver : DeviceDriver {
    override val type: DeviceType = DeviceType.ANDROID_TV

    override suspend fun startPairing(host: String): PairingSession =
        withContext(Dispatchers.IO) {
            val identity = AndroidTvIdentity.generate()
            val link = TvLink.open(host, AndroidTvMessages.PAIRING_PORT, identity)
            try {
                val server = link.serverCertificate.publicKey as? RSAPublicKey ?: throw DeviceException(UNEXPECTED)
                link.send(AndroidTvMessages.pairingRequest("allCommand"))
                AndroidTvMessages.expectPairing(link.receive(), AndroidTvMessages.PAIRING_REQUEST_ACK)
                link.send(AndroidTvMessages.pairingOption())
                AndroidTvMessages.expectPairing(link.receive(), AndroidTvMessages.PAIRING_OPTION)
                link.send(AndroidTvMessages.pairingConfiguration())
                AndroidTvMessages.expectPairing(link.receive(), AndroidTvMessages.PAIRING_CONFIGURATION_ACK)
                AndroidTvPairingSession(link, identity, server)
            } catch (e: Exception) {
                link.close()
                throw if (e is DeviceException) e else DeviceException(UNEXPECTED, e)
            }
        }

    override fun connect(device: Device): RemoteConnection = AndroidTvConnection(device)

    internal companion object {
        const val UNEXPECTED = "Réponse inattendue de la télé."
    }
}

private class AndroidTvPairingSession(
    private val link: TvLink,
    private val identity: AndroidTvIdentity,
    private val server: RSAPublicKey,
) : PairingSession {
    override suspend fun complete(code: String): Map<String, String> {
        val typed = code.trim()
        if (!Regex("[0-9A-Fa-f]{${AndroidTvMessages.CODE_LENGTH}}").matches(typed)) {
            throw DeviceException("Saisissez le code à 6 caractères (chiffres ou lettres A à F) affiché sur la télé.")
        }
        val client = identity.certificate.publicKey as RSAPublicKey
        val secret =
            AndroidTvMessages.pairingSecret(client, server, typed)
                ?: throw DeviceException("Code incorrect. Recommencez l'ajout pour obtenir un nouveau code.")
        return withContext(Dispatchers.IO) {
            try {
                link.send(AndroidTvMessages.pairingSecret(secret))
                AndroidTvMessages.expectPairing(link.receive(), AndroidTvMessages.PAIRING_SECRET_ACK)
                identity.toCredentials()
            } catch (e: IOException) {
                throw DeviceException("La télé a coupé la connexion. Recommencez l'ajout.", e)
            } finally {
                link.close()
            }
        }
    }

    override fun cancel() = link.close()
}

/**
 * Connexion de télécommande : s'ouvre au premier envoi, répond aux « ping » de la télé tant qu'elle reste ouverte,
 * et se rouvre une fois si elle est tombée (veille, changement de réseau).
 */
private class AndroidTvConnection(
    private val device: Device,
) : RemoteConnection {
    private val lock = Mutex()
    private var link: TvLink? = null

    override suspend fun send(key: RemoteKey) {
        val frame = AndroidTvMessages.remoteKey(key)
        lock.withLock {
            withContext(Dispatchers.IO) {
                try {
                    ensureLink().send(frame)
                } catch (e: IOException) {
                    reset()
                    try {
                        ensureLink().send(frame)
                    } catch (e2: IOException) {
                        reset()
                        throw DeviceException("La télé ne répond pas. Est-elle allumée, sur le même Wi-Fi ?", e2)
                    }
                }
            }
        }
    }

    override fun close() = reset()

    private fun ensureLink(): TvLink {
        link?.takeIf { it.isAlive }?.let { return it }
        reset()
        val identity =
            AndroidTvIdentity.fromCredentials(device.credentials)
                ?: throw DeviceException("Cet appareil n'est pas appairé : supprimez-le puis ajoutez-le de nouveau.")
        val opened = TvLink.open(device.host, AndroidTvMessages.REMOTE_PORT, identity)
        try {
            opened.handshake()
        } catch (e: Exception) {
            opened.close()
            throw e
        }
        link = opened
        return opened
    }

    private fun reset() {
        link?.close()
        link = null
    }
}

/** Socket TLS vers la télé, avec lecture/écriture de messages cadrés. */
private class TvLink(
    private val socket: SSLSocket,
    val serverCertificate: X509Certificate,
) {
    private val input: InputStream = socket.inputStream
    private val output: OutputStream = socket.outputStream

    @Volatile
    var isAlive: Boolean = true
        private set

    fun send(frame: ByteArray) {
        synchronized(output) {
            output.write(frame)
            output.flush()
        }
    }

    fun receive(): ProtoMessage = ProtoMessage.readFrame(input)

    /**
     * Poignée de main de télécommande : la télé annonce sa configuration, on répond avec la nôtre et on active
     * la session. Ensuite un fil lit en continu pour répondre aux « ping », faute de quoi la télé coupe.
     */
    fun handshake() {
        socket.soTimeout = HANDSHAKE_TIMEOUT_MILLIS
        var active = false
        var messages = 0
        while (!active && messages++ < MAX_HANDSHAKE_MESSAGES) {
            val message = receive()
            when {
                message.has(AndroidTvMessages.REMOTE_CONFIGURE) -> send(AndroidTvMessages.remoteConfigure())
                message.has(AndroidTvMessages.REMOTE_SET_ACTIVE) -> {
                    send(AndroidTvMessages.remoteSetActive())
                    active = true
                }
                message.has(
                    AndroidTvMessages.REMOTE_PING_REQUEST,
                ) -> send(AndroidTvMessages.remotePingResponse(message))
            }
        }
        if (!active) throw IOException("Poignée de main incomplète")
        socket.soTimeout = 0
        Thread({ answerPings() }, "androidtv-ping").apply { isDaemon = true }.start()
    }

    private fun answerPings() {
        try {
            while (true) {
                val message = receive()
                if (message.has(
                        AndroidTvMessages.REMOTE_PING_REQUEST,
                    )
                ) {
                    send(AndroidTvMessages.remotePingResponse(message))
                }
            }
        } catch (_: Exception) {
            close()
        }
    }

    fun close() {
        isAlive = false
        runCatching { socket.close() }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 5_000
        private const val HANDSHAKE_TIMEOUT_MILLIS = 10_000
        private const val MAX_HANDSHAKE_MESSAGES = 20

        /**
         * Ouvre la connexion TLS. Le certificat de la télé est auto-signé et on s'y connecte par IP : on l'accepte
         * (le secret d'appairage, lui, est lié aux clés des deux côtés).
         */
        fun open(
            host: String,
            port: Int,
            identity: AndroidTvIdentity,
        ): TvLink {
            val context = SSLContext.getInstance("TLS")
            context.init(identity.keyManagers(), PinnedTrust(pinnedKey = null).factory.trustManagers, SecureRandom())
            val socket = context.socketFactory.createSocket() as SSLSocket
            try {
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS)
                socket.soTimeout = HANDSHAKE_TIMEOUT_MILLIS
                socket.startHandshake()
                val server = socket.session.peerCertificates.firstOrNull() as? X509Certificate ?: throw IOException()
                return TvLink(socket, server)
            } catch (e: IOException) {
                runCatching { socket.close() }
                throw DeviceException("Impossible de joindre la télé. Est-elle allumée, sur le même Wi-Fi ?", e)
            }
        }
    }
}
