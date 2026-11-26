package com.doublemoon1119.mahjongcraft.logic.table.opening

/**
 * 不擲骰的固定開局程序：莊家沿用賽程決定的莊家（第一局為暫定擲骰者），開門位置固定為 [wallOpening]。
 *
 * 用於不需要擲骰決定開門的玩法，例如不砌牌牆時，開門位置只用來切分摸牌序列與保留牌，不對應桌面位置。
 *
 * @property wallOpening 固定的開門位置。
 */
class FixedOpeningProcedure(
    private val wallOpening: WallOpening,
) : RoundOpeningProcedure {
    override val supportedDiceCounts: Set<Int> = emptySet()

    override fun next(context: RoundOpeningContext): RoundOpeningStep = RoundOpeningStep.Complete(
        result = RoundOpeningCompletion(
            dealerPlayerId = context.fixedDealerPlayerId ?: context.provisionalPlayerId,
            wallOpening = wallOpening,
        ),
    )
}
