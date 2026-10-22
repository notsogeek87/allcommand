package com.lielu.allcommand.data.devices

import com.lielu.allcommand.device.Device
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    val devices: Flow<List<Device>>

    suspend fun get(id: String): Device?

    /** Ajoute l'appareil, ou remplace celui qui a le même identifiant. */
    suspend fun add(device: Device)

    suspend fun remove(id: String)

    /**
     * Remplace la liste par celle d'une sauvegarde, de façon atomique. Une sauvegarde ne contient pas les secrets
     * d'appairage : ceux des appareils déjà présents (même identifiant) sont conservés, les nouveaux arrivent
     * sans secret et sont à appairer de nouveau.
     */
    suspend fun replaceFromBackup(devices: List<Device>)
}
