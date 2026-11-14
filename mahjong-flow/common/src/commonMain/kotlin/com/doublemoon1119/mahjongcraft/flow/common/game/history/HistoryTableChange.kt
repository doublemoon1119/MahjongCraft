package com.doublemoon1119.mahjongcraft.flow.common.game.history

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.module.RevealedHandSettlement
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.TurnPhase
import com.doublemoon1119.mahjongcraft.logic.table.opening.OpeningDiceRoll
import kotlin.uuid.Uuid

/**
 * 一筆權威交易造成的可重建桌況差異。
 *
 * @property components 對局組件的新集合；未改變時為 null，空集合代表清空。
 * @property changedPlayers 實際變動的玩家與新追加動作，未列出的玩家保持原值。
 * @property wall 活牌牆兩端的移除；未改變時為 null。
 * @property currentPlayerIndex 新的行動玩家索引；未改變時為 null。
 * @property turnPhase 新的回合階段；未改變時為 null。
 * @property reservedWallTiles 規則保留牌的新順序；未改變時為 null。
 * @property physicalWallLayout 實體牌牆位置的局部變化；未改變時為 null。
 * @property finishedPlayerIds 本局已完成玩家的新集合；未改變時為 null。
 * @property publicTileEvidenceIds 本局公開證據的新集合；未改變時為 null。
 * @property revealedHandSettlements 流局公開手牌與等待牌的新資料；未改變時為 null，空集合代表清空。
 * @property openingDiceRolls 本局逐輪擲骰結果；未改變時為 null。
 * @property appendedDiscards 接在本局打牌順序後面的牌；沒有新打出的牌時為空清單。一局之內打牌順序只會往後加。
 */
data class HistoryTableChange(
    val components: ComponentSet? = null,
    val changedPlayers: List<HistoryPlayerChange> = emptyList(),
    val wall: HistoryWallChange? = null,
    val currentPlayerIndex: Int? = null,
    val turnPhase: TurnPhase? = null,
    val reservedWallTiles: List<IdentifiedTile>? = null,
    val physicalWallLayout: HistoryWallLayoutChange? = null,
    val finishedPlayerIds: Set<Uuid>? = null,
    val publicTileEvidenceIds: Set<Uuid>? = null,
    val revealedHandSettlements: List<RevealedHandSettlement>? = null,
    val openingDiceRolls: List<OpeningDiceRoll>? = null,
    val appendedDiscards: List<Uuid> = emptyList(),
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
            components = components ?: before.components,
            players = before.players.map { player -> replacements[player.id]?.applyTo(player) ?: player },
            tileWall = wall?.applyTo(before.tileWall) ?: before.tileWall,
            currentPlayerIndex = currentPlayerIndex ?: before.currentPlayerIndex,
            turnPhase = turnPhase ?: before.turnPhase,
            reservedWallTiles = reservedWallTiles ?: before.reservedWallTiles,
            physicalWallLayout = physicalWallLayout?.applyTo(checkNotNull(before.physicalWallLayout))
                ?: before.physicalWallLayout,
            finishedPlayerIds = finishedPlayerIds ?: before.finishedPlayerIds,
            publicTileEvidenceIds = publicTileEvidenceIds ?: before.publicTileEvidenceIds,
            revealedHandSettlements = revealedHandSettlements ?: before.revealedHandSettlements,
            openingDiceRolls = openingDiceRolls ?: before.openingDiceRolls,
            discardOrder = before.discardOrder + appendedDiscards,
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
                before.roundSerial != after.roundSerial ||
                before.roundPosition != after.roundPosition ||
                before.comboCount != after.comboCount ||
                before.wallOpening != after.wallOpening
            ) {
                return null
            }
            if (before.players.map(MahjongPlayer::id) != after.players.map(MahjongPlayer::id)) return null
            if (after.discardOrder.take(before.discardOrder.size) != before.discardOrder) return null
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
                components = after.components.takeIf { it != before.components },
                changedPlayers = changedPlayers,
                wall = wallChange.takeUnless { it.removedFront == 0 && it.removedBack == 0 && it.updatedTiles.isEmpty() },
                currentPlayerIndex = after.currentPlayerIndex.takeIf { it != before.currentPlayerIndex },
                turnPhase = after.turnPhase.takeIf { it != before.turnPhase },
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
                publicTileEvidenceIds = after.publicTileEvidenceIds.takeIf { it != before.publicTileEvidenceIds },
                revealedHandSettlements = after.revealedHandSettlements.takeIf {
                    it != before.revealedHandSettlements
                },
                openingDiceRolls = after.openingDiceRolls.takeIf { it != before.openingDiceRolls },
                appendedDiscards = after.discardOrder.drop(before.discardOrder.size),
            )
            return result.takeIf { runCatching { it.applyTo(before) == after }.getOrDefault(false) }
        }

        /**
         * 只接受原牌牆頭尾移除與組件變化，不把重排或改牌面當作一般更新。
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
            val first = old.indexOfFirst { it.id == new.first().id }
            if (first < 0 || first + new.size > old.size) return null
            val retained = old.subList(first, first + new.size)
            if (retained.zip(new).any { (previous, current) -> previous.id != current.id || previous.tile != current.tile }) return null
            return HistoryWallChange(
                removedFront = first,
                removedBack = old.size - first - new.size,
                updatedTiles = retained.zip(new).mapNotNull { (previous, current) -> current.takeIf { it != previous } },
            )
        }
    }
}
