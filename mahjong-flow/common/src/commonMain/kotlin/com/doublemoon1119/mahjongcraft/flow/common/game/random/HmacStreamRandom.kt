package com.doublemoon1119.mahjongcraft.flow.common.game.random

import kotlin.random.Random

/**
 * 以 HMAC-SHA256 依序產生位元組的亂數來源。
 *
 * 第 n 個區塊是 `HMAC(key, label ‖ n)`，其中 n 為 8 位元組大端序計數；不知道 [key] 時，看到任何輸出都無法推回
 * [key]，也無法預測其他輸出。同一組 [key] 與 [label] 總是產生同一串位元組。只供單一流程依序使用，不可跨執行緒共用。
 *
 * @property key 不公開的金鑰。
 * @property label 區分不同亂數串的穩定標籤。
 */
internal class HmacStreamRandom(
    private val key: ByteArray,
    private val label: ByteArray,
) : Random() {
    /** 下一個要計算的區塊編號。 */
    private var blockIndex = 0L

    /** 目前區塊的內容。 */
    private var block = ByteArray(0)

    /** 目前區塊中下一個要讀取的位置。 */
    private var position = 0

    override fun nextBits(bitCount: Int): Int {
        var value = 0
        repeat(Int.SIZE_BYTES) { value = (value shl Byte.SIZE_BITS) or nextByte() }
        return if (bitCount == 0) 0 else value ushr (Int.SIZE_BITS - bitCount)
    }

    /**
     * 讀取下一個位元組，目前區塊用完時計算下一個區塊。
     *
     * @return 0 到 255 的位元組值。
     */
    private fun nextByte(): Int {
        if (position == block.size) {
            block = hmacSha256(key, label + blockIndex.toBigEndianBytes())
            blockIndex++
            position = 0
        }
        return block[position++].toInt() and BYTE_MASK
    }

    /** 位元組轉換常數。 */
    private companion object {
        /** 取出一個位元組無號值的遮罩。 */
        const val BYTE_MASK = 0xFF
    }
}

/**
 * 轉成 8 位元組大端序。
 *
 * @receiver 要轉換的數值。
 * @return 由高位到低位排列的位元組。
 */
private fun Long.toBigEndianBytes(): ByteArray = ByteArray(Long.SIZE_BYTES) { index ->
    (this ushr ((Long.SIZE_BYTES - 1 - index) * Byte.SIZE_BITS)).toByte()
}
