package com.doublemoon1119.mahjongcraft.logic.config

import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import kotlin.test.Test
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
}
