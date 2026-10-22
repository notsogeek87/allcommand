package com.lielu.allcommand.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lielu.allcommand.data.devices.DeviceRepository
import com.lielu.allcommand.device.Device
import com.lielu.allcommand.device.DeviceDrivers
import com.lielu.allcommand.device.DeviceException
import com.lielu.allcommand.device.DeviceScanner
import com.lielu.allcommand.device.DeviceType
import com.lielu.allcommand.device.LanScanner
import com.lielu.allcommand.device.PairingSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** Étapes de l'ajout d'un appareil. */
sealed interface AddDeviceStep {
    /** Saisie du type, du nom et de l'adresse. */
    data object Form : AddDeviceStep

    /** Échange en cours avec l'appareil. */
    data object Working : AddDeviceStep

    /** L'appareil affiche un code, à saisir. */
    data object AwaitingCode : AddDeviceStep

    /** Appareil enregistré. */
    data object Done : AddDeviceStep
}

class AddDeviceViewModel(
    private val repository: DeviceRepository,
    private val drivers: DeviceDrivers,
    private val scanner: DeviceScanner = LanScanner(),
) : ViewModel() {
    private val _type = MutableStateFlow(DeviceType.TITAN_OS)
    val type: StateFlow<DeviceType> = _type

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name

    private val _host = MutableStateFlow("")
    val host: StateFlow<String> = _host

    private val _step = MutableStateFlow<AddDeviceStep>(AddDeviceStep.Form)
    val step: StateFlow<AddDeviceStep> = _step

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    /** Adresses trouvées sur le réseau pour le type choisi ; `null` tant qu'aucune recherche n'a abouti. */
    private val _found = MutableStateFlow<List<String>?>(null)
    val found: StateFlow<List<String>?> = _found

    private var session: PairingSession? = null
    private var scanJob: Job? = null

    init {
        scan()
    }

    /** Cherche les appareils du type choisi sur le Wi-Fi, pour éviter de taper leur adresse. */
    fun scan() {
        scanJob?.cancel()
        val type = _type.value
        _found.value = null
        _scanning.value = true
        scanJob =
            viewModelScope.launch {
                try {
                    _found.value = scanner.scan(type.discoveryPort)
                } finally {
                    _scanning.value = false
                }
            }
    }

    fun isAvailable(type: DeviceType): Boolean = drivers.forType(type).isAvailable

    fun onTypeChange(value: DeviceType) {
        _type.value = value
        _error.value = null
        scan()
    }

    fun onNameChange(value: String) {
        _name.value = value
    }

    fun onHostChange(value: String) {
        _host.value = value
    }

    fun startPairing() {
        val type = _type.value
        val host = _host.value.trim()
        if (!HOST_FORMAT.matches(host)) {
            _error.value = "Saisissez l'adresse IP de l'appareil, par exemple 192.168.1.20."
            return
        }
        _error.value = null
        _step.value = AddDeviceStep.Working
        viewModelScope.launch {
            try {
                session = drivers.forType(type).startPairing(host)
                _step.value = AddDeviceStep.AwaitingCode
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun submitCode(code: String) {
        val pairing = session ?: return
        val type = _type.value
        _error.value = null
        _step.value = AddDeviceStep.Working
        viewModelScope.launch {
            try {
                val credentials = pairing.complete(code)
                session = null
                val name = _name.value.trim().ifEmpty { type.label }
                repository.add(Device(UUID.randomUUID().toString(), name, type, _host.value.trim(), credentials))
                _step.value = AddDeviceStep.Done
            } catch (e: Exception) {
                // Un appairage ne se rejoue pas : la télé génère un nouveau code à chaque tentative.
                session = null
                fail(e)
            }
        }
    }

    fun cancelPairing() {
        session?.cancel()
        session = null
        _error.value = null
        _step.value = AddDeviceStep.Form
    }

    override fun onCleared() {
        scanJob?.cancel()
        session?.cancel()
    }

    private fun fail(error: Exception) {
        _error.value = (error as? DeviceException)?.message ?: "Erreur inattendue : ${error.message}"
        _step.value = AddDeviceStep.Form
    }

    private companion object {
        val HOST_FORMAT = Regex("[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?")
    }
}
