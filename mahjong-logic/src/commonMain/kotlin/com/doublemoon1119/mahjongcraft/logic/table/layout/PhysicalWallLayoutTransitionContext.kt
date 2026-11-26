package com.doublemoon1119.mahjongcraft.logic.table.layout

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 動作完成後解析實體牌牆演進所需的資料。
 *
 * @property tableStateBeforeAction 動作套用前的權威桌況。
 * @property tableStateAfterAction 動作及其規則牌牆變化套用後的候選桌況。
 * @property currentLayout 動作前的權威實體牌牆布局。
 * @property actorPlayerId 執行動作的玩家 Uuid。
 * @property action 實際完成的遊戲動作。
 */
data class PhysicalWallLayoutTransitionContext(
    val tableStateBeforeAction: TableState,
    val tableStateAfterAction: TableState,
    val currentLayout: TileWallPhysicalLayout,
    val actorPlayerId: Uuid,
    val action: GameAction,
)
