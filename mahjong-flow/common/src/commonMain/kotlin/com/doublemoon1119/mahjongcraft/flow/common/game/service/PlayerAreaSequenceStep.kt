package com.doublemoon1119.mahjongcraft.flow.common.game.service

import kotlin.uuid.Uuid

/**
 * 依序呈現的一步牌區變化：一位玩家把 [setAsideTileIds] 從手牌移到移出區，接著依序摸入 [drawnTileIds]。
 *
 * 兩者都可以是空清單，例如只摸牌，或移出後因等待選擇而暫停補牌。牌最後的位置以
 * [GamePresentationPublisher.publishPlayerAreaSequence] 的牌區為準：摸入的牌可能進入立牌或摸牌位。
 *
 * @property seatIndex 這位玩家在 `TableState.players` 的固定座位 index。
 * @property setAsideTileIds 這一步從手牌移出的牌，依移出順序排列。
 * @property drawnTileIds 這一步摸入的牌，依摸牌順序排列。
 */
data class PlayerAreaSequenceStep(
    val seatIndex: Int,
    val setAsideTileIds: List<Uuid>,
    val drawnTileIds: List<Uuid>,
) {
    init {
        require(setAsideTileIds.isNotEmpty() || drawnTileIds.isNotEmpty()) { "A player area step must move at least one tile" }
    }
}
