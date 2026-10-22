package com.lielu.allcommand.androidtv

import com.lielu.allcommand.device.RemoteKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey

class AndroidTvMessagesTest {
    @Test
    fun `un message encode puis decode redonne ses champs`() {
        val frame =
            ProtoWriter()
                .varint(1, 300)
                .string(2, "salon")
                .message(3) { varint(1, 622) }
                .toFrame()
        val message = ProtoMessage.readFrame(ByteArrayInputStream(frame))
        assertEquals(300L, message.long(1))
        assertEquals("salon", message.bytes(2)?.toString(Charsets.UTF_8))
        assertEquals(622L, message.message(3)?.long(1))
    }

    @Test
    fun `la touche OK part en KEYCODE_DPAD_CENTER pour un appui court`() {
        val message = ProtoMessage.readFrame(ByteArrayInputStream(AndroidTvMessages.remoteKey(RemoteKey.OK)))
        val inject = message.message(10)
        assertEquals(23L, inject?.long(1))
        assertEquals(3L, inject?.long(2))
    }

    @Test
    fun `chaque touche a un code Android distinct`() {
        val codes = RemoteKey.entries.map { it.androidKeyCode() }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `le ping recoit la meme valeur en reponse`() {
        val ping = ProtoWriter().message(8) { varint(1, 42) }.toByteArray()
        val response = AndroidTvMessages.remotePingResponse(ProtoMessage.decode(ping))
        assertEquals(42L, ProtoMessage.readFrame(ByteArrayInputStream(response)).message(9)?.long(1))
    }

    @Test
    fun `le secret n'est accepte que si le premier octet du code correspond au hash`() {
        val generator = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }
        val client = generator.generateKeyPair().public as RSAPublicKey
        val server = generator.generateKeyPair().public as RSAPublicKey
        // Parmi les 256 premiers octets possibles, un seul convient : on le cherche.
        val valid =
            (0..255)
                .map { "%02X".format(it) + "ABCD" }
                .mapNotNull { code -> AndroidTvMessages.pairingSecret(client, server, code)?.let { code to it } }
        assertEquals(1, valid.size)
        assertEquals(32, valid.single().second.size)
        assertNull(
            AndroidTvMessages.pairingSecret(client, server, "00ABCD").takeIf { valid.single().first != "00ABCD" },
        )
    }

    @Test
    fun `le certificat auto-signe est un X509 valide signe par sa propre cle`() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val cert = AndroidTvIdentity.selfSignedCertificate(pair.private, pair.public.encoded)
        cert.verify(pair.public)
        cert.checkValidity()
        assertNotNull(cert.subjectX500Principal)
        assertTrue(cert.subjectX500Principal.name.contains("allCommand"))
        assertArrayEquals(pair.public.encoded, cert.publicKey.encoded)
    }
}
