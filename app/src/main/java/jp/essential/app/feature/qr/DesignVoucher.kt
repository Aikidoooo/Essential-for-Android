package jp.essential.app.feature.qr

import java.math.BigInteger
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

/** デザイン配布専用の署名領域を使い、XP配布QRとの取り違えを拒否する。 */
object DesignVoucher {
    const val PREFIX = "essential-design:"
    data class Grant(val id: String, val designId: String, val issuedAt: Long, val startsAt: Long, val endsAt: Long)
    fun verify(value: String, modulus: String, designIds: Set<String>, now: Long = System.currentTimeMillis() / 1000): Grant {
        require(value.length <= 2048 && value.startsWith(PREFIX)) { "アプリデザイン配布QRの形式が違います" }
        val parts = value.removePrefix(PREFIX).split('.')
        require(parts.size == 2) { "配布QRの形式が違います" }
        fun decode(part: String): ByteArray {
            require(part.matches(Regex("[A-Za-z0-9_-]+"))) { "配布QRの形式が違います" }
            val decoded = Base64.getUrlDecoder().decode(part)
            require(Base64.getUrlEncoder().withoutPadding().encodeToString(decoded) == part) { "配布QRの形式が違います" }
            return decoded
        }
        val payload = decode(parts[0])
        val signature = decode(parts[1])
        val key = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(
            BigInteger(1, Base64.getDecoder().decode(modulus.trim())), BigInteger.valueOf(65537)))
        require(signature.size == 384 && Signature.getInstance("SHA256withRSA").run {
            initVerify(key); update(payload); verify(signature)
        }) { "署名が無効です。正規のデザイン配布QRではありません" }
        require(payload.all { it.toInt() in 32..126 }) { "配布QRの内容が不正です" }
        val fields = payload.toString(Charsets.US_ASCII).split('|')
        require(fields.size == 8 && fields[0] == "1" && fields[1] == "jp.essential.app" && fields[2] == "app-design") { "Essentialのデザイン配布QRではありません" }
        require(fields[3].matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))) { "配布IDが不正です" }
        require(fields[4] in designIds) { "このアプリでは対応していないデザインです" }
        require(fields.drop(5).all { it.matches(Regex("[1-9][0-9]{0,11}")) }) { "有効期間が不正です" }
        val issued = fields[5].toLong()
        val start = fields[6].toLong()
        val end = fields[7].toLong()
        require(start < end && end - start <= 366L * 86400 && issued < end) { "利用期間は366日以内で指定してください" }
        require(now >= issued - 300 && now < end) { "期限切れ、または端末の日時が正しくありません" }
        return Grant(fields[3], fields[4], issued, start, end)
    }
}
