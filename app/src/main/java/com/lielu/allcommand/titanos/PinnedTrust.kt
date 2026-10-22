package com.lielu.allcommand.titanos

import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Provider
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.ManagerFactoryParameters
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.TrustManagerFactorySpi
import javax.net.ssl.X509TrustManager

/**
 * Confiance « première utilisation » pour le serveur MQTT d'une Titan OS.
 *
 * La télé présente un certificat émis par une autorité privée (« Titanos Root CA ») et on s'y connecte par son
 * adresse IP : la validation classique échouerait toujours. À la place, on retient l'empreinte SHA-256 de la clé
 * publique vue lors de l'appairage, et toute connexion suivante doit présenter la même clé. L'empreinte porte sur
 * la clé et non sur le certificat entier, pour survivre à un renouvellement du certificat avec la même clé.
 *
 * @param pinnedKey empreinte attendue ; `null` pendant l'appairage (on accepte et on retient ce qui est vu).
 */
internal class PinnedTrust(
    private val pinnedKey: String?,
) {
    /** Empreinte de la clé publique vue lors de la dernière poignée de main (`null` tant qu'il n'y en a pas eu). */
    @Volatile
    var observedKey: String? = null
        private set

    /** Vrai si la clé vue ne correspond pas à l'empreinte attendue. */
    @Volatile
    var mismatch: Boolean = false
        private set

    private val trustManager =
        object : X509TrustManager {
            override fun checkClientTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?,
            ) = throw CertificateException("Authentification client non prise en charge")

            override fun checkServerTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?,
            ) {
                val leaf = chain?.firstOrNull() ?: throw CertificateException("Aucun certificat présenté")
                val seen = fingerprint(leaf)
                observedKey = seen
                if (pinnedKey != null && pinnedKey != seen) {
                    mismatch = true
                    throw CertificateException("La clé de la télé a changé")
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

    val factory: TrustManagerFactory = FixedTrustManagerFactory(trustManager)

    private fun fingerprint(certificate: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(certificate.publicKey.encoded)
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    /** `TrustManagerFactory` qui renvoie un gestionnaire de confiance fixe, sans magasin de certificats. */
    private class FixedTrustManagerFactory(
        trustManager: X509TrustManager,
    ) : TrustManagerFactory(
            FixedSpi(trustManager),
            object : Provider("allcommand-pinned", 1.0, "Confiance par empreinte de clé") {},
            "pinned",
        )

    private class FixedSpi(
        private val trustManager: X509TrustManager,
    ) : TrustManagerFactorySpi() {
        override fun engineInit(keyStore: KeyStore?) = Unit

        override fun engineInit(parameters: ManagerFactoryParameters?) = Unit

        override fun engineGetTrustManagers(): Array<TrustManager> = arrayOf(trustManager)
    }
}
