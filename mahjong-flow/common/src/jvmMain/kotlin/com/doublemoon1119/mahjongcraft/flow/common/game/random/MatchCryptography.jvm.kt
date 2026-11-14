package com.doublemoon1119.mahjongcraft.flow.common.game.random

import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** 共用的安全亂數來源；[SecureRandom] 可跨執行緒使用。 */
private val SECURE_RANDOM = SecureRandom()

/** JVM 內建的 HMAC-SHA256 演算法名稱。 */
private const val HMAC_SHA256 = "HmacSHA256"

internal actual fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also(SECURE_RANDOM::nextBytes)

internal actual fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray = Mac.getInstance(HMAC_SHA256).run {
    init(SecretKeySpec(key, HMAC_SHA256))
    doFinal(message)
}
