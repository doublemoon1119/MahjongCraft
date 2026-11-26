package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlin.uuid.Uuid

/**
 * 補牌 policy 的完整輸入。
 *
 * @property tableStateBeforeAction 動作尚未套用前的權威桌況。
 * @property tableStateAfterAction 已套用動作本身、但尚未補牌的候選桌況。
 * @property actorPlayerId 執行動作的玩家 ID。
 * @property action 實際完成的遊戲動作。
 */
data class SupplementalDrawContext(
    val tableStateBeforeAction: TableState,
    val tableStateAfterAction: TableState,
    val actorPlayerId: Uuid,
    val action: GameAction,
)
