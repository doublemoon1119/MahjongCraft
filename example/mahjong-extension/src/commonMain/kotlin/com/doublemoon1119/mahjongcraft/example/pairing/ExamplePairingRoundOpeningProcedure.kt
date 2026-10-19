package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import com.doublemoon1119.mahjongcraft.logic.table.opening.OpeningDiceRollRequest
import com.doublemoon1119.mahjongcraft.logic.table.opening.RoundOpeningCompletion
import com.doublemoon1119.mahjongcraft.logic.table.opening.RoundOpeningContext
import com.doublemoon1119.mahjongcraft.logic.table.opening.RoundOpeningProcedure
import com.doublemoon1119.mahjongcraft.logic.table.opening.RoundOpeningStep
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import kotlin.uuid.Uuid

/** 簡易配對麻將使用兩顆骰子的首局兩輪、後續單輪開門程序。 */
class ExamplePairingRoundOpeningProcedure : RoundOpeningProcedure {
    override val supportedDiceCounts: Set<Int> = setOf(2)

    override fun next(context: RoundOpeningContext): RoundOpeningStep = if (context.firstRound) firstRound(context) else laterRound(context)

    /**
     * 先選臨時莊家，再以第二輪選正式莊家並決定開門。
     *
     * @param context 固定座位與已完成的擲骰記錄。
     * @return 下一輪擲骰要求或完整開局結果。
     */
    private fun firstRound(context: RoundOpeningContext): RoundOpeningStep = when (context.rolls.size) {
        0 -> roll(context.provisionalPlayerId)
        1 -> roll(provisionalDealer(context))
        2 -> complete(context, formalDealer(context), context.rolls[1].dice)
        else -> error("Pairing first-round opening received too many rolls")
    }

    /**
     * 讓既定莊家只擲一輪開門，不重新選定起家。
     *
     * @param context 本局固定莊家與擲骰記錄。
     * @return 擲骰要求或開門結果。
     */
    private fun laterRound(context: RoundOpeningContext): RoundOpeningStep = when (context.rolls.size) {
        0 -> roll(requireNotNull(context.fixedDealerPlayerId))
        1 -> complete(context, requireNotNull(context.fixedDealerPlayerId), context.rolls.single().dice)
        else -> error("Pairing later-round opening received too many rolls")
    }

    /**
     * 建立兩顆骰子的要求；骰子結果由共用執行器產生。
     *
     * @param playerId 實際擲骰者。
     * @return 下一輪要求。
     */
    private fun roll(playerId: Uuid): RoundOpeningStep.Roll = RoundOpeningStep.Roll(
        OpeningDiceRollRequest(playerId = playerId, diceCount = 2),
    )

    /**
     * 從第一輪擲骰者起算，依骰子總和選臨時莊家。
     *
     * @param context 已含第一輪結果的開局情境。
     * @return 臨時莊家。
     */
    private fun provisionalDealer(context: RoundOpeningContext): Uuid {
        val firstTotal = context.rolls.first().dice.total
        val index = context.playerIdsInTurnOrder.indexOf(context.provisionalPlayerId)
        return context.playerIdsInTurnOrder[(index + firstTotal - 1) % context.playerIdsInTurnOrder.size]
    }

    /**
     * 從臨時莊家起算，以第二輪總和選正式莊家。
     *
     * @param context 已含兩輪結果的開局情境。
     * @return 本場起家。
     */
    private fun formalDealer(context: RoundOpeningContext): Uuid {
        val provisionalIndex = context.playerIdsInTurnOrder.indexOf(provisionalDealer(context))
        val secondTotal = context.rolls[1].dice.total
        return context.playerIdsInTurnOrder[(provisionalIndex + secondTotal - 1) % context.playerIdsInTurnOrder.size]
    }

    /**
     * 以最後一輪骰子建立小牌山的開門位置。
     *
     * 骰子總和循環對應邏輯牆面與每面墩數，不改變規則指定的實際側面配置。
     *
     * @param context 本局玩家名單。
     * @param dealerId 已選定的莊家。
     * @param dice 決定開門的最後一輪骰子。
     * @return 開局完成結果。
     */
    private fun complete(
        context: RoundOpeningContext,
        dealerId: Uuid,
        dice: DiceRollResult,
    ): RoundOpeningStep.Complete {
        val sideCount = if (context.playerIdsInTurnOrder.size == 2) 2 else 4
        val stacksPerSide = if (context.playerIdsInTurnOrder.size == 2) 4 else context.playerIdsInTurnOrder.size
        return RoundOpeningStep.Complete(
            RoundOpeningCompletion(
                dealerPlayerId = dealerId,
                wallOpening = WallOpening(
                    wallSideOffsetFromDealer = (dice.total - 1) % sideCount,
                    stacksFromRight = (dice.total - 1) % stacksPerSide + 1,
                ),
            ),
        )
    }
}
