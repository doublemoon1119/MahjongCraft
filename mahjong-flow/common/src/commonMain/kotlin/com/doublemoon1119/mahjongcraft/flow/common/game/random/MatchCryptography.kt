package com.doublemoon1119.mahjongcraft.flow.common.game.random

/**
 * 產生密碼學安全的亂數位元組，用來建立無法被猜出的對局種子。
 *
 * @param size 需要的位元組數。
 * @return 新產生的亂數位元組。
 */
internal expect fun secureRandomBytes(size: Int): ByteArray

/**
 * 以 HMAC-SHA256 計算 [message] 在 [key] 下的值。
 *
 * @param key 不公開的金鑰。
 * @param message 要計算的訊息。
 * @return 32 位元組的結果；不知道 [key] 時無法從結果推回 [key]，也無法預測其他訊息的結果。
 */
internal expect fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray
