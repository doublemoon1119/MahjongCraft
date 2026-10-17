package com.doublemoon1119.mahjongcraft.flow.common.game.history

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.module.RevealedHandSettlement
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.PendingRobbingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import kotlin.uuid.Uuid

/**
 * 一筆權威交易造成的可重建桌況差異。
 *
 * @property changedPlayers 實際變動的玩家與新追加動作，未列出的玩家保持原值。
 * @property wall 活牌牆兩端的移除；未改變時為 null。
 * @property currentPlayerIndex 新的行動玩家索引；未改變時為 null。
 * @property dynamicRuleState 規則動態狀態的新值；外層 null 代表未改變。
 * @property pendingReaction 捨牌反應視窗的新值；外層 null 代表未改變。
 * @property pendingRobbingReaction 搶和反應視窗的新值；外層 null 代表未改變。
 * @property reservedWallTiles 規則保留牌的新順序；未改變時為 null。
 * @property physicalWallLayout 實體牌牆位置的局部變化；未改變時為 null。
 * @property finishedPlayerIds 本局已完成玩家的新集合；未改變時為 null。
 * @property revealedHandTileIds 本局已公開手牌的新集合；未改變時為 null。
 * @property revealedHandSettlements 流局公開手牌與等待牌的新資料；未改變時為 null，空集合代表清空。
 */
