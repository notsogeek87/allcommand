package com.lielu.allcommand.device

/**
 * Familles d'appareils pilotables. [storageId] est écrit dans le stockage et les sauvegardes : ne jamais le
 * renommer, sous peine de ne plus reconnaître les appareils déjà enregistrés.
 */
enum class DeviceType(
    val storageId: String,
    val label: String,
    val pairingHint: String,
    /** Port TCP que l'appareil ouvre quand il est allumé : sert à le retrouver sur le réseau. */
    val discoveryPort: Int,
    /** Où trouver l'adresse IP à la main, si la recherche automatique ne l'a pas trouvé. */
    val ipHelp: String,
) {
    TITAN_OS(
        storageId = "titan_os",
        label = "Philips Titan OS",
        pairingHint = "Un code à 4 chiffres va s'afficher sur la télé.",
        discoveryPort = 8883,
        ipHelp = "Sur la télé : Paramètres > Réseau > Informations sur le réseau.",
    ),
    ANDROID_TV(
        storageId = "android_tv",
        label = "Android TV",
        pairingHint = "Un code à 6 caractères va s'afficher sur la télé.",
        discoveryPort = 6466,
        ipHelp = "Sur la télé : Paramètres > Réseau et Internet > votre Wi-Fi.",
    ),
    ;

    companion object {
        fun fromStorageId(id: String?): DeviceType? = entries.firstOrNull { it.storageId == id }
    }
}
