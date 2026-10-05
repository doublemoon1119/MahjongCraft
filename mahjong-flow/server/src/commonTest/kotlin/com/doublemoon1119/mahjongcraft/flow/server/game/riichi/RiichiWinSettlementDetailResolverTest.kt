package com.doublemoon1119.mahjongcraft.flow.server.game.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundOutcomePresentationClassification
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiWinSettlementIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPointResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [RiichiWinSettlementDetailResolver] 輸出的日麻語意詳情與特殊 outcome 判別測試。 */
class RiichiWinSettlementDetailResolverTest {
    private val config = RiichiRuleConfig()

    /** 立直及雙立直即使沒有裏寶牌加飜，也保留可用指示牌欄位；未立直及役滿不加入。
     */
    @Test
    fun `ura indicators require an applicable winning riichi result`() {
        val state = FakeTableStateFactory.create(config = config)
        listOf(YakuType.Riichi, YakuType.DoubleRiichi).forEach { yaku ->
            val result = RiichiHandValueResult(listOf(YakuResult.han(yaku, 1)), 1, 30, RiichiPointResult.Ron(1000))
            val fields = RiichiWinSettlementDetailResolver.riichiDetails(state, result)
            assertTrue(fields.any { it.id == RiichiWinSettlementIds.URA_DORA_FIELD })
        }
        listOf(
            RiichiHandValueResult(listOf(YakuResult.han(YakuType.Tanyao, 1)), 1, 30, RiichiPointResult.Ron(1000)),
            RiichiHandValueResult(listOf(YakuResult.han(YakuType.Riichi, 1)), -1, 0, RiichiPointResult.Ron(32000)),
        ).forEach { result ->
            val fields = RiichiWinSettlementDetailResolver.riichiDetails(state, result)
            assertTrue(fields.any { it.id == RiichiWinSettlementIds.DORA_FIELD })
            assertFalse(fields.any { it.id == RiichiWinSettlementIds.URA_DORA_FIELD })
        }
    }

    /** 一般和牌的役種條目依役種結果順序帶出役種 ID 與翻數，翻符欄位帶出總翻數與符數。 */
    @Test
    fun `regular win lists yaku with han and the han fu total`() {
        val state = FakeTableStateFactory.create(config = config)
        val result = RiichiHandValueResult(
            listOf(YakuResult.han(YakuType.Riichi, 1), YakuResult.han(YakuType.Tanyao, 1), YakuResult.han(YakuType.Honitsu, 3)),
            5,
            40,
            RiichiPointResult.Ron(8000),
        )

        val fields = RiichiWinSettlementDetailResolver.riichiDetails(state, result)

        assertEquals(
            WinSettlementDetailValue.Entries(
                listOf(
                    RiichiWinSettlementIds.yakuEntry(YakuType.Riichi, 1),
                    RiichiWinSettlementIds.yakuEntry(YakuType.Tanyao, 1),
                    RiichiWinSettlementIds.yakuEntry(YakuType.Honitsu, 3),
                ),
            ),
            fields.value(RiichiWinSettlementIds.YAKU_FIELD),
        )
        assertEquals(
            WinSettlementDetailValue.Quantities(
                listOf(WinSettlementQuantity(RiichiWinSettlementIds.HAN, 5), WinSettlementQuantity(RiichiWinSettlementIds.FU, 40)),
            ),
            fields.value(RiichiWinSettlementIds.HAN_FU_FIELD),
        )
        assertFalse(fields.any { it.id == RiichiWinSettlementIds.YAKUMAN_TOTAL_FIELD })
    }

    /** 役滿和牌的條目帶出各役滿倍數，合計欄位帶出總倍數，不輸出翻符欄位。 */
    @Test
    fun `yakuman win lists multipliers and the yakuman total`() {
        val state = FakeTableStateFactory.create(config = config)
        val result = RiichiHandValueResult(
            listOf(YakuResult.doubleYakuman(YakuType.KokushiMusou13), YakuResult.yakuman(YakuType.Tenhou)),
            -3,
            0,
            RiichiPointResult.Ron(96000),
        )

        val fields = RiichiWinSettlementDetailResolver.riichiDetails(state, result)

        assertEquals(
            WinSettlementDetailValue.Entries(
                listOf(
                    RiichiWinSettlementIds.yakumanEntry(YakuType.KokushiMusou13, 2),
                    RiichiWinSettlementIds.yakumanEntry(YakuType.Tenhou, 1),
                ),
            ),
            fields.value(RiichiWinSettlementIds.YAKU_FIELD),
        )
        assertEquals(
            WinSettlementDetailValue.Quantities(listOf(WinSettlementQuantity(RiichiWinSettlementIds.YAKUMAN, 3))),
            fields.value(RiichiWinSettlementIds.YAKUMAN_TOTAL_FIELD),
        )
        assertFalse(fields.any { it.id == RiichiWinSettlementIds.HAN_FU_FIELD })
    }

    /** 未達滿貫且具有權威符數時應同時提供翻數與符數。 */
    @Test
    fun includesFuWhenAvailable() {
        val value = RiichiWinSettlementDetailResolver.riichiHanFuValue(totalHan = 3, totalFu = 30)

        assertEquals(
            listOf(WinSettlementQuantity(RiichiWinSettlementIds.HAN, 3), WinSettlementQuantity(RiichiWinSettlementIds.FU, 30)),
            value.quantities,
        )
    }

    /** 滿貫以上的符數為零時不得提供不存在的符數。 */
    @Test
    fun omitsFuWhenUnavailable() {
        val value = RiichiWinSettlementDetailResolver.riichiHanFuValue(totalHan = 5, totalFu = 0)

        assertEquals(listOf(WinSettlementQuantity(RiichiWinSettlementIds.HAN, 5)), value.quantities)
    }

    /** 流局滿貫的 outcome id 應解析出只有流局滿貫條目的役種欄位。 */
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

        val fields = RiichiWinSettlementDetailResolver.resolveSpecialOutcome(state, outcome)

        assertEquals(
            listOf(
                WinSettlementDetailField(
                    RiichiWinSettlementIds.YAKU_FIELD,
                    WinSettlementDetailValue.Entries(listOf(WinSettlementDetailEntry(RiichiWinSettlementIds.NAGASHI_MANGAN))),
                ),
            ),
            fields,
        )
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

    /** 取得指定欄位的值。 */
    private fun List<WinSettlementDetailField>.value(id: String): WinSettlementDetailValue? = firstOrNull { it.id == id }?.value
}
