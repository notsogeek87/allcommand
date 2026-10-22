package com.lielu.allcommand.device

/**
 * Un appareil enregistré dans l'app.
 *
 * [credentials] contient les secrets d'appairage, propres à chaque type (identifiants, empreinte du certificat).
 * Ils restent sur l'appareil : ils ne sont jamais écrits dans un fichier de sauvegarde (voir
 * `DevicesBackupSection`) et le dossier de stockage est exclu de la sauvegarde Android.
 */
data class Device(
    val id: String,
    val name: String,
    val type: DeviceType,
    val host: String,
    val credentials: Map<String, String> = emptyMap(),
) {
    /** Faux après une restauration de sauvegarde : l'appareil est connu mais doit être appairé de nouveau. */
    val isPaired: Boolean get() = credentials.isNotEmpty()
}
