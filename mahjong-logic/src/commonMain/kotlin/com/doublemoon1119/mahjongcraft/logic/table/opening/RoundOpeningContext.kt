package com.doublemoon1119.mahjongcraft.logic.table.opening

import kotlin.uuid.Uuid

/**
 * 開局程序的一次不可變輸入。
 *
 * @property playerIdsInTurnOrder 固定的玩家回合順序。
 * @property firstRound 是否為整場對局的第一局。
 * @property provisionalPlayerId 第一輪開始時暫定用來擲骰的玩家。
 * @property fixedDealerPlayerId 換局時由賽程決定且不可被程序改變的莊家；第一局為 null。
 * @property rolls 已完成的擲骰記錄，依輪次排列。
 */
data class RoundOpeningContext(
    val playerIdsInTurnOrder: List<Uuid>,
    val firstRound: Boolean,
    val provisionalPlayerId: Uuid,
    val fixedDealerPlayerId: Uuid?,
    val rolls: List<OpeningDiceRoll> = emptyList(),
) {
    init {
        require(playerIdsInTurnOrder.isNotEmpty()) { "Opening procedure requires at least one player" }
        require(playerIdsInTurnOrder.distinct().size == playerIdsInTurnOrder.size) {
            "Opening procedure player IDs must be unique"
        }
        require(provisionalPlayerId in playerIdsInTurnOrder) { "Provisional player must belong to the table" }
        require(firstRound == (fixedDealerPlayerId == null)) {
            "First-round context must not have a fixed dealer, and later rounds must have one"
        }
        fixedDealerPlayerId?.let { require(it in playerIdsInTurnOrder) { "Fixed dealer must belong to the table" } }
        require(rolls.map { it.rollIndex } == rolls.indices.toList()) {
            "Opening dice rolls must use contiguous zero-based indexes"
        }
    }
}
