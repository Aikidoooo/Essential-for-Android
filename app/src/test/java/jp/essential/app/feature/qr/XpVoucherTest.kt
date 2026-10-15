package jp.essential.app.feature.qr

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.UUID

class XpVoucherTest {
    private val key = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
    private val modulus = Base64.getEncoder().encodeToString((key.public as RSAPublicKey).modulus.toByteArray().let {
        if (it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it
    })
    private fun encode(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    private fun token(payload: String): String {
        val bytes = payload.toByteArray(Charsets.US_ASCII)
        val signed = Signature.getInstance("SHA256withRSA").run { initSign(key.private); update(bytes); sign() }
        return XpVoucher.PREFIX + encode(bytes) + "." + encode(signed)
    }
    private fun rejected(value: String, now: Long = 1000) {
        assertTrue(runCatching { XpVoucher.verify(value, modulus, now) }.isFailure)
    }
    @Test fun validSignatureAndBoundaries() {
        val id = UUID.randomUUID().toString()
        val value = token("1|jp.essential.app|$id|1200|1000|2000")
        assertEquals(1200, XpVoucher.verify(value, modulus, 1000).points)
        assertEquals(id, XpVoucher.verify(value, modulus, 1999).id)
        rejected(value, 2000)
        rejected(value, 699)
    }
    @Test fun tamperingUnknownSignerAndMalformedInputRejected() {
        val value = token("1|jp.essential.app|${UUID.randomUUID()}|1200|1000|2000")
        val fields = value.removePrefix(XpVoucher.PREFIX).split('.')
        val altered = String(Base64.getUrlDecoder().decode(fields[0]), Charsets.US_ASCII).replace("1200", "9999")
        rejected(XpVoucher.PREFIX + encode(altered.toByteArray()) + "." + fields[1])
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
        val otherModulus = Base64.getEncoder().encodeToString((other.public as RSAPublicKey).modulus.toByteArray())
        assertTrue(runCatching { XpVoucher.verify(value, otherModulus, 1000) }.isFailure)
        rejected(value + ".extra")
        rejected(value.replace(".", "=."))
        rejected("essential-xp:" + "a".repeat(3000))
    }
    @Test fun signedInvalidClaimsRejected() {
        val id = UUID.randomUUID()
        listOf("2|jp.essential.app|$id|1|1000|2000", "1|other.app|$id|1|1000|2000",
            "1|jp.essential.app|wrong-id|1|1000|2000", "1|jp.essential.app|$id|0|1000|2000",
            "1|jp.essential.app|$id|1000001|1000|2000", "1|jp.essential.app|$id|1|2000|1000",
            "1|jp.essential.app|$id|1|1000|99999999999").forEach { rejected(token(it)) }
    }
}
