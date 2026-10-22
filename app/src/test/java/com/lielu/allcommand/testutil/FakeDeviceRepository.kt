package com.lielu.allcommand.testutil

import com.lielu.allcommand.data.devices.DeviceRepository
import com.lielu.allcommand.device.Device
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeDeviceRepository(
    initial: List<Device> = emptyList(),
) : DeviceRepository {
    private val state = MutableStateFlow(initial)
    override val devices: StateFlow<List<Device>> = state

    override suspend fun get(id: String): Device? = state.value.firstOrNull { it.id == id }

    override suspend fun add(device: Device) {
        state.value = state.value.filterNot { it.id == device.id } + device
    }

    override suspend fun remove(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun replaceFromBackup(devices: List<Device>) {
        val known = state.value.associateBy { it.id }
        state.value = devices.map { it.copy(credentials = known[it.id]?.credentials ?: it.credentials) }
    }
}
