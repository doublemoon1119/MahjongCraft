package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import kotlin.uuid.Uuid

/**
 * 摸牌後自動處理 policy 的完整輸入。
 *
 * @property tableState 已套用這次摸牌的權威桌況；摸到的牌位於玩家的摸牌位。
 * @property playerId 摸牌的玩家。
 * @property drawnTile 這次摸到的牌。
 * @property source 這次摸牌的來源。
 * @property completedStepCount 同一串自動處理已完成的步數；第一次詢問為 0。
 */
data class DrawFollowUpContext(
    val tableState: TableState,
    val playerId: Uuid,
    val drawnTile: IdentifiedTile,
    val source: DrawSource,
    val completedStepCount: Int,
)
