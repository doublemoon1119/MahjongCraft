package com.doublemoon1119.mahjongcraft.logic.config

import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 驗證 [MahjongRuleConfig.validate] 擴充函式對各項不變量的檢查。
 */
class MahjongRuleConfigTest {

    /**
     * 測試合法的配置不應拋出例外。
     */
    @Test
    fun `test validate does not throw for a valid config`() {
        FakeMahjongRuleConfig().validate()
    }

    /**
     * 測試當 minPlayers 小於 1 時應拋出例外。
     */
    @Test
    fun `test validate throws when minPlayers is less than 1`() {
        val config = FakeMahjongRuleConfig(minPlayers = 0, maxPlayers = 4)

        assertFailsWith<IllegalArgumentException> { config.validate() }
    }

    /**
     * 測試當 maxPlayers 小於 minPlayers 時應拋出例外。
     */
    @Test
    fun `test validate throws when maxPlayers is less than minPlayers`() {
        val config = FakeMahjongRuleConfig(minPlayers = 4, maxPlayers = 2)

        assertFailsWith<IllegalArgumentException> { config.validate() }
    }

    /**
     * 測試當 initialHandSize 不是正整數時應拋出例外。
     */
    @Test
    fun `test validate throws when initialHandSize is not positive`() {
        val config = FakeMahjongRuleConfig(initialHandSize = 0)

        assertFailsWith<IllegalArgumentException> { config.validate() }
    }

    /**
     * 測試當 deadTileCount 為負數時應拋出例外。
     */
    @Test
    fun `test validate throws when deadTileCount is negative`() {
        val config = FakeMahjongRuleConfig(deadTileCount = -1)

        assertFailsWith<IllegalArgumentException> { config.validate() }
    }

    /**
     * 測試當 minimumWinConstraint 為負數時應拋出例外。
     */
    @Test
    fun `test validate throws when minimumWinConstraint is negative`() {
        val config = FakeMahjongRuleConfig(minimumWinConstraint = -1)

        assertFailsWith<IllegalArgumentException> { config.validate() }
    }

    /** 測試日麻 13 張初始手牌，依「每批最多 4 張」的通行慣例切成 4、4、4、1。 */
    @Test
    fun `test dealBatchSizes splits thirteen tiles into four four four one`() {
        val config = FakeMahjongRuleConfig(initialHandSize = 13)

        assertEquals(listOf(4, 4, 4, 1), config.dealBatchSizes())
    }

    /** 測試台麻 16 張初始手牌恰好整除，切成四批 4 張。 */
    @Test
    fun `test dealBatchSizes splits sixteen tiles into four equal batches`() {
        val config = FakeMahjongRuleConfig(initialHandSize = 16)

        assertEquals(listOf(4, 4, 4, 4), config.dealBatchSizes())
    }

    /** 測試不足一批（小於 4 張）的初始手牌只回傳單一批次，不會多切出空批次。 */
    @Test
    fun `test dealBatchSizes returns a single batch when hand size is under four`() {
        val config = FakeMahjongRuleConfig(initialHandSize = 3)

        assertEquals(listOf(3), config.dealBatchSizes())
    }

    /** 測試任何初始手牌張數算出的批次總和都必須等於原始張數，不會多算或漏算。 */
    @Test
    fun `test dealBatchSizes total matches initial hand size`() {
        for (handSize in 1..20) {
            val config = FakeMahjongRuleConfig(initialHandSize = handSize)

            assertEquals(handSize, config.dealBatchSizes().sum(), "Batch sizes for hand size $handSize should sum back to itself.")
        }
    }
}
