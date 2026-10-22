package com.lielu.allcommand.titanos

import org.junit.Assert.assertEquals
import org.junit.Test

class TitanOsPairingTest {
    /** Vecteur relevé sur la télé réelle : le défi est signé tel quel, avec le PIN comme clé. */
    @Test
    fun signChallengeMatchesValueAcceptedByTheTv() {
        assertEquals(
            "bf3198b7dc7444b47fa7b31bf75f3cf70f229bbe9867c05a681a9c0c63339a3d",
            TitanOsPairing.signChallenge("6334", "Ucf2n6zSPWKj9wo9PmzdUQ=="),
        )
    }
}
