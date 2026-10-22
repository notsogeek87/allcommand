package com.lielu.allcommand.device

/** Registre des pilotes disponibles, un par [DeviceType]. */
class DeviceDrivers(
    drivers: List<DeviceDriver>,
) {
    private val byType = drivers.associateBy { it.type }

    init {
        require(byType.size == drivers.size) { "Deux pilotes déclarent le même type d'appareil" }
    }

    fun forType(type: DeviceType): DeviceDriver = byType[type] ?: error("Aucun pilote pour $type")
}
