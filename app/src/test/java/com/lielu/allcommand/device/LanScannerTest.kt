package com.lielu.allcommand.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanScannerTest {
    @Test
    fun `subnetHosts couvre le reseau sauf le telephone`() {
        val hosts = LanScanner.subnetHosts("192.168.1.42")
        assertEquals(253, hosts.size)
        assertTrue("192.168.1.1" in hosts)
        assertTrue("192.168.1.254" in hosts)
        assertFalse("192.168.1.42" in hosts)
    }

    @Test
    fun `subnetHosts ignore une adresse invalide`() {
        assertTrue(LanScanner.subnetHosts("pas-une-ip").isEmpty())
    }
}
