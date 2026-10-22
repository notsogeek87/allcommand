package com.lielu.allcommand.ui.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lielu.allcommand.data.devices.DeviceRepository
import com.lielu.allcommand.device.Device
import com.lielu.allcommand.device.DeviceDrivers
import com.lielu.allcommand.device.DeviceException
import com.lielu.allcommand.device.RemoteConnection
import com.lielu.allcommand.device.RemoteKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RemoteViewModel(
    deviceId: String,
    private val repository: DeviceRepository,
    private val drivers: DeviceDrivers,
) : ViewModel() {
    private val _device = MutableStateFlow<Device?>(null)
    val device: StateFlow<Device?> = _device

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private var connection: RemoteConnection? = null

    init {
        viewModelScope.launch {
            val loaded = repository.get(deviceId)
            _device.value = loaded
            if (loaded == null) _message.value = "Appareil introuvable."
        }
    }

    fun onKey(key: RemoteKey) {
        viewModelScope.launch {
            try {
                val current = connection ?: openConnection() ?: return@launch
                current.send(key)
            } catch (e: Exception) {
                _message.value = (e as? DeviceException)?.message ?: "Erreur inattendue : ${e.message}"
            }
        }
    }

    fun onMessageShown() {
        _message.value = null
    }

    override fun onCleared() {
        connection?.close()
    }

    /** Appelée sur le fil principal sans suspension : deux touches rapprochées ne créent qu'une connexion. */
    private fun openConnection(): RemoteConnection? {
        val device = _device.value
        if (device == null) {
            _message.value = "Appareil en cours de chargement, réessayez."
            return null
        }
        if (!device.isPaired) {
            _message.value = "Cet appareil n'est pas appairé : supprimez-le puis ajoutez-le de nouveau."
            return null
        }
        return drivers.forType(device.type).connect(device).also { connection = it }
    }
}
