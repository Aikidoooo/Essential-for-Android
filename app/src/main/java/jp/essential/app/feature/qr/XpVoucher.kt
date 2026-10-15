package jp.essential.app.feature.qr

import java.math.BigInteger
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

/** 配布元の公開鍵だけで検証する。QR内の公開鍵やアルゴリズムは信用しない。 */
object XpVoucher {
    const val PREFIX = "essential-xp:"
    data class Grant(val id: String, val points: Int, val issuedAt: Long, val expiresAt: Long)

    fun verify(value: String, modulus: String, now: Long = System.currentTimeMillis() / 1000): Grant {
        require(value.length <= 2048 && value.startsWith(PREFIX)) { "XP配布QRの形式が違います" }
        val parts = value.removePrefix(PREFIX).split('.')
        require(parts.size == 2) { "XP配布QRの形式が違います" }
        fun decode(part: String): ByteArray {
            require(part.matches(Regex("[A-Za-z0-9_-]+"))) { "XP配布QRの形式が違います" }
            val bytes = Base64.getUrlDecoder().decode(part)
            require(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) == part) { "XP配布QRの形式が違います" }
            return bytes
        }
        val payload = decode(parts[0])
        val signature = decode(parts[1])
        val key = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(
            BigInteger(1, Base64.getDecoder().decode(modulus.trim())), BigInteger.valueOf(65537),
        ))
        require(signature.size == 384 && Signature.getInstance("SHA256withRSA").run {
            initVerify(key)
            update(payload)
            verify(signature)
        }) { "署名が無効です。正規の配布QRではありません" }
        require(payload.all { it.toInt() in 32..126 }) { "XP配布QRの内容が不正です" }
        val fields = payload.toString(Charsets.US_ASCII).split('|')
        require(fields.size == 6 && fields[0] == "1" && fields[1] == "jp.essential.app") { "Essential用の配布QRではありません" }
        require(fields[2].matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))) { "配布IDが不正です" }
        require(fields.drop(3).all { it.matches(Regex("[1-9][0-9]{0,11}")) }) { "配布値が不正です" }
        val points = fields[3].toIntOrNull() ?: error("XPが不正です")
        val issued = fields[4].toLong()
        val expires = fields[5].toLong()
        require(points in 1..1_000_000 && issued < expires && expires - issued <= 366L * 86400) { "配布範囲が不正です" }
        require(now >= issued - 300 && now < expires) { "有効期限切れ、または端末の日時が正しくありません" }
        return Grant(fields[2], points, issued, expires)
    }
}
