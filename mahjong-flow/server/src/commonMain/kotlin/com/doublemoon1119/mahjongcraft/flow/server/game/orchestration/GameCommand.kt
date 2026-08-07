package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlin.uuid.Uuid

/**
 * 玩家想執行的一次遊戲操作，供 [GameActionRouter] 分派到對應的 use case。
 *
 * 每個變體對應恰好一個玩家發起的 Game Use Case 及其所需參數。不直接重用
 * [GameAction]：[Riichi] 需要攜帶欲宣告的捨牌張（`GameAction.Riichi` 本身不帶欄位）、
 * [KyuushuKyuuhai] 不需要任何規則專屬的 payload、且 [RespondToDiscard]/[RespondToChankan]
 * 需要由呼叫端明確表達「這次回應是針對捨牌反應視窗還是搶槓反應視窗」，單靠 [GameAction] 本身
 * 無法區分這三件事。
 */
sealed interface GameCommand {
    data object Draw : GameCommand
    data class Discard(val tileId: Uuid) : GameCommand
    data class Riichi(val tileId: Uuid) : GameCommand
    data object Tsumo : GameCommand
    data class Kan(val type: GameAction.KanType, val tileId: Uuid) : GameCommand
    data class RespondToDiscard(val action: GameAction) : GameCommand
    data class RespondToChankan(val action: GameAction) : GameCommand
    data object KyuushuKyuuhai : GameCommand
}
