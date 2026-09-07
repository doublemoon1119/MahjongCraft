package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundOutcomePresentationClassification
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementTranslationKeys
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [RiichiWinSettlementDetailResolver] 的翻符顯示政策與特殊 outcome 判別測試。 */
class RiichiWinSettlementDetailResolverTest {
    private val config = RiichiRuleConfig()

    /** 未達滿貫且具有權威符數時應同時顯示翻數與符數。 */
    @Test
    fun includesFuWhenAvailable() {
        val value = RiichiWinSettlementDetailResolver.riichiHanFuValue(totalHan = 3, totalFu = 30)

        assertEquals(WinSettlementTranslationKeys.HAN_FU, value.translationKey)
        assertEquals(listOf("3", "30"), value.arguments)
    }

    /** 滿貫以上的符數為零時不得顯示不存在的符數。 */
    @Test
    fun omitsFuWhenUnavailable() {
        val value = RiichiWinSettlementDetailResolver.riichiHanFuValue(totalHan = 5, totalFu = 0)

        assertEquals(WinSettlementTranslationKeys.HAN, value.translationKey)
        assertEquals(listOf("5"), value.arguments)
    }

    /** 流局滿貫的 outcome id 應解析出日麻樣板鍵與流局滿貫役種欄位。 */
    @Test
    fun `resolves nagashi mangan special outcome`() {
        val winner = FakeMahjongPlayerFactory.create()
        val state = FakeTableStateFactory.create(players = listOf(winner), config = config)
        val outcome = ResolvedRoundOutcome(
            id = BuiltInRoundOutcomeIds.NAGASHI_MANGAN,
            settledTableState = state,
            beneficiaryPlayerIds = setOf(winner.id),
            scoreDeltas = mapOf(winner.id to 0),
            transitionDirective = RoundTransitionDirective.ADVANCE_DEALER,
            presentationClassification = RoundOutcomePresentationClassification.WIN_EQUIVALENT,
        )

        val resolved = RiichiWinSettlementDetailResolver.resolveSpecialOutcome(state, outcome)

        assertEquals(RiichiWinSettlementDetailResolver.TEMPLATE_KEY, resolved?.templateKey)
        assertEquals(RiichiWinSettlementDetailResolver.YAKU_FIELD, resolved?.fields?.single()?.id)
    }

    /** 不認得的 outcome id（非日麻自訂的特殊結果）不得誤判成流局滿貫。 */
    @Test
    fun `returns null for unrecognized special outcome`() {
        val winner = FakeMahjongPlayerFactory.create()
        val state = FakeTableStateFactory.create(players = listOf(winner), config = config)
        val outcome = ResolvedRoundOutcome(
            id = "mahjongcraft:test_outcome",
            settledTableState = state,
            beneficiaryPlayerIds = setOf(winner.id),
            scoreDeltas = mapOf(winner.id to 0),
            transitionDirective = RoundTransitionDirective.ADVANCE_DEALER,
            presentationClassification = RoundOutcomePresentationClassification.WIN_EQUIVALENT,
        )

        assertNull(RiichiWinSettlementDetailResolver.resolveSpecialOutcome(state, outcome))
    }
}
