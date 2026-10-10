package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlin.uuid.Uuid

/**
 * 牌牆公開 policy 的完整輸入。
 *
 * @property checkpoint 目前抵達的規則中立流程節點。
 * @property tableState 目前的權威桌況。
 * @property actorPlayerId 觸發此節點的玩家；沒有單一玩家時為 null。
 * @property sourceAction 觸發此節點的已完成動作；沒有單一動作時為 null。
 */
data class WallRevealContext(
    val checkpoint: WallRevealCheckpoint,
    val tableState: TableState,
    val actorPlayerId: Uuid? = null,
    val sourceAction: GameAction? = null,
)
