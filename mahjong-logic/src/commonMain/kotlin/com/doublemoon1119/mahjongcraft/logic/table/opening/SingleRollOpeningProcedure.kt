package com.doublemoon1119.mahjongcraft.logic.table.opening

/**
 * 由本局莊家擲一次骰，再依指定公式決定開門位置。
 *
 * @property openingPolicy 將該次點數換算成開門位置的規則公式。
 */
class SingleRollOpeningProcedure(
    private val openingPolicy: WallOpeningPolicy,
) : RoundOpeningProcedure {
    override val supportedDiceCounts: Set<Int> = setOf(openingPolicy.diceCount)

    override fun next(context: RoundOpeningContext): RoundOpeningStep = when (context.rolls.size) {
        0 -> RoundOpeningStep.Roll(
            OpeningDiceRollRequest(
                playerId = context.fixedDealerPlayerId ?: context.provisionalPlayerId,
                diceCount = openingPolicy.diceCount,
            ),
        )

        1 -> RoundOpeningStep.Complete(
            RoundOpeningCompletion(
                dealerPlayerId = context.fixedDealerPlayerId ?: context.provisionalPlayerId,
                wallOpening = openingPolicy.resolve(context.rolls.single().dice),
            ),
        )

        else -> error("Single-roll opening procedure received too many rolls")
    }
}
