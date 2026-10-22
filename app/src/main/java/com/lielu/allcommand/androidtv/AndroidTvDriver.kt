package com.lielu.allcommand.androidtv

import com.lielu.allcommand.device.Device
import com.lielu.allcommand.device.DeviceDriver
import com.lielu.allcommand.device.DeviceException
import com.lielu.allcommand.device.DeviceType
import com.lielu.allcommand.device.PairingSession
import com.lielu.allcommand.device.RemoteConnection

/**
 * Emplacement du pilote Android TV (protocole « Android TV Remote v2 », ports 6466/6467).
 *
 * Volontairement non branché : le protocole n'a pas encore été validé sur une vraie télé, et on ne livre pas
 * un pilote dont on ne sait pas qu'il fonctionne. Le type d'appareil existe déjà dans l'app (liste, stockage,
 * sauvegarde) ; l'interface refuse l'appairage tant que [isAvailable] est faux. Pour le brancher : implémenter
 * [startPairing] et [connect], puis passer [isAvailable] à vrai.
 */
class AndroidTvDriver : DeviceDriver {
    override val type: DeviceType = DeviceType.ANDROID_TV

    override val isAvailable: Boolean = false

    override suspend fun startPairing(host: String): PairingSession = throw DeviceException(NOT_AVAILABLE)

    override fun connect(device: Device): RemoteConnection = throw DeviceException(NOT_AVAILABLE)

    private companion object {
        const val NOT_AVAILABLE = "Le pilotage des Android TV n'est pas encore pris en charge."
    }
}
