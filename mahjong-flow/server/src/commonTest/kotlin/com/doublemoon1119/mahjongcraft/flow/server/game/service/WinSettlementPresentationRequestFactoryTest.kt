package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementTranslationKeys
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證胡牌結算中的日麻翻符顯示政策。 */
class WinSettlementPresentationRequestFactoryTest {
    /** 未達滿貫且具有權威符數時應同時顯示翻數與符數。 */
    @Test
    fun includesFuWhenAvailable() {
        val value = WinSettlementPresentationRequestFactory.riichiHanFuValue(totalHan = 3, totalFu = 30)

        assertEquals(WinSettlementTranslationKeys.HAN_FU, value.translationKey)
        assertEquals(listOf("3", "30"), value.arguments)
    }

    /** 滿貫以上的符數為零時不得顯示不存在的符數。 */
    @Test
    fun omitsFuWhenUnavailable() {
        val value = WinSettlementPresentationRequestFactory.riichiHanFuValue(totalHan = 5, totalFu = 0)

        assertEquals(WinSettlementTranslationKeys.HAN, value.translationKey)
        assertEquals(listOf("5"), value.arguments)
    }
}
