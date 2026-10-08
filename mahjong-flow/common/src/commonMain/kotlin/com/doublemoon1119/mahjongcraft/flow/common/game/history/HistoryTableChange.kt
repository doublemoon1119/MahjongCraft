package com.doublemoon1119.mahjongcraft.flow.common.game.history

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.PendingRobbingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import kotlin.uuid.Uuid

/**
 * 外層表示欄位已變更，與沒有變化的 null 外層區別。
 *
 * @property value 欄位的新值；欄位本身可空時，此值也可以為 null。
 */
data class HistoryChangedValue<T>(
    val value: T,
)

/**
 * 活牌牆僅從兩端移除牌張的變化。
 *
 * @property removedFront 從摸牌端移除的張數。
 * @property removedBack 從另一端移除的張數。
 */
data class HistoryWallChange(
    val removedFront: Int,
    val removedBack: Int,
) {
    init {
        require(removedFront >= 0 && removedBack >= 0)
    }

    /** 從前一個牌牆重建留下的牌序。 */
    fun applyTo(before: TileWall): TileWall {
        val tiles = before.getAllTiles()
        require(removedFront + removedBack <= tiles.size) { "History wall removal exceeds source wall" }
        return TileWall(tiles.subList(removedFront, tiles.size - removedBack))
    }
}

/**
 * 實體牌牆位置的局部變化。
 *
 * @property removedTileIds 離開牌牆的牌 UUID。
 * @property updatedPlacements 位置或方向改變的牌 UUID 與新位置。
 */
data class HistoryWallLayoutChange(
    val removedTileIds: Set<Uuid>,
    val updatedPlacements: Map<Uuid, TileWallPlacement>,
) {
    /** 將局部變化套用到前一個布局。 */
    fun applyTo(before: TileWallPhysicalLayout): TileWallPhysicalLayout = TileWallPhysicalLayout(
        before.placements.filterKeys { it !in removedTileIds } + updatedPlacements,
    )
}

/**
 * 玩家狀態與累積動作紀錄的局部變化。
 *
 * @property playerWithoutHistory 交易後玩家狀態；[MahjongPlayer.actionHistory] 固定為空，避免反覆保存累積紀錄。
 * @property retainedActionCount 從交易前動作紀錄保留的前綴張數；清空紀錄時為零。
 * @property appendedActions 本次交易新追加的動作。
 */
data class HistoryPlayerChange(
    val playerWithoutHistory: MahjongPlayer,
    val retainedActionCount: Int,
    val appendedActions: List<GameAction>,
) {
    init {
        require(playerWithoutHistory.actionHistory.isEmpty()) { "History player change must not contain cumulative actions" }
        require(retainedActionCount >= 0) { "Retained history action count must not be negative" }
    }

    /** 使用前一位玩家的動作紀錄重建交易後玩家狀態。 */
    fun applyTo(before: MahjongPlayer): MahjongPlayer {
        require(before.id == playerWithoutHistory.id) { "History player change references another player" }
        require(retainedActionCount <= before.actionHistory.size) { "History action prefix exceeds source history" }
        return playerWithoutHistory.copy(
            actionHistory = before.actionHistory.take(retainedActionCount) + appendedActions,
        )
    }
}

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
) {
    /** 將差異套用到前一個完整桌況。 */
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
            initialDeadWall = reservedWallTiles ?: before.reservedWallTiles,
            physicalWallLayout = physicalWallLayout?.applyTo(checkNotNull(before.physicalWallLayout))
                ?: before.physicalWallLayout,
            finishedPlayerIds = finishedPlayerIds ?: before.finishedPlayerIds,
            revealedHandTileIds = revealedHandTileIds ?: before.revealedHandTileIds,
        )
    }

    companion object {
        /** 只建立可驗證的結構差異；無法安全表示時回傳 null。 */
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
            )
            return result.takeIf { runCatching { it.applyTo(before) == after }.getOrDefault(false) }
        }

        /** 只接受原牌牆頭尾移除，避免把重排誤認為一般摸牌。 */
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

/**
 * 同一權威交易中唯一的桌況結果。
 */
sealed interface HistoryTableResult {
    /**
     * 可根據前一桌況還原的變化。
     *
     * @property change 本次交易的桌況差異。
     */
    data class Change(
        val change: HistoryTableChange,
    ) : HistoryTableResult

    /**
     * 無法安全表示結構變化時的完整檢查點。
     *
     * @property reasonId 選用完整檢查點的穩定原因 ID。
     * @property tableState 交易提交後的完整桌況。
     */
    data class Checkpoint(
        val reasonId: String,
        val tableState: TableState,
    ) : HistoryTableResult
}
