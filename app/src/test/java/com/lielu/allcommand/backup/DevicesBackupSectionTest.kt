package com.lielu.allcommand.backup

import com.lielu.allcommand.device.Device
import com.lielu.allcommand.device.DeviceType
import com.lielu.allcommand.testutil.FakeDeviceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicesBackupSectionTest {
    private val tv = Device("1", "Salon", DeviceType.TITAN_OS, "192.168.1.20", mapOf("password" to "secret"))

    @Test
    fun exportContainsNoCredentials() =
        runTest {
            val exported = DevicesBackupSection(FakeDeviceRepository(listOf(tv))).export().toString()
            assertTrue(!exported.contains("secret"))
        }

    @Test
    fun restoreKeepsCredentialsOfKnownDevicesAndDropsRemovedOnes() =
        runTest {
            val repository = FakeDeviceRepository(listOf(tv, tv.copy(id = "2", name = "Chambre")))
            val section = DevicesBackupSection(repository)
            val backup = DevicesBackupSection(FakeDeviceRepository(listOf(tv.copy(name = "Salon 2")))).export()

            section.restore(backup)

            val devices = repository.devices.first()
            assertEquals(listOf("1"), devices.map { it.id })
            assertEquals("Salon 2", devices.single().name)
            assertEquals(tv.credentials, devices.single().credentials)
        }
}
