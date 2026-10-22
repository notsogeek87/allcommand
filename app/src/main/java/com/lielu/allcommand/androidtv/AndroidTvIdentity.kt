package com.lielu.allcommand.androidtv

import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory

/**
 * Identité de l'app face à une Android TV : paire de clés RSA et certificat auto-signé, présentés à la télé lors
 * de l'appairage puis à chaque connexion. La télé retient ce certificat : le garder, c'est rester appairé.
 */
internal class AndroidTvIdentity(
    val privateKey: PrivateKey,
    val certificate: X509Certificate,
) {
    fun keyManagers(): Array<KeyManager> {
        val store = KeyStore.getInstance("PKCS12")
        store.load(null, null)
        store.setKeyEntry("client", privateKey, KEY_PASSWORD, arrayOf(certificate))
        val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        factory.init(store, KEY_PASSWORD)
        return factory.keyManagers
    }

    fun toCredentials(): Map<String, String> =
        mapOf(
            AndroidTvCredentials.PRIVATE_KEY to Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP),
            AndroidTvCredentials.CERTIFICATE to Base64.encodeToString(certificate.encoded, Base64.NO_WRAP),
        )

    companion object {
        private val KEY_PASSWORD = "allcommand".toCharArray()

        fun fromCredentials(credentials: Map<String, String>): AndroidTvIdentity? {
            val key = credentials[AndroidTvCredentials.PRIVATE_KEY] ?: return null
            val cert = credentials[AndroidTvCredentials.CERTIFICATE] ?: return null
            return runCatching {
                val privateKey =
                    KeyFactory
                        .getInstance("RSA")
                        .generatePrivate(PKCS8EncodedKeySpec(Base64.decode(key, Base64.NO_WRAP)))
                val certificate =
                    CertificateFactory
                        .getInstance("X.509")
                        .generateCertificate(ByteArrayInputStream(Base64.decode(cert, Base64.NO_WRAP)))
                        as X509Certificate
                AndroidTvIdentity(privateKey, certificate)
            }.getOrNull()
        }

        fun generate(): AndroidTvIdentity {
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            return AndroidTvIdentity(pair.private, selfSignedCertificate(pair.private, pair.public.encoded))
        }

        /** Certificat X.509 v3 auto-signé (SHA-256 avec RSA) construit en DER, sans bibliothèque tierce. */
        internal fun selfSignedCertificate(
            privateKey: PrivateKey,
            subjectPublicKeyInfo: ByteArray,
            now: Date = Date(),
        ): X509Certificate {
            val signatureAlgorithm = Der.sequence(Der.oid("1.2.840.113549.1.1.11"), Der.NULL)
            val name = Der.sequence(Der.set(Der.sequence(Der.oid("2.5.4.3"), Der.utf8("allCommand"))))
            val validity =
                Der.sequence(Der.utcTime(Date(now.time - DAY_MILLIS)), Der.utcTime(Date(now.time + VALID_MILLIS)))
            val serial = BigInteger(SERIAL_BITS, SecureRandom()).add(BigInteger.ONE)
            val tbs =
                Der.sequence(
                    Der.tlv(0xA0, Der.integer(BigInteger.valueOf(2))),
                    Der.integer(serial),
                    signatureAlgorithm,
                    name,
                    validity,
                    name,
                    subjectPublicKeyInfo,
                )
            val signature =
                Signature.getInstance("SHA256withRSA").run {
                    initSign(privateKey)
                    update(tbs)
                    sign()
                }
            val der = Der.sequence(tbs, signatureAlgorithm, Der.tlv(0x03, byteArrayOf(0) + signature))
            return CertificateFactory
                .getInstance(
                    "X.509",
                ).generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        }

        private const val DAY_MILLIS = 24L * 3600 * 1000
        private const val VALID_MILLIS = 20L * 365 * DAY_MILLIS
        private const val SERIAL_BITS = 63
    }
}

/** Clés du dictionnaire [com.lielu.allcommand.device.Device.credentials] d'une Android TV. */
internal object AndroidTvCredentials {
    const val PRIVATE_KEY = "privateKey"
    const val CERTIFICATE = "certificate"
}

/** Encodage DER minimal, juste ce qu'il faut pour un certificat. */
internal object Der {
    val NULL = byteArrayOf(0x05, 0x00)

    fun tlv(
        tag: Int,
        content: ByteArray,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        when {
            content.size < 0x80 -> out.write(content.size)
            content.size < 0x100 -> {
                out.write(0x81)
                out.write(content.size)
            }
            else -> {
                out.write(0x82)
                out.write(content.size shr 8)
                out.write(content.size and 0xFF)
            }
        }
        out.write(content)
        return out.toByteArray()
    }

    fun sequence(vararg parts: ByteArray): ByteArray = tlv(0x30, parts.fold(ByteArray(0)) { acc, p -> acc + p })

    fun set(vararg parts: ByteArray): ByteArray = tlv(0x31, parts.fold(ByteArray(0)) { acc, p -> acc + p })

    fun integer(value: BigInteger): ByteArray = tlv(0x02, value.toByteArray())

    fun utf8(value: String): ByteArray = tlv(0x0C, value.toByteArray(Charsets.UTF_8))

    fun utcTime(date: Date): ByteArray {
        val format = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return tlv(0x17, format.format(date).toByteArray(Charsets.US_ASCII))
    }

    fun oid(dotted: String): ByteArray {
        val arcs = dotted.split('.').map(String::toLong)
        val out = ByteArrayOutputStream()
        out.write((arcs[0] * 40 + arcs[1]).toInt())
        for (arc in arcs.drop(2)) {
            val groups = mutableListOf((arc and 0x7F).toInt())
            var rest = arc ushr 7
            while (rest > 0) {
                groups.add(0, ((rest and 0x7F) or 0x80).toInt())
                rest = rest ushr 7
            }
            groups.forEach(out::write)
        }
        return tlv(0x06, out.toByteArray())
    }
}
