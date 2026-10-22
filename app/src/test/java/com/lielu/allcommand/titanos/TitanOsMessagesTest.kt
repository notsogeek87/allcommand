package com.lielu.allcommand.titanos

import com.lielu.allcommand.device.DeviceException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TitanOsMessagesTest {
    @Test
    fun returnsTheDataObject() {
        val data = TitanOsMessages.parseReply("""{"data":{"challenge":"abc","timeout":60}}""")
        assertEquals("abc", data.getString("challenge"))
    }

    @Test
    fun invalidAuthenticationHasAReadableMessage() {
        val error =
            assertThrows(DeviceException::class.java) {
                TitanOsMessages.parseReply("""{"error":{"code":7,"message":"Invalid authentication"}}""")
            }
        assertTrue(error.message!!.contains("Code incorrect"))
    }

    @Test
    fun pairingInProgressHasAReadableMessage() {
        val error =
            assertThrows(DeviceException::class.java) {
                TitanOsMessages.parseReply("""{"error":{"code":8,"message":"x"}}""")
            }
        assertTrue(error.message!!.contains("déjà en cours"))
    }

    @Test
    fun unknownErrorKeepsCodeAndMessage() {
        val error =
            assertThrows(DeviceException::class.java) {
                TitanOsMessages.parseReply("""{"error":{"code":42,"message":"boom"}}""")
            }
        assertTrue(error.message!!.contains("42") && error.message!!.contains("boom"))
    }

    @Test
    fun garbageAndMissingDataAreRejected() {
        assertThrows(DeviceException::class.java) { TitanOsMessages.parseReply("pas du json") }
        assertThrows(DeviceException::class.java) { TitanOsMessages.parseReply("{}") }
    }
}
