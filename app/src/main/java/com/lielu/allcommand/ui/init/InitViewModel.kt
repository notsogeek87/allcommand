package com.lielu.allcommand.ui.init

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lielu.allcommand.backup.BackupManager
import com.lielu.allcommand.data.devices.DeviceRepository
import com.lielu.allcommand.data.settings.SettingsRepository
import com.lielu.allcommand.device.Device
import com.lielu.allcommand.ui.describeImport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InitViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupManager: BackupManager,
    private val deviceRepository: DeviceRepository,
) : ViewModel() {
    /** Appareils enregistrés, affichés sur l'écran d'accueil. */
    val devices: StateFlow<List<Device>> =
        deviceRepository.devices.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Vrai tant que l'invitation de bienvenue n'a pas été traitée. Initialement faux : on n'affiche rien
     * avant que le DataStore ait répondu, pour ne pas faire clignoter l'invitation à chaque lancement.
     */
    val showWelcome: StateFlow<Boolean> =
        settingsRepository.welcomeShown
            .map { !it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun onWelcomeDismissed() {
        viewModelScope.launch { settingsRepository.setWelcomeShown() }
    }

    /** Restauration proposée au premier lancement (réinstallation, nouvel appareil) depuis un fichier choisi. */
    fun onImportRequested(
        contentResolver: ContentResolver,
        uri: Uri,
    ) {
        viewModelScope.launch {
            _message.value =
                try {
                    val stream =
                        contentResolver.openInputStream(uri) ?: error("Impossible de lire le fichier sélectionné")
                    val result = stream.use { backupManager.importFrom(it) }
                    settingsRepository.setWelcomeShown()
                    describeImport(result)
                } catch (e: Exception) {
                    "Échec de la restauration : ${e.message}"
                }
        }
    }

    fun onDeviceRemoved(id: String) {
        viewModelScope.launch { deviceRepository.remove(id) }
    }

    /** Annulation d'une suppression : remet l'appareil tel quel, appairage compris. */
    fun onDeviceRestored(device: Device) {
        viewModelScope.launch { deviceRepository.add(device) }
    }

    fun onMessageShown() {
        _message.value = null
    }
}
