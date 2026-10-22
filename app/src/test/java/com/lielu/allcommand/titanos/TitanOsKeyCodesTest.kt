package com.lielu.allcommand.titanos

import com.lielu.allcommand.device.RemoteKey
import org.junit.Assert.assertEquals
import org.junit.Test

class TitanOsKeyCodesTest {
    @Test
    fun everyKeyHasADistinctCode() {
        val codes = RemoteKey.entries.map { it.titanKeyCode() }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun knownCodesMatchLinuxInputValues() {
        assertEquals(113, RemoteKey.MUTE.titanKeyCode())
        assertEquals(352, RemoteKey.OK.titanKeyCode())
        assertEquals(158, RemoteKey.BACK.titanKeyCode())
    }
}
