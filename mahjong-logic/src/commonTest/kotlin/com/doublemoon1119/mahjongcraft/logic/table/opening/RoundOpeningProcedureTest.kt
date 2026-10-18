package com.doublemoon1119.mahjongcraft.logic.table.opening

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.uuid.Uuid

/** 驗證開局程序的單輪預設與不可變輸入契約。 */
class RoundOpeningProcedureTest {
    /** 用於建立程序輸入的固定玩家清單。 */
    private val players = List(4) { Uuid.random() }

    /** 依骰子總和計算牌牆開門位置的測試 policy。 */
    private val policy = object : WallOpeningPolicy {
        override val diceCount: Int = 2

        override fun resolve(diceRoll: DiceRollResult): WallOpening = WallOpening(
            wallSideOffsetFromDealer = (diceRoll.total - 1) % 4,
            stacksFromRight = diceRoll.total,
        )
    }

    /** 確認第一局先由暫定玩家擲骰，完成後仍由該玩家作莊。 */
    @Test
    fun `single roll requests provisional player then completes with same dealer`() {
        val procedure = SingleRollOpeningProcedure(policy)
        val context = RoundOpeningContext(players, true, players[0], null)

        val request = assertIs<RoundOpeningStep.Roll>(procedure.next(context)).request
        assertEquals(players[0], request.playerId)
        assertEquals(2, request.diceCount)

        val completed = assertIs<RoundOpeningStep.Complete>(
            procedure.next(
                context.copy(rolls = listOf(OpeningDiceRoll(0, players[0], DiceRollResult.of(listOf(3, 4))))),
            ),
        ).result
        assertEquals(players[0], completed.dealerPlayerId)
        assertEquals(7, completed.wallOpening?.stacksFromRight)
    }

    /** 確認後續局仍由賽程指定的莊家擲骰並保留莊家。 */
    @Test
    fun `single roll keeps progression dealer in later round`() {
        val procedure = SingleRollOpeningProcedure(policy)
        val context = RoundOpeningContext(players, false, players[0], players[2])

        val request = assertIs<RoundOpeningStep.Roll>(procedure.next(context)).request
        assertEquals(players[2], request.playerId)
        val completed = assertIs<RoundOpeningStep.Complete>(
            procedure.next(
                context.copy(rolls = listOf(OpeningDiceRoll(0, players[2], DiceRollResult.of(listOf(1, 1))))),
            ),
        ).result
        assertEquals(players[2], completed.dealerPlayerId)
    }

    /** 確認擲骰記錄的索引必須從零開始連續排列。 */
    @Test
    fun `context rejects non contiguous roll indexes`() {
        assertFailsWith<IllegalArgumentException> {
            RoundOpeningContext(
                players,
                true,
                players[0],
                null,
                listOf(OpeningDiceRoll(1, players[0], DiceRollResult.of(listOf(1, 1)))),
            )
        }
    }

    /** 確認規則程序可以依前幾輪結果要求不同玩家與骰子數量。 */
    @Test
    fun `procedure can branch to a third roll and choose a non first player`() {
        val procedure = object : RoundOpeningProcedure {
            override val supportedDiceCounts: Set<Int> = setOf(2, 3)

            override fun next(context: RoundOpeningContext): RoundOpeningStep = when (context.rolls.size) {
                0 -> RoundOpeningStep.Roll(OpeningDiceRollRequest(players[0], 2))
                1 -> RoundOpeningStep.Roll(OpeningDiceRollRequest(players[2], 3))
                2 -> RoundOpeningStep.Roll(OpeningDiceRollRequest(players[3], 2))
                else -> RoundOpeningStep.Complete(
                    RoundOpeningCompletion(players[2], WallOpening(1, 5)),
                )
            }
        }
        val context = RoundOpeningContext(players, true, players[0], null)
        val rolls = listOf(
            OpeningDiceRoll(0, players[0], DiceRollResult.of(listOf(1, 2))),
            OpeningDiceRoll(1, players[2], DiceRollResult.of(listOf(3, 4, 5))),
            OpeningDiceRoll(2, players[3], DiceRollResult.of(listOf(6, 1))),
        )

        assertEquals(players[0], assertIs<RoundOpeningStep.Roll>(procedure.next(context)).request.playerId)
        assertEquals(players[2], assertIs<RoundOpeningStep.Roll>(procedure.next(context.copy(rolls = rolls.take(1)))).request.playerId)
        assertEquals(players[3], assertIs<RoundOpeningStep.Roll>(procedure.next(context.copy(rolls = rolls.take(2)))).request.playerId)
        assertEquals(players[2], assertIs<RoundOpeningStep.Complete>(procedure.next(context.copy(rolls = rolls))).result.dealerPlayerId)
    }

    /** 確認零次擲骰程序可以直接選定固定莊家。 */
    @Test
    fun `empty supported dice counts allow a fixed opening without rolls`() {
        val procedure = object : RoundOpeningProcedure {
            override val supportedDiceCounts: Set<Int> = emptySet()

            override fun next(context: RoundOpeningContext): RoundOpeningStep = RoundOpeningStep.Complete(
                RoundOpeningCompletion(context.provisionalPlayerId, null),
            )
        }
        val context = RoundOpeningContext(players, true, players[1], null)
        val result = assertIs<RoundOpeningStep.Complete>(procedure.next(context)).result
        assertEquals(players[1], result.dealerPlayerId)
    }

    /** 確認建立骰子結果時會複製呼叫端清單。 */
    @Test
    fun `opening dice records are immutable copies of caller lists`() {
        val mutableValues = mutableListOf(2, 4)
        val dice = DiceRollResult.of(mutableValues)
        mutableValues[0] = 6
        assertEquals(listOf(2, 4), dice.values)
    }
}
