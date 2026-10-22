package com.lielu.allcommand.device

/**
 * Pilote d'un type d'appareil : sait l'appairer et ouvrir une connexion de télécommande.
 *
 * Ajouter un type d'appareil = un [DeviceType], un pilote, et son enregistrement dans [DeviceDrivers].
 */
interface DeviceDriver {
    val type: DeviceType

    /** Faux tant que le protocole du type n'est pas branché : l'interface le dit au lieu de proposer un échec. */
    val isAvailable: Boolean get() = true

    /** Ouvre l'appairage : l'appareil affiche un code que l'utilisateur saisit ensuite via [PairingSession.complete]. */
    suspend fun startPairing(host: String): PairingSession

    /** Prépare une connexion de télécommande ; le réseau n'est sollicité qu'au premier [RemoteConnection.send]. */
    fun connect(device: Device): RemoteConnection
}

/** Appairage en cours, en attente du code affiché par l'appareil. */
interface PairingSession {
    /** Valide le code ; renvoie les secrets à enregistrer avec l'appareil ([Device.credentials]). */
    suspend fun complete(code: String): Map<String, String>

    /** Abandonne l'appairage et libère la connexion. */
    fun cancel()
}

/** Connexion de télécommande vers un appareil appairé. */
interface RemoteConnection {
    suspend fun send(key: RemoteKey)

    fun close()
}

/** Erreur d'appareil dont le message est affiché tel quel à l'utilisateur (en français). */
class DeviceException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
