package com.lielu.allcommand.androidtv

import com.lielu.allcommand.device.DeviceException
import com.lielu.allcommand.device.RemoteKey
import java.math.BigInteger
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey

/** Messages du protocole « Android TV Remote v2 » : appairage (port 6467) puis télécommande (port 6466). */
internal object AndroidTvMessages {
    const val PAIRING_PORT = 6467
    const val REMOTE_PORT = 6466

    private const val PROTOCOL_VERSION = 2L
    private const val STATUS_OK = 200L
    private const val ENCODING_HEXADECIMAL = 3L
    const val CODE_LENGTH = 6
    private const val ROLE_INPUT = 1L
    private const val DIRECTION_SHORT = 3L
    private const val REMOTE_FEATURES = 622L

    // Champs de PairingMessage.
    const val PAIRING_REQUEST_ACK = 11
    const val PAIRING_OPTION = 20
    const val PAIRING_CONFIGURATION_ACK = 31
    const val PAIRING_SECRET_ACK = 41

    // Champs de RemoteMessage.
    const val REMOTE_CONFIGURE = 1
    const val REMOTE_SET_ACTIVE = 2
    const val REMOTE_PING_REQUEST = 8

    private fun pairing(build: ProtoWriter.() -> Unit): ByteArray =
        ProtoWriter()
            .varint(1, PROTOCOL_VERSION)
            .varint(2, STATUS_OK)
            .apply(build)
            .toFrame()

    fun pairingRequest(clientName: String): ByteArray =
        pairing {
            message(10) {
                string(1, "androidtvremote2")
                string(2, clientName)
            }
        }

    fun pairingOption(): ByteArray =
        pairing {
            message(20) {
                message(1) {
                    varint(1, ENCODING_HEXADECIMAL)
                    varint(2, CODE_LENGTH.toLong())
                }
                varint(3, ROLE_INPUT)
            }
        }

    fun pairingConfiguration(): ByteArray =
        pairing {
            message(30) {
                message(1) {
                    varint(1, ENCODING_HEXADECIMAL)
                    varint(2, CODE_LENGTH.toLong())
                }
                varint(2, ROLE_INPUT)
            }
        }

    fun pairingSecret(secret: ByteArray): ByteArray = pairing { message(40) { bytes(1, secret) } }

    /** Vérifie le statut d'une réponse d'appairage et la présence du champ attendu. */
    fun expectPairing(
        message: ProtoMessage,
        field: Int,
    ) {
        val status = message.long(2)
        if (status == STATUS_BAD_SECRET) throw DeviceException("Code refusé par la télé. Recommencez l'ajout.")
        if (status != STATUS_OK || !message.has(field)) throw DeviceException("Réponse inattendue de la télé.")
    }

    private const val STATUS_BAD_SECRET = 402L

    /**
     * Secret envoyé à la télé : SHA-256 des clés publiques (client puis serveur) et des 4 derniers chiffres du
     * code. Le premier octet du code doit égaler le premier octet du hash — cela détecte une faute de frappe avant
     * même d'interroger la télé. `null` si le code ne correspond pas.
     */
    fun pairingSecret(
        client: RSAPublicKey,
        server: RSAPublicKey,
        code: String,
    ): ByteArray? {
        val bytes = hexToBytes(code)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(unsigned(client.modulus))
        digest.update(unsigned(client.publicExponent))
        digest.update(unsigned(server.modulus))
        digest.update(unsigned(server.publicExponent))
        digest.update(bytes.copyOfRange(1, bytes.size))
        val hash = digest.digest()
        return hash.takeIf { it[0] == bytes[0] }
    }

    /** Entier sans octet de signe, comme `bytes.fromhex(format(n, 'X'))` côté Android TV. */
    private fun unsigned(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        return if (bytes.size > 1 && bytes[0].toInt() == 0) bytes.copyOfRange(1, bytes.size) else bytes
    }

    fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) {
            hex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }

    fun remoteConfigure(): ByteArray =
        ProtoWriter()
            .message(REMOTE_CONFIGURE) {
                varint(1, REMOTE_FEATURES)
                message(2) {
                    string(1, "allCommand")
                    string(2, "allCommand")
                    varint(3, 1)
                    string(4, "1")
                    string(5, "com.lielu.allcommand")
                    string(6, "1.0.0")
                }
            }.toFrame()

    fun remoteSetActive(): ByteArray = ProtoWriter().message(REMOTE_SET_ACTIVE) { varint(1, REMOTE_FEATURES) }.toFrame()

    fun remotePingResponse(request: ProtoMessage): ByteArray =
        ProtoWriter().message(9) { varint(1, request.message(REMOTE_PING_REQUEST)?.long(1) ?: 0L) }.toFrame()

    fun remoteKey(key: RemoteKey): ByteArray = remoteKeyCode(key.androidKeyCode())

    fun remoteKeyCode(code: Int): ByteArray =
        ProtoWriter()
            .message(10) {
                varint(1, code.toLong())
                varint(2, DIRECTION_SHORT)
            }.toFrame()
}

/** Codes `KeyEvent.KEYCODE_*` d'Android, attendus tels quels par une Android TV. */
internal fun RemoteKey.androidKeyCode(): Int =
    when (this) {
        RemoteKey.UP -> 19
        RemoteKey.DOWN -> 20
        RemoteKey.LEFT -> 21
        RemoteKey.RIGHT -> 22
        RemoteKey.OK -> 23
        RemoteKey.BACK -> 4
        RemoteKey.HOME -> 3
        RemoteKey.OPTIONS -> 82
        RemoteKey.SOURCES -> 178
        RemoteKey.VOLUME_UP -> 24
        RemoteKey.VOLUME_DOWN -> 25
        RemoteKey.MUTE -> 164
        RemoteKey.POWER -> 26
        RemoteKey.PLAY_PAUSE -> 85
        RemoteKey.PREVIOUS -> 88
        RemoteKey.NEXT -> 87
        RemoteKey.CHANNEL_UP -> 166
        RemoteKey.CHANNEL_DOWN -> 167
    }
