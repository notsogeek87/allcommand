package com.lielu.allcommand.device

/**
 * Connexions de télécommande gardées ouvertes pour toute la vie de l'app : quitter l'écran d'une télécommande
 * (ou en ouvrir une autre) ne coupe rien, et y revenir ne refait ni connexion ni appairage.
 */
class RemoteConnections(
    private val drivers: DeviceDrivers,
) {
    private class Entry(
        val device: Device,
        val connection: RemoteConnection,
    )

    private val entries = mutableMapOf<String, Entry>()

    /** La connexion de [device] ; une nouvelle si l'appareil a changé (adresse, secrets) depuis la précédente. */
    @Synchronized
    fun get(device: Device): RemoteConnection {
        val existing = entries[device.id]
        if (existing != null && existing.device == device) return existing.connection
        existing?.connection?.close()
        return drivers
            .forType(device.type)
            .connect(device)
            .also { entries[device.id] = Entry(device, it) }
    }

    /** Ferme les connexions des appareils qui ne sont plus enregistrés. */
    @Synchronized
    fun retain(ids: Set<String>) {
        val removed = entries.keys.filter { it !in ids }
        removed.forEach { entries.remove(it)?.connection?.close() }
    }
}
