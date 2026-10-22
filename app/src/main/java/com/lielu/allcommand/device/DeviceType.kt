package com.lielu.allcommand.device

/**
 * Familles d'appareils pilotables. [storageId] est écrit dans le stockage et les sauvegardes : ne jamais le
 * renommer, sous peine de ne plus reconnaître les appareils déjà enregistrés.
 */
enum class DeviceType(
    val storageId: String,
    val label: String,
    val pairingHint: String,
) {
    TITAN_OS(
        storageId = "titan_os",
        label = "Philips Titan OS",
        pairingHint = "Un code à 4 chiffres va s'afficher sur la télé.",
    ),
    ANDROID_TV(
        storageId = "android_tv",
        label = "Android TV",
        pairingHint = "Un code va s'afficher sur la télé.",
    ),
    ;

    companion object {
        fun fromStorageId(id: String?): DeviceType? = entries.firstOrNull { it.storageId == id }
    }
}
