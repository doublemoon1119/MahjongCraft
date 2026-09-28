package com.doublemoon1119.mahjongcraft.flow.server.game.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryActionResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWallRevealable
import kotlin.uuid.Uuid

/**
 * 由明確接受的動作與交易前後桌況產生事件；不從 `actionHistory` 猜測動作。
 *
 * @param actorPlayerId 執行動作的玩家；規則自動動作可為 null。
 * @param action 已通過權威驗證的動作。
 * @param before 交易前的完整桌況，用來計算新增公開的牌。
 * @param after 交易提交後的完整桌況，用來計算新增公開資訊與索引。
 * @param affectedTileIds 動作直接涉及的牌 UUID；未指定時為空清單。
 * @return 尚未指派場次內序號與時間戳的事件草稿。
 */
fun acceptedActionHistoryDraft(
    actorPlayerId: Uuid?,
    action: GameAction,
    before: TableState,
    after: TableState,
    affectedTileIds: List<Uuid> = emptyList(),
): HistoryEventDraft {
    val beforeRevealed = (before.dynamicRuleState as? TileWallRevealable)?.getVisibleTileIds(before).orEmpty()
    val afterRevealed = (after.dynamicRuleState as? TileWallRevealable)?.getVisibleTileIds(after).orEmpty()
    return HistoryEventDraft(
        actorPlayerId = actorPlayerId,
        fact = HistoryFact.ActionAccepted(
            action = action,
            result = HistoryActionResult(
                affectedTileIds = affectedTileIds,
                newlyRevealedTileIds = (afterRevealed - beforeRevealed).sortedBy(Uuid::toString),
                remainingWallTileCount = after.tileWall.remainingCount,
                reservedWallTileIds = after.reservedWallTiles.map { it.id },
                scoresByPlayerId = after.players.associate { it.id to it.score },
                nextPlayerId = after.currentPlayer.id,
            ),
        ),
    )
}
