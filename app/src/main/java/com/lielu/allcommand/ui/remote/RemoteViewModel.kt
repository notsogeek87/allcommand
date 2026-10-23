package com.lielu.allcommand.ui.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lielu.allcommand.data.devices.DeviceRepository
import com.lielu.allcommand.data.settings.HapticSettings
import com.lielu.allcommand.data.settings.SettingsRepository
import com.lielu.allcommand.device.Device
import com.lielu.allcommand.device.DeviceException
import com.lielu.allcommand.device.RemoteConnection
import com.lielu.allcommand.device.RemoteConnections
import com.lielu.allcommand.device.RemoteKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** État de la liaison avec l'appareil, montré dans l'en-tête. */
enum class ConnectionState {
    CONNECTING,
    CONNECTED,
    OFFLINE,
}

class RemoteViewModel(
    deviceId: String,
    private val repository: DeviceRepository,
    private val connections: RemoteConnections,
    settings: SettingsRepository,
) : ViewModel() {
    private val _device = MutableStateFlow<Device?>(null)
    val device: StateFlow<Device?> = _device

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _connection = MutableStateFlow(ConnectionState.CONNECTING)
    val connection: StateFlow<ConnectionState> = _connection

    val haptics: StateFlow<HapticSettings> =
        settings.haptics.stateIn(viewModelScope, SharingStarted.Eagerly, HapticSettings())

    init {
        viewModelScope.launch {
            val loaded = repository.get(deviceId)
            _device.value = loaded
            if (loaded == null) {
                _message.value = "Appareil introuvable."
                _connection.value = ConnectionState.OFFLINE
            } else if (loaded.isPaired) {
                // Réchauffe la liaison pour que la première touche parte tout de suite. L'erreur n'est pas affichée
                // ici : l'indicateur passe hors ligne, et l'envoi d'une touche réessaie puis explique.
                _connection.value =
                    if (runCatching { connections.get(loaded).open() }.isSuccess) {
                        ConnectionState.CONNECTED
                    } else {
                        ConnectionState.OFFLINE
                    }
            } else {
                _connection.value = ConnectionState.OFFLINE
            }
        }
    }

    fun onKey(key: RemoteKey) = run { it.send(key) }

    fun onText(text: String) = run { it.sendText(text) }

    fun onRename(name: String) {
        val current = _device.value ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == current.name) return
        viewModelScope.launch {
            val renamed = current.copy(name = trimmed)
            repository.add(renamed)
            _device.value = renamed
        }
    }

    fun onMessageShown() {
        _message.value = null
    }

    /** La connexion appartient à l'app et reste ouverte quand on quitte l'écran : rien à fermer ici. */
    private fun run(action: suspend (RemoteConnection) -> Unit) {
        viewModelScope.launch {
            try {
                val current = connection() ?: return@launch
                action(current)
                _connection.value = ConnectionState.CONNECTED
            } catch (e: Exception) {
                _connection.value = ConnectionState.OFFLINE
                _message.value = (e as? DeviceException)?.message ?: "Erreur inattendue : ${e.message}"
            }
        }
    }

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
