package com.lielu.allcommand.data.devices

import com.lielu.allcommand.device.Device
import com.lielu.allcommand.device.DeviceType
import org.json.JSONArray
import org.json.JSONObject

/**
 * Représentation JSON des appareils, partagée par le stockage et la sauvegarde. Lecture tolérante : un appareil
 * illisible ou d'un type inconnu (fichier écrit par une version plus récente) est ignoré, pas fatal.
 */
internal object DeviceJson {
    fun encode(
        devices: List<Device>,
        includeCredentials: Boolean,
    ): JSONArray {
        val array = JSONArray()
        devices.forEach { device ->
            val json =
                JSONObject()
                    .put("id", device.id)
                    .put("name", device.name)
                    .put("type", device.type.storageId)
                    .put("host", device.host)
            if (includeCredentials) {
                val credentials = JSONObject()
                device.credentials.forEach { (key, value) -> credentials.put(key, value) }
                json.put("credentials", credentials)
            }
            array.put(json)
        }
        return array
    }

    fun decode(array: JSONArray?): List<Device> {
        if (array == null) return emptyList()
        val devices = mutableListOf<Device>()
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            val type = DeviceType.fromStorageId(json.optString("type")) ?: continue
            val id = json.optString("id")
            val host = json.optString("host")
            if (id.isEmpty() || host.isEmpty()) continue
            devices += Device(id, json.optString("name", type.label), type, host, readCredentials(json))
        }
        return devices
    }

    private fun readCredentials(json: JSONObject): Map<String, String> {
        val credentials = json.optJSONObject("credentials") ?: return emptyMap()
        return credentials.keys().asSequence().associateWith { credentials.optString(it) }
    }
}
