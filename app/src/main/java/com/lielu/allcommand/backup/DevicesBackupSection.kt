package com.lielu.allcommand.backup

import com.lielu.allcommand.data.devices.DeviceJson
import com.lielu.allcommand.data.devices.DeviceRepository
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/**
 * Liste des appareils (nom, type, adresse). Les secrets d'appairage n'y figurent volontairement pas : un fichier
 * de sauvegarde se range dans un cloud ou un dossier partagé, et il donnerait le contrôle de la télé à qui le lit.
 * Après une restauration, les appareils reviennent donc « à appairer ».
 */
class DevicesBackupSection(
    private val devices: DeviceRepository,
) : BackupSection {
    override val id: String = "devices"

    override suspend fun export(): JSONObject =
        JSONObject().put("devices", DeviceJson.encode(devices.devices.first(), includeCredentials = false))

    override suspend fun restore(data: JSONObject) {
        devices.replaceFromBackup(DeviceJson.decode(data.optJSONArray("devices")))
    }
}
