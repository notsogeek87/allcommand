package com.lielu.allcommand.data

import com.lielu.allcommand.data.devices.DeviceJson
import com.lielu.allcommand.device.Device
import com.lielu.allcommand.device.DeviceType
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceJsonTest {
    private val tv = Device("1", "Salon", DeviceType.TITAN_OS, "192.168.1.20", mapOf("username" to "u"))

    @Test
    fun roundTripKeepsCredentialsWhenAsked() {
        val decoded = DeviceJson.decode(DeviceJson.encode(listOf(tv), includeCredentials = true))
        assertEquals(listOf(tv), decoded)
    }

    @Test
    fun encodingWithoutCredentialsLeaksNoSecret() {
        val text = DeviceJson.encode(listOf(tv), includeCredentials = false).toString()
        assertTrue(!text.contains("credentials") && !text.contains("\"u\""))
    }

    @Test
    fun unknownTypeAndIncompleteEntriesAreSkipped() {
        val array =
            JSONArray(
                """[{"id":"1","type":"inconnu","host":"h"},{"id":"","type":"titan_os","host":"h"},
                {"id":"2","type":"titan_os","host":"h","name":"ok"}]""",
            )
        assertEquals(listOf("2"), DeviceJson.decode(array).map { it.id })
    }

    @Test
    fun nullArrayGivesEmptyList() {
        assertTrue(DeviceJson.decode(null).isEmpty())
    }
}
