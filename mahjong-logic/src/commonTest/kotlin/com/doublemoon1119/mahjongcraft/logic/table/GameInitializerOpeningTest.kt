package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollSource
import com.doublemoon1119.mahjongcraft.logic.table.opening.OpeningDiceRollRequest
import com.doublemoon1119.mahjongcraft.logic.table.opening.RoundOpeningCompletion
import com.doublemoon1119.mahjongcraft.logic.table.opening.RoundOpeningContext
import com.doublemoon1119.mahjongcraft.logic.table.opening.RoundOpeningProcedure
import com.doublemoon1119.mahjongcraft.logic.table.opening.RoundOpeningStep
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/** 驗證多輪開門程序確實由 [GameInitializer] 權威執行與保存。 */
class GameInitializerOpeningTest {
    /** 確認程序選出的莊家會成為目前玩家，且固定座位索引以該莊家為零。 */
    @Test
    fun `initialize uses selected dealer without reordering fixed roster`() {
        val playerIds = List(4) { Uuid.random() }
        var selectedDealer: Uuid? = null
        val procedure = object : RoundOpeningProcedure {
            override val supportedDiceCounts: Set<Int> = setOf(2)

            override fun next(context: RoundOpeningContext): RoundOpeningStep = if (context.rolls.isEmpty()) {
                RoundOpeningStep.Roll(OpeningDiceRollRequest(context.playerIdsInTurnOrder.first(), 2))
            } else {
                val dealer = context.playerIdsInTurnOrder[2]
                selectedDealer = dealer
                RoundOpeningStep.Complete(
                    RoundOpeningCompletion(
                        dealerPlayerId = dealer,
                        wallOpening = WallOpening(wallSideOffsetFromDealer = 0, stacksFromRight = 1),
                    ),
                )
            }
        }

        val result = GameInitializer.initialize(
            id = Uuid.random(),
            playerIds = playerIds,
            module = module(procedure),
            diceRollSource = fixedDiceSource,
        )

        assertEquals(playerIds.toSet(), result.tableState.players.map { it.id }.toSet())
        assertEquals(selectedDealer, result.tableState.dealerPlayerId)
        assertEquals(selectedDealer, result.tableState.currentPlayer.id)
        assertEquals(2, result.tableState.openingDiceRolls.single().dice.values.size)
        assertEquals(
            0,
            result.tableState.players.single { it.id == selectedDealer }.initialSeatIndex,
        )
    }

    /** 確認程序要求不在牌桌上的玩家擲骰時會被拒絕。 */
    @Test
    fun `initialize rejects opening roll requested by an unknown player`() {
        val procedure = procedureReturning { RoundOpeningStep.Roll(OpeningDiceRollRequest(Uuid.random(), 2)) }

        assertFailsWith<IllegalArgumentException> {
            GameInitializer.initialize(
                id = Uuid.random(),
                playerIds = List(4) { Uuid.random() },
                module = module(procedure),
                diceRollSource = fixedDiceSource,
            )
        }
    }

    /** 確認程序要求未宣告的骰子數量時會被拒絕。 */
    @Test
    fun `initialize rejects opening roll count outside declared bounds`() {
        val procedure = procedureReturning(supportedDiceCounts = emptySet()) { context ->
            RoundOpeningStep.Roll(OpeningDiceRollRequest(context.playerIdsInTurnOrder.first(), 3))
        }

        assertFailsWith<IllegalArgumentException> {
            GameInitializer.initialize(
                id = Uuid.random(),
                playerIds = List(4) { Uuid.random() },
                module = module(procedure),
                diceRollSource = fixedDiceSource,
            )
        }
    }

    /** 確認程序超過最多擲骰輪數時會明確失敗。 */
    @Test
    fun `initialize rejects a procedure that exceeds the roll bound`() {
        val procedure = procedureReturning { context ->
            RoundOpeningStep.Roll(OpeningDiceRollRequest(context.playerIdsInTurnOrder.first(), 2))
        }

        assertFailsWith<IllegalArgumentException> {
            GameInitializer.initialize(
                id = Uuid.random(),
                playerIds = List(4) { Uuid.random() },
                module = module(procedure),
                diceRollSource = fixedDiceSource,
            )
        }
    }

    /** 確認零次擲骰的固定開局不會呼叫骰子來源。 */
    @Test
    fun `zero roll fixed opening does not call dice source`() {
        val playerIds = List(4) { Uuid.random() }
        val procedure = procedureReturning { context ->
            RoundOpeningStep.Complete(
                RoundOpeningCompletion(
                    dealerPlayerId = context.provisionalPlayerId,
                    wallOpening = WallOpening(wallSideOffsetFromDealer = 0, stacksFromRight = 1),
                ),
            )
        }

        val result = GameInitializer.initialize(
            id = Uuid.random(),
            playerIds = playerIds,
            module = module(procedure),
            diceRollSource = DiceRollSource { error("zero-roll opening must not call the dice source") },
        )

        assertEquals(emptyList(), result.tableState.openingDiceRolls)
    }

    /** 確認後續局的開局程序不能取代賽程已決定的莊家。 */
    @Test
    fun `later round cannot replace progression dealer`() {
        val players = listOf(0, 1, 2, 3).map { FakeMahjongPlayerFactory.create() }
        val dealerId = players.first().id
        val procedure = procedureReturning { context ->
            RoundOpeningStep.Complete(
                RoundOpeningCompletion(
                    dealerPlayerId = context.playerIdsInTurnOrder[1],
                    wallOpening = WallOpening(wallSideOffsetFromDealer = 0, stacksFromRight = 1),
                ),
            )
        }
        val advancement = RoundAdvancementResult(
            players = players,
            dealerPlayerId = dealerId,
            roundNumber = 2,
            comboCount = 0,
            prevalentWind = Wind.EAST,
        )

        assertFailsWith<IllegalArgumentException> {
            GameInitializer.startNextRound(
                gameId = Uuid.random(),
                roundAdvancement = advancement,
                previousDynamicRuleState = null,
                module = module(procedure),
                diceRollSource = fixedDiceSource,
            )
        }
    }

    /** 建立一個只替換開局程序的測試規則模組。 */
    private fun module(procedure: RoundOpeningProcedure): MahjongRuleModule<RiichiRuleConfig> {
        val delegate = RiichiRuleModule(id = "test:riichi", config = RiichiRuleConfig())
        return object : MahjongRuleModule<RiichiRuleConfig> by delegate {
            override fun createRoundOpeningProcedure(): RoundOpeningProcedure = procedure
        }
    }

    /**
     * 建立固定回傳指定下一步的開局程序測試替身。
     *
     * @param supportedDiceCounts 程序允許要求的骰子數量。
     * @param step 根據目前情境回傳下一步的函式。
     * @return 使用指定行為的開局程序。
     */
    private fun procedureReturning(
        supportedDiceCounts: Set<Int> = setOf(2),
        step: (RoundOpeningContext) -> RoundOpeningStep,
    ): RoundOpeningProcedure = object : RoundOpeningProcedure {
        override val supportedDiceCounts: Set<Int> = supportedDiceCounts
        override fun next(context: RoundOpeningContext): RoundOpeningStep = step(context)
    }

    private companion object {
        /** 回傳每顆骰子點數為一的固定骰子來源。 */
        val fixedDiceSource = DiceRollSource { diceCount ->
            DiceRollResult.of(List(diceCount) { 1 })
        }
    }
}
