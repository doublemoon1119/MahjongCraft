package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.module.PublicPlayerIndicator
import com.doublemoon1119.mahjongcraft.logic.module.PublicPlayerIndicatorValue
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證台灣麻將公開給所有人的座位標示。 */
class TaiwanPublicIndicatorTest {
    /** 標示由規則模組提供。 */
    private val module = TaiwanRuleModule(id = "mahjongcraft:taiwan", config = TaiwanRuleConfig())

    /** 連莊一次以上時只有莊家帶連莊次數；初莊時沒有。 */
    @Test
    fun `only a dealer with a streak shows the streak count`() {
        val (dealer, other) = List(2) { FakeMahjongPlayerFactory.create() }
        val streak = FakeTableStateFactory.create(players = listOf(dealer, other), dealerPlayerId = dealer.id, config = TaiwanRuleConfig(), comboCount = 2)
        val firstDealership = streak.copy(comboCount = 0)

        assertEquals(
            expected = listOf(PublicPlayerIndicator(TaiwanPublicIndicatorIds.DEALER_STREAK, PublicPlayerIndicatorValue.Count(2))),
            actual = module.getPublicPlayerIndicators(streak, dealer),
        )
        assertEquals(emptyList(), module.getPublicPlayerIndicators(streak, other))
        assertEquals(emptyList(), module.getPublicPlayerIndicators(firstDealership, dealer))
    }
}