data class HistoryTableChange(
    val changedPlayers: List<HistoryPlayerChange> = emptyList(),
    val wall: HistoryWallChange? = null,
    val currentPlayerIndex: Int? = null,
    val dynamicRuleState: HistoryChangedValue<DynamicRuleState?>? = null,
    val pendingReaction: HistoryChangedValue<PendingReaction?>? = null,
    val pendingRobbingReaction: HistoryChangedValue<PendingRobbingReaction?>? = null,
    val reservedWallTiles: List<IdentifiedTile>? = null,
    val physicalWallLayout: HistoryWallLayoutChange? = null,
    val finishedPlayerIds: Set<Uuid>? = null,
    val revealedHandTileIds: Set<Uuid>? = null,
    val revealedHandSettlements: List<RevealedHandSettlement>? = null,
) {
    /**
     * 將差異套用到前一個完整桌況。
     *
     * @param before 產生此差異時所依據的完整桌況。
     * @return 套用玩家、牌牆與流程欄位差異後的新桌況。
     */
    fun applyTo(before: TableState): TableState {
        val replacements = changedPlayers.associateBy { it.playerWithoutHistory.id }
        require(replacements.size == changedPlayers.size && replacements.keys.all { id -> before.players.any { it.id == id } }) {
            "History change references duplicate or unknown player"
        }
        return before.copy(
            players = before.players.map { player -> replacements[player.id]?.applyTo(player) ?: player },
            tileWall = wall?.applyTo(before.tileWall) ?: before.tileWall,
            currentPlayerIndex = currentPlayerIndex ?: before.currentPlayerIndex,
            dynamicRuleState = if (dynamicRuleState != null) dynamicRuleState.value else before.dynamicRuleState,
            pendingReaction = if (pendingReaction != null) pendingReaction.value else before.pendingReaction,
            pendingRobbingReaction = if (pendingRobbingReaction != null) pendingRobbingReaction.value else before.pendingRobbingReaction,
            reservedWallTiles = reservedWallTiles ?: before.reservedWallTiles,
            physicalWallLayout = physicalWallLayout?.applyTo(checkNotNull(before.physicalWallLayout))
                ?: before.physicalWallLayout,
            finishedPlayerIds = finishedPlayerIds ?: before.finishedPlayerIds,
            revealedHandTileIds = revealedHandTileIds ?: before.revealedHandTileIds,
            revealedHandSettlements = revealedHandSettlements ?: before.revealedHandSettlements,
        )
    }

    /** 從前後桌況建立可重建差異的入口。 */
    companion object {
        /**
         * 建立差異，並確認套用後能還原交易後桌況；無法安全表示時回傳 null。
         *
         * @param before 交易前的完整桌況。
         * @param after 交易後的完整桌況。
         * @return 可重建 [after] 的差異；包含不支援的變更或驗證失敗時為 null。
         */
        fun between(before: TableState, after: TableState): HistoryTableChange? {
            if (
                before.id != after.id ||
                before.config != after.config ||
                before.dealerPlayerId != after.dealerPlayerId ||
                before.prevalentWind != after.prevalentWind ||
                before.roundNumber != after.roundNumber ||
                before.roundPosition != after.roundPosition ||
                before.comboCount != after.comboCount ||
                before.wallOpening != after.wallOpening
            ) {
                return null
            }
            if (before.players.map(MahjongPlayer::id) != after.players.map(MahjongPlayer::id)) return null
            if ((before.physicalWallLayout == null) != (after.physicalWallLayout == null)) return null
            val wallChange = wallChange(before.tileWall, after.tileWall) ?: return null
            val oldPlacements = before.physicalWallLayout?.placements.orEmpty()
            val newPlacements = after.physicalWallLayout?.placements.orEmpty()
            val changedPlayers = after.players.mapIndexedNotNull { index, player ->
                val previous = before.players[index]
                if (player == previous) return@mapIndexedNotNull null
                val previousActions = previous.actionHistory
                val nextActions = player.actionHistory
                val retained = when {
                    nextActions.take(previousActions.size) == previousActions -> previousActions.size
                    nextActions.isEmpty() -> 0
                    else -> return null
                }
                HistoryPlayerChange(player.copy(actionHistory = emptyList()), retained, nextActions.drop(retained))
            }
            val result = HistoryTableChange(
                changedPlayers = changedPlayers,
                wall = wallChange.takeUnless { it.removedFront == 0 && it.removedBack == 0 },
                currentPlayerIndex = after.currentPlayerIndex.takeIf { it != before.currentPlayerIndex },
                dynamicRuleState = if (after.dynamicRuleState != before.dynamicRuleState) HistoryChangedValue(after.dynamicRuleState) else null,
                pendingReaction = if (after.pendingReaction != before.pendingReaction) HistoryChangedValue(after.pendingReaction) else null,
                pendingRobbingReaction = if (after.pendingRobbingReaction != before.pendingRobbingReaction) HistoryChangedValue(after.pendingRobbingReaction) else null,
                reservedWallTiles = after.reservedWallTiles.takeIf { it != before.reservedWallTiles },
                physicalWallLayout = if (newPlacements != oldPlacements) {
                    HistoryWallLayoutChange(
                        removedTileIds = oldPlacements.keys - newPlacements.keys,
                        updatedPlacements = newPlacements.filter { (id, position) -> oldPlacements[id] != position },
                    )
                } else {
                    null
                },
                finishedPlayerIds = after.finishedPlayerIds.takeIf { it != before.finishedPlayerIds },
                revealedHandTileIds = after.revealedHandTileIds.takeIf { it != before.revealedHandTileIds },
                revealedHandSettlements = after.revealedHandSettlements.takeIf {
                    it != before.revealedHandSettlements
                },
            )
            return result.takeIf { runCatching { it.applyTo(before) == after }.getOrDefault(false) }
        }

        /**
         * 只接受原牌牆頭尾移除，避免把重排誤認為一般摸牌。
         *
         * @param before 交易前的活牌牆。
         * @param after 交易後的活牌牆。
         * @return 兩端移除的張數；新牌序不是原牌序的連續片段時為 null。
         */
        private fun wallChange(before: TileWall, after: TileWall): HistoryWallChange? {
            val old = before.getAllTiles()
            val new = after.getAllTiles()
            if (new.size > old.size) return null
            if (new.isEmpty()) return HistoryWallChange(old.size, 0)
            val first = old.indexOf(new.first())
            if (first < 0 || first + new.size > old.size || old.subList(first, first + new.size) != new) return null
            return HistoryWallChange(first, old.size - first - new.size)
        }
    }
}
