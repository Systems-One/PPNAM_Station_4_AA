package com.mitas.ppnam.station4aa.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ScramCryptoTest {

    @Test
    fun `computeProof is deterministic for identical inputs and its signature verifies`() {
        val saltBase64 = Base64.getEncoder().encodeToString(ByteArray(16) { it.toByte() })
        val clientNonce = ScramCrypto.generateClientNonce()
        val serverNonce = clientNonce + "server-suffix"
        val serverFirstMessage = "r=$serverNonce,s=$saltBase64,i=4096"
        val clientFinalWithoutProof = ScramCrypto.clientFinalWithoutProof(serverNonce)
        val authMessage = ScramCrypto.authMessage(
            clientFirstBare = ScramCrypto.clientFirstBare("operator1", clientNonce),
            serverFirstMessage = serverFirstMessage,
            clientFinalWithoutProof = clientFinalWithoutProof,
        )

        val proofA = ScramCrypto.computeProof("correct horse battery staple", saltBase64, 4096, authMessage)
        val proofB = ScramCrypto.computeProof("correct horse battery staple", saltBase64, 4096, authMessage)

        // Same inputs must yield the same proof and expected signature — this is what lets the
        // server independently recompute and verify it.
        assertEquals(proofA.clientProofBase64, proofB.clientProofBase64)
        assertEquals(proofA.expectedServerSignatureBase64, proofB.expectedServerSignatureBase64)
        assertTrue(ScramCrypto.verifyServerSignature(proofA.expectedServerSignatureBase64, proofB.expectedServerSignatureBase64))

        val wrongPasswordProof = ScramCrypto.computeProof("wrong password", saltBase64, 4096, authMessage)
        assertTrue(proofA.clientProofBase64 != wrongPasswordProof.clientProofBase64)
    }

    @Test
    fun `verifyServerSignature rejects a mismatched signature`() {
        assertEquals(false, ScramCrypto.verifyServerSignature("AAAA", "BBBB"))
    }

    @Test
    fun `verifyServerSignature rejects malformed base64`() {
        assertEquals(false, ScramCrypto.verifyServerSignature("not-base64!!", "also-not-base64!!"))
    }

    @Test
    fun `parseServerNonce accepts a combined nonce that extends the client nonce`() {
        val clientNonce = "abc123"
        val serverFirstMessage = "r=abc123xyz789,s=c2FsdA==,i=4096"
        assertEquals("abc123xyz789", ScramCrypto.parseServerNonce(serverFirstMessage, clientNonce))
    }

    @Test
    fun `parseServerNonce rejects a nonce that does not extend the client nonce`() {
        val serverFirstMessage = "r=totally-different,s=c2FsdA==,i=4096"
        assertNull(ScramCrypto.parseServerNonce(serverFirstMessage, "abc123"))
    }

    @Test
    fun `escapeUsername escapes equals before comma`() {
        // Order matters: escaping comma first would re-escape the '=' it just introduced.
        assertEquals("a=3D=2Cb", ScramCrypto.escapeUsername("a=,b"))
    }
}
