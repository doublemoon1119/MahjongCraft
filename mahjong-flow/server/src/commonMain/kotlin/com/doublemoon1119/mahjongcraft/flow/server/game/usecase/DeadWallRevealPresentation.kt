package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWallRevealable
import kotlin.uuid.Uuid

/**
 * 比較槓牌成立前後的規則可見牌集合，只回傳本次新增公開的王牌 ID。
 *
 * 呈現層只應替新增的指示牌播放翻牌動畫；已公開的牌不得因後續槓牌再次播放動畫。
 */
internal fun newlyRevealedDeadWallTileIds(previousState: TableState, currentState: TableState): Set<Uuid> {
    val currentRevealable = currentState.dynamicRuleState as? TileWallRevealable ?: return emptySet()
    val previousVisible = (previousState.dynamicRuleState as? TileWallRevealable)
        ?.getVisibleTileIds(previousState)
        .orEmpty()
    return currentRevealable.getVisibleTileIds(currentState) - previousVisible
}
