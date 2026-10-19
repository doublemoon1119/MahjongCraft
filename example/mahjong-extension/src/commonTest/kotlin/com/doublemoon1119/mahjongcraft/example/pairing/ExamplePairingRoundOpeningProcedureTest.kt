package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import com.doublemoon1119.mahjongcraft.logic.table.opening.OpeningDiceRoll
import com.doublemoon1119.mahjongcraft.logic.table.opening.RoundOpeningContext
import com.doublemoon1119.mahjongcraft.logic.table.opening.RoundOpeningStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

/** 覆蓋首局兩輪擲骰與後續單輪開門流程。 */
class ExamplePairingRoundOpeningProcedureTest {
    /** 首局應先選出臨時莊家，再由第二輪擲骰選出正式莊家。 */
    @Test
    fun `first round selects provisional dealer before final dealer`() {
        val players = List(4) { Uuid.random() }
        val procedure = ExamplePairingRoundOpeningProcedure()
        val initial = RoundOpeningContext(players, true, players[0], null)
        val first = assertIs<RoundOpeningStep.Roll>(procedure.next(initial))
        assertEquals(players[0], first.request.playerId)

        val afterFirst = initial.copy(
            rolls = listOf(OpeningDiceRoll(0, players[0], DiceRollResult.of(listOf(2, 2)))),
        )
        val second = assertIs<RoundOpeningStep.Roll>(procedure.next(afterFirst))
        assertEquals(players[3], second.request.playerId)

        val complete = assertIs<RoundOpeningStep.Complete>(
            procedure.next(
                afterFirst.copy(
                    rolls = afterFirst.rolls + OpeningDiceRoll(1, players[3], DiceRollResult.of(listOf(3, 3))),
                ),
            ),
        )
        assertEquals(players[0], complete.result.dealerPlayerId)
        assertEquals(1, complete.result.wallOpening?.wallSideOffsetFromDealer)
    }

    /** 後續局只由既定莊家擲骰決定開門位置。 */
    @Test
    fun `later round keeps fixed dealer and rolls once`() {
        val players = List(3) { Uuid.random() }
        val procedure = ExamplePairingRoundOpeningProcedure()
        val context = RoundOpeningContext(players, false, players[0], players[1])
        val roll = assertIs<RoundOpeningStep.Roll>(procedure.next(context))
        assertEquals(players[1], roll.request.playerId)
        val complete = assertIs<RoundOpeningStep.Complete>(
            procedure.next(
                context.copy(
                    rolls = listOf(OpeningDiceRoll(0, players[1], DiceRollResult.of(listOf(1, 4)))),
                ),
            ),
        )
        assertEquals(players[1], complete.result.dealerPlayerId)
    }
}
