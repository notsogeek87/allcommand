package com.lielu.allcommand.data.devices

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lielu.allcommand.device.Device
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONException

class DeviceRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : DeviceRepository {
    override val devices: Flow<List<Device>> = dataStore.data.map { read(it[DEVICES_KEY]) }

    override suspend fun get(id: String): Device? = devices.first().firstOrNull { it.id == id }

    override suspend fun add(device: Device) {
        update { current -> current.filterNot { it.id == device.id } + device }
    }

    override suspend fun remove(id: String) {
        update { current -> current.filterNot { it.id == id } }
    }

    override suspend fun replaceFromBackup(devices: List<Device>) {
        update { current ->
            val known = current.associateBy { it.id }
            devices.map { restored -> restored.copy(credentials = known[restored.id]?.credentials.orEmpty()) }
        }
    }

    /** Lecture-modification-écriture dans une seule transaction DataStore : atomique. */
    private suspend fun update(transform: (List<Device>) -> List<Device>) {
        dataStore.edit { preferences ->
            val updated = transform(read(preferences[DEVICES_KEY]))
            preferences[DEVICES_KEY] = DeviceJson.encode(updated, includeCredentials = true).toString()
        }
    }

    private fun read(stored: String?): List<Device> =
        try {
            DeviceJson.decode(stored?.let { JSONArray(it) })
        } catch (e: JSONException) {
            emptyList()
        }

    private companion object {
        val DEVICES_KEY = stringPreferencesKey("devices_json")
    }
}
