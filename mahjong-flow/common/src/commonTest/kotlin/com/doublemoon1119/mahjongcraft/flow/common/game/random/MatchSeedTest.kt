package com.doublemoon1119.mahjongcraft.flow.common.game.random

import com.doublemoon1119.mahjongcraft.logic.table.RoundRandomness
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** 驗證對局種子的重現性、各局各用途的獨立性與隱藏內容。 */
class MatchSeedTest {
    /** 驗證同一個種子與局序總是衍生出相同的座位、牌山與骰子。 */
    @Test
    fun `same seed and round reproduce every random source`() {
        val first = seed(1).roundRandomness(roundOrdinal = 3)
        val second = seed(1).roundRandomness(roundOrdinal = 3)
        assertEquals(sample(first), sample(second))
    }

    /** 驗證不同局序、不同用途與不同種子都得到不同的亂數。 */
    @Test
    fun `rounds purposes and seeds give independent sequences`() {
        val base = sample(seed(1).roundRandomness(roundOrdinal = 0))
        assertNotEquals(base, sample(seed(1).roundRandomness(roundOrdinal = 1)))
        assertNotEquals(base, sample(seed(2).roundRandomness(roundOrdinal = 0)))
        assertNotEquals(base.seats, base.wall)
        assertNotEquals(base.wall, base.dice.map(Int::toLong))
    }

    /** 驗證亂數串的第一個整數就是 HMAC 第一個區塊的前四個位元組，確認衍生方式沒有改變。 */
    @Test
    fun `stream reads hmac blocks in order`() {
        val key = ByteArray(MatchSeed.SIZE_BYTES) { it.toByte() }
        val label = "label".encodeToByteArray()
        val block = hmacSha256(key, label + ByteArray(Long.SIZE_BYTES))
        val expected = block.take(Int.SIZE_BYTES).fold(0) { value, byte -> (value shl Byte.SIZE_BITS) or (byte.toInt() and 0xFF) }
        assertEquals(expected, HmacStreamRandom(key, label).nextInt())
    }

    /** 驗證骰子點數都在一到六之間。 */
    @Test
    fun `dice stay within six faces`() {
        val dice = seed(5).roundRandomness(roundOrdinal = 0).dice.roll(diceCount = 600).values
        assertTrue(dice.all { it in 1..6 })
        assertEquals((1..6).toSet(), dice.toSet())
    }

    /** 驗證種子轉成文字時不顯示內容，避免隨對局物件寫進紀錄。 */
    @Test
    fun `text form hides the seed`() {
        val bytes = ByteArray(MatchSeed.SIZE_BYTES) { (it * 7 + 1).toByte() }
        val text = MatchSeed.fromByteArray(bytes).toString()
        assertFalse(bytes.toHexString() in text)
        assertFalse(bytes.toHexString(HexFormat.UpperCase) in text)
        assertEquals("MatchSeed(hidden)", text)
    }

    /** 驗證種子以內容比較，並且不受外部修改影響。 */
    @Test
    fun `seed compares by content and copies its bytes`() {
        val bytes = ByteArray(MatchSeed.SIZE_BYTES) { 1 }
        val seed = MatchSeed.fromByteArray(bytes)
        bytes[0] = 2
        assertEquals(MatchSeed.fromByteArray(ByteArray(MatchSeed.SIZE_BYTES) { 1 }), seed)
        seed.toByteArray()[0] = 3
        assertContentEquals(ByteArray(MatchSeed.SIZE_BYTES) { 1 }, seed.toByteArray())
        assertNotEquals(MatchSeed.generate(), MatchSeed.generate())
    }

    /** 驗證長度錯誤的種子與負數局序會被拒絕。 */
    @Test
    fun `invalid seed length and round ordinal are rejected`() {
        assertFailsWith<IllegalArgumentException> { MatchSeed.fromByteArray(ByteArray(MatchSeed.SIZE_BYTES - 1)) }
        assertFailsWith<IllegalArgumentException> { seed(1).roundRandomness(roundOrdinal = -1) }
    }

    /**
     * 建立內容由 [value] 決定的種子。
     *
     * @param value 填入每個位元組的值。
     * @return 測試用種子。
     */
    private fun seed(value: Int): MatchSeed = MatchSeed.fromByteArray(ByteArray(MatchSeed.SIZE_BYTES) { value.toByte() })

    /**
     * 從每個亂數來源各取一段數值，方便比較。
     *
     * @param randomness 要取樣的亂數來源。
     * @return 取樣結果。
     */
    private fun sample(randomness: RoundRandomness): Sample = Sample(
        seats = randomness.seatOrder.longs(),
        wall = randomness.wall.longs(),
        dice = randomness.dice.roll(diceCount = SAMPLE_SIZE).values,
    )

    /**
     * 連續讀取固定數量的長整數。
     *
     * @receiver 要讀取的亂數來源。
     * @return 讀到的數值。
     */
    private fun Random.longs(): List<Long> = List(SAMPLE_SIZE) { nextLong() }

    /**
     * 三種亂數來源的取樣結果。
     *
     * @property seats 座位亂數。
     * @property wall 牌山亂數。
     * @property dice 骰子點數。
     */
    private data class Sample(val seats: List<Long>, val wall: List<Long>, val dice: List<Int>)

    /** 取樣長度。 */
    private companion object {
        /** 每個來源讀取的數量。 */
        const val SAMPLE_SIZE = 16
    }
}
