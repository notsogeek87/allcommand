package com.lielu.allcommand.ui.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lielu.allcommand.data.devices.DeviceRepository
import com.lielu.allcommand.device.Device
import com.lielu.allcommand.device.DeviceException
import com.lielu.allcommand.device.RemoteConnection
import com.lielu.allcommand.device.RemoteConnections
import com.lielu.allcommand.device.RemoteKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RemoteViewModel(
    deviceId: String,
    private val repository: DeviceRepository,
    private val connections: RemoteConnections,
) : ViewModel() {
    private val _device = MutableStateFlow<Device?>(null)
    val device: StateFlow<Device?> = _device

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        viewModelScope.launch {
            val loaded = repository.get(deviceId)
            _device.value = loaded
            if (loaded == null) {
                _message.value = "Appareil introuvable."
            } else if (loaded.isPaired) {
                // Réchauffe la liaison pour que la première touche parte tout de suite. Une erreur est ignorée ici :
                // l'envoi d'une touche la réessaie et l'affiche.
                runCatching { connections.get(loaded).open() }
            }
        }
    }

    fun onKey(key: RemoteKey) {
        viewModelScope.launch {
            try {
                val current = connection() ?: return@launch
                current.send(key)
            } catch (e: Exception) {
                _message.value = (e as? DeviceException)?.message ?: "Erreur inattendue : ${e.message}"
            }
        }
    }

    fun onMessageShown() {
        _message.value = null
    }

    /** Pas de fermeture ici : la connexion appartient à l'app et reste ouverte quand on quitte l'écran. */
    private fun connection(): RemoteConnection? {
        val device = _device.value
        if (device == null) {
            _message.value = "Appareil en cours de chargement, réessayez."
            return null
        }
        if (!device.isPaired) {
            _message.value = "Cet appareil n'est pas appairé : supprimez-le puis ajoutez-le de nouveau."
            return null
        }
        return connections.get(device)
    }
}
