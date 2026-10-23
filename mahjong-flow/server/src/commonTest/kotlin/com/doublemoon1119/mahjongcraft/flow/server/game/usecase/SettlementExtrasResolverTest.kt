package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.SettlementExtrasAllocation
import com.doublemoon1119.mahjongcraft.logic.module.SettlementExtrasContext
import com.doublemoon1119.mahjongcraft.logic.module.SettlementExtrasOccasion
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 驗證供託與連莊點數依規則的分配結算，以及不合法分配會中止。 */
class SettlementExtrasResolverTest {
    /** 一場桌上有兩根立直棒、一本場的四人桌。 */
    private val state: TableState = FakeTableStateFactory.create(
        players = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).map { FakeMahjongPlayerFactory.create(it) },
        config = RiichiRuleConfig(),
        dynamicRuleState = RiichiDynamicState(riichiStickCount = 2),
        comboCount = 1,
    )

    /** 放銃者。 */
    private val discarderId = state.players[0].id

    /** 依頭跳順位排列的兩位榮和者。 */
    private val winnerIds = listOf(state.players[1].id, state.players[2].id)

    /** 預設由最近的贏家拿走全部供託並收取本場，另一位贏家不收。 */
    @Test
    fun `by default the nearest winner takes the pot and the combo bonus`() {
        val result = resolve(RiichiRuleModule("test:riichi", RiichiRuleConfig()))

        assertEquals(mapOf(winnerIds[0] to 2300, discarderId to -300), result.scoreDeltas)
        assertEquals(setOf(winnerIds[0]), result.stickPotRecipientIds)
        assertEquals(0, (result.dynamicRuleState as RiichiDynamicState).riichiStickCount)
    }

    /** 規則可以平分供託，並讓每位贏家都收本場。 */
    @Test
    fun `a rule can split the pot and pay the combo bonus to every winner`() {
        val module = Allocating { context ->
            SettlementExtrasAllocation(
                stickPotShares = context.candidatePlayerIds.associateWith { context.stickPotAmount / context.candidatePlayerIds.size },
                comboBonusRecipientIds = context.candidatePlayerIds.toSet(),
            )
        }

        val result = resolve(module)

        assertEquals(mapOf(winnerIds[0] to 1300, winnerIds[1] to 1300, discarderId to -600), result.scoreDeltas)
        assertEquals(winnerIds.toSet(), result.stickPotRecipientIds)
    }

    /** 收款者不是候選者、金額為負、總額不符或整場結束還收本場時都以規則錯誤中止。 */
    @Test
    fun `an invalid allocation stops the match`() {
        val outsider = state.players[3].id
        val invalidAllocations = listOf(
            SettlementExtrasAllocation(mapOf(outsider to 2000), emptySet()),
            SettlementExtrasAllocation(mapOf(winnerIds[0] to 3000, winnerIds[1] to -1000), emptySet()),
            SettlementExtrasAllocation(mapOf(winnerIds[0] to 1000), emptySet()),
            SettlementExtrasAllocation(emptyMap(), emptySet()),
            SettlementExtrasAllocation(mapOf(winnerIds[0] to 2000), setOf(outsider)),
        )

        invalidAllocations.forEach { allocation ->
            assertFailsWith<RuleContractViolationException> { resolve(Allocating { allocation }) }
        }
        assertFailsWith<RuleContractViolationException> {
            resolve(Allocating { SettlementExtrasAllocation(mapOf(winnerIds[0] to 2000), setOf(winnerIds[0])) }, SettlementExtrasOccasion.MATCH_END)
        }
    }

    /** 整場結束時規則可以不分剩下的供託，供託仍從桌上收走。 */
    @Test
    fun `leftover pot can be dropped at the end of the match`() {
        val result = resolve(Allocating { SettlementExtrasAllocation(emptyMap(), emptySet()) }, SettlementExtrasOccasion.MATCH_END)

        assertEquals(emptyMap(), result.scoreDeltas)
        assertEquals(emptySet(), result.stickPotRecipientIds)
        assertEquals(0, (result.dynamicRuleState as RiichiDynamicState).riichiStickCount)
    }

    /**
     * 以兩位贏家為候選者計算分配。
     *
     * @param module 規則模組。
     * @param occasion 分配的時機。
     * @return 結算結果。
     */
    private fun resolve(
        module: MahjongRuleModule<*>,
        occasion: SettlementExtrasOccasion = SettlementExtrasOccasion.WIN,
    ): SettlementExtrasResolver.Result = SettlementExtrasResolver.resolve(
        state = state,
        module = module,
        occasion = occasion,
        candidatePlayerIds = winnerIds,
        discarderId = discarderId,
    )

    /**
     * 只覆寫分配方式、其餘委派給日麻的測試規則。
     *
     * @property allocate 測試指定的分配。
     */
    private class Allocating(
        private val allocate: (SettlementExtrasContext) -> SettlementExtrasAllocation,
    ) : MahjongRuleModule<RiichiRuleConfig> by RiichiRuleModule("test:riichi", RiichiRuleConfig()) {
        override fun allocateSettlementExtras(context: SettlementExtrasContext): SettlementExtrasAllocation = allocate(context)
    }
}
