package com.doublemoon1119.mahjongcraft.logic.rules

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionContext
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/**
 * 驗證內建規則模組透過規則 hook 決定本局結束後的莊家推進方式。
 */
class RoundTransitionRuleHookTest {
    /** 莊家和牌時應連莊，只有其他玩家和牌時應過莊。 */
    @Test
    fun `riichi win transition follows beneficiary`() {
        val players = listOf(
            FakeMahjongPlayerFactory.create(),
            FakeMahjongPlayerFactory.create(),
        )
        val state = FakeTableStateFactory.create(players = players, dealerPlayerId = players.first().id)
        val module = RiichiRuleModule("mahjongcraft:riichi", RiichiRuleConfig())

        assertEquals(
            RoundTransitionDirective.REPEAT_DEALER,
            module.resolveRoundTransition(context(state, RoundCompletionClassification.WIN, setOf(players.first().id))),
        )
        assertEquals(
            RoundTransitionDirective.ADVANCE_DEALER,
            module.resolveRoundTransition(context(state, RoundCompletionClassification.WIN, setOf(players.last().id))),
        )
    }

    /** 日麻一般流局由莊家聽牌時應連莊，沒有聽牌時應過莊。 */
    @Test
    fun `riichi exhaustive draw transition follows beneficiary`() {
        val players = listOf(
            FakeMahjongPlayerFactory.create(),
            FakeMahjongPlayerFactory.create(),
        )
        val state = FakeTableStateFactory.create(players = players, dealerPlayerId = players.first().id)
        val module = RiichiRuleModule("mahjongcraft:riichi", RiichiRuleConfig())

        assertEquals(
            RoundTransitionDirective.REPEAT_DEALER,
            module.resolveRoundTransition(
                context(
                    state,
                    RoundCompletionClassification.EXHAUSTIVE_DRAW,
                    setOf(players.first().id),
                    RiichiExhaustiveDrawReason.Normal,
                ),
            ),
        )
        assertEquals(
            RoundTransitionDirective.ADVANCE_DEALER,
            module.resolveRoundTransition(
                context(
                    state,
                    RoundCompletionClassification.EXHAUSTIVE_DRAW,
                    setOf(players.last().id),
                    RiichiExhaustiveDrawReason.Normal,
                ),
            ),
        )
    }

    /** 台麻途中流局沿用既有的莊家連莊行為。 */
    @Test
    fun `taiwan abortive draw repeats dealer`() {
        val players = listOf(
            FakeMahjongPlayerFactory.create(),
            FakeMahjongPlayerFactory.create(),
        )
        val state = FakeTableStateFactory.create(players = players, dealerPlayerId = players.first().id)
        val module = TaiwanRuleModule("mahjongcraft:taiwan", TaiwanRuleConfig())

        assertEquals(
            RoundTransitionDirective.REPEAT_DEALER,
            module.resolveRoundTransition(
                context(
                    state,
                    RoundCompletionClassification.ABORTIVE_DRAW,
                    emptySet(),
                    RiichiExhaustiveDrawReason.Normal,
                ),
            ),
        )
    }

    /** 第三方規則可以固定採用與內建規則不同的莊家推進決策。 */
    @Test
    fun `custom rule may override transition for win and abortive draw`() {
        val players = listOf(
            FakeMahjongPlayerFactory.create(),
            FakeMahjongPlayerFactory.create(),
        )
        val state = FakeTableStateFactory.create(players = players, dealerPlayerId = players.first().id)
        val module = CustomTransitionRuleModule(
            delegate = RiichiRuleModule("mahjongcraft:riichi", RiichiRuleConfig()),
        )

        assertEquals(
            RoundTransitionDirective.REPEAT_DEALER,
            module.resolveRoundTransition(context(state, RoundCompletionClassification.WIN, setOf(players.last().id))),
        )
        assertEquals(
            RoundTransitionDirective.ADVANCE_DEALER,
            module.resolveRoundTransition(
                context(
                    state,
                    RoundCompletionClassification.ABORTIVE_DRAW,
                    emptySet(),
                    RiichiExhaustiveDrawReason.SuuchaRiichi,
                ),
            ),
        )
    }

    /** 建立指定結果分類的規則推進測試情境。 */
    private fun context(
        state: TableState,
        classification: RoundCompletionClassification,
        beneficiaries: Set<Uuid>,
        drawReason: ExhaustiveDrawReason? = null,
    ): RoundTransitionContext = RoundTransitionContext(
        tableState = state,
        outcomeId = "mahjongcraft:test_outcome",
        classification = classification,
        beneficiaryPlayerIds = beneficiaries,
        responsiblePlayerIds = emptySet(),
        exhaustiveDrawReason = drawReason,
    )

    /** 只覆寫莊家推進 hook 的第三方規則測試替身。 */
    private class CustomTransitionRuleModule(
        private val delegate: RiichiRuleModule,
    ) : MahjongRuleModule<RiichiRuleConfig> by delegate {
        override val id: String = "example:custom-transition"

        override fun resolveRoundTransition(context: RoundTransitionContext): RoundTransitionDirective = when {
            context.classification == RoundCompletionClassification.ABORTIVE_DRAW -> RoundTransitionDirective.ADVANCE_DEALER
            else -> RoundTransitionDirective.REPEAT_DEALER
        }
    }
}
