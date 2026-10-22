package com.lielu.allcommand.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lielu.allcommand.data.devices.DeviceRepository
import com.lielu.allcommand.device.Device
import com.lielu.allcommand.device.DeviceDrivers
import com.lielu.allcommand.device.DeviceException
import com.lielu.allcommand.device.DeviceNamer
import com.lielu.allcommand.device.DeviceScanner
import com.lielu.allcommand.device.DeviceType
import com.lielu.allcommand.device.LanScanner
import com.lielu.allcommand.device.PairingSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/** Appareil repéré sur le réseau. */
data class FoundDevice(
    val type: DeviceType,
    val host: String,
    /** Nom annoncé par l'appareil (« Salon »…), si on l'a trouvé. */
    val name: String? = null,
) {
    /** Ce qu'on montre à l'utilisateur : le nom s'il existe, sinon le type, toujours suivi de l'adresse. */
    val title: String get() = "${name ?: type.label} · $host"
}

/** Ajout en série des appareils trouvés : position courante, [total] et appareils déjà traités. */
data class BatchProgress(
    val position: Int,
    val total: Int,
    val device: FoundDevice,
)

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
    private val namer: DeviceNamer,
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
    private val _found = MutableStateFlow<List<FoundDevice>?>(null)
    val found: StateFlow<List<FoundDevice>?> = _found

    private val _batch = MutableStateFlow<BatchProgress?>(null)
    val batch: StateFlow<BatchProgress?> = _batch

    private var queue: List<FoundDevice> = emptyList()
    private var lastSuggestedName: String? = null
    private var queueIndex = 0
    private var addedCount = 0
    private val failedHosts = mutableListOf<String>()

    private var session: PairingSession? = null
    private var scanJob: Job? = null

    init {
        scan()
    }

    /** Cherche sur le Wi-Fi les appareils de tous les types pris en charge, pour éviter de taper leur adresse. */
    fun scan() {
        scanJob?.cancel()
        _found.value = null
        _scanning.value = true
        scanJob =
            viewModelScope.launch {
                try {
                    val types = DeviceType.entries.filter(::isAvailable)
                    val devices =
                        types
                            .map { type ->
                                async {
                                    val hosts = scanner.scan(type.discoveryPort)
                                    // Un port ouvert ne prouve rien : on écarte box, aspirateurs, etc.
                                    hosts
                                        .map { host -> async { host.takeIf { drivers.forType(type).probe(it) } } }
                                        .awaitAll()
                                        .filterNotNull()
                                        .map { FoundDevice(type, it) }
                                }
                            }.awaitAll()
                            .flatten()
                            .sortedBy { it.host }
                    val names = namer.names(devices.map { it.host })
                    _found.value = devices.map { it.copy(name = names[it.host]) }
                } finally {
                    _scanning.value = false
                }
            }
    }

    fun select(device: FoundDevice) {
        _type.value = device.type
        _host.value = device.host
        if (_name.value.isBlank() || _name.value == lastSuggestedName) {
            _name.value = device.name.orEmpty()
            lastSuggestedName = device.name
        }
        _error.value = null
    }

    /** Appaire à la suite tous les appareils trouvés qui ne sont pas déjà enregistrés. */
    fun addAll() {
        val found = _found.value.orEmpty()
        viewModelScope.launch {
            val known =
                repository.devices
                    .first()
                    .map { it.host }
                    .toSet()
            queue = found.filter { it.host !in known }
            if (queue.isEmpty()) {
                _error.value = "Aucun nouvel appareil à ajouter : ceux trouvés sont déjà enregistrés."
                return@launch
            }
            queueIndex = 0
            addedCount = 0
            failedHosts.clear()
            _error.value = null
            pairCurrent()
        }
    }

    /** Abandonne l'appareil en cours d'une série et passe au suivant. */
    fun skipCurrent() {
        failedHosts += queue[queueIndex].host
        session?.cancel()
        session = null
        advance()
    }

    private fun pairCurrent() {
        val device = queue[queueIndex]
        _type.value = device.type
        _host.value = device.host
        _batch.value = BatchProgress(queueIndex + 1, queue.size, device)
        _step.value = AddDeviceStep.Working
        viewModelScope.launch {
            try {
                session = drivers.forType(device.type).startPairing(device.host)
                _step.value = AddDeviceStep.AwaitingCode
            } catch (e: Exception) {
                failedHosts += device.host
                _error.value = (e as? DeviceException)?.message ?: "Erreur inattendue : ${e.message}"
                advance()
            }
        }
    }

    private fun advance() {
        queueIndex++
        if (queueIndex < queue.size) {
            pairCurrent()
            return
        }
        _batch.value = null
        queue = emptyList()
        if (failedHosts.isEmpty() && addedCount > 0) {
            _step.value = AddDeviceStep.Done
        } else {
            _error.value = "$addedCount appareil(s) ajouté(s). Échec ou abandon : ${failedHosts.joinToString()}."
            _step.value = AddDeviceStep.Form
        }
    }

    fun isAvailable(type: DeviceType): Boolean = drivers.forType(type).isAvailable

    fun onNameChange(value: String) {
        _name.value = value
    }

    fun onHostChange(value: String) {
        _host.value = value
    }

    /** Type de l'appareil à cette adresse : celui trouvé par le scan, sinon le premier pilote qui le reconnaît. */
    private suspend fun detectType(host: String): DeviceType {
        _found.value?.firstOrNull { it.host == host }?.let { return it.type }
        val candidates = DeviceType.entries.filter(::isAvailable)
        return candidates.firstOrNull { drivers.forType(it).probe(host) } ?: candidates.first()
    }

    fun startPairing() {
        val host = _host.value.trim()
        if (!HOST_FORMAT.matches(host)) {
            _error.value = "Saisissez l'adresse IP de l'appareil, par exemple 192.168.1.20."
            return
        }
        _error.value = null
        _step.value = AddDeviceStep.Working
        viewModelScope.launch {
            try {
                val type = detectType(host)
                _type.value = type
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
        val inBatch = _batch.value != null
        _error.value = null
        _step.value = AddDeviceStep.Working
        viewModelScope.launch {
            try {
                val credentials = pairing.complete(code)
                session = null
                val host = _host.value.trim()
                val name =
                    if (inBatch) {
                        _batch.value
                            ?.device
                            ?.let {
                                it.name ?: "${type.label} ${it.host}"
                            }.orEmpty()
                    } else {
                        _name.value.trim().ifEmpty { type.label }
                    }
                repository.add(Device(UUID.randomUUID().toString(), name, type, host, credentials))
                if (inBatch) {
                    addedCount++
                    advance()
                } else {
                    _step.value = AddDeviceStep.Done
                }
            } catch (e: Exception) {
                // Un appairage ne se rejoue pas : la télé génère un nouveau code à chaque tentative.
                session = null
                if (inBatch) {
                    failedHosts += _host.value
                    _error.value = (e as? DeviceException)?.message ?: "Erreur inattendue : ${e.message}"
                    advance()
                } else {
                    fail(e)
                }
            }
        }
    }

    fun cancelPairing() {
        session?.cancel()
        session = null
        queue = emptyList()
        _batch.value = null
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
