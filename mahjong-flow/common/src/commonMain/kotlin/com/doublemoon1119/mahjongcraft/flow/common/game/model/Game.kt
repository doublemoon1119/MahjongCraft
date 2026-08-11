package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 進行中遊戲的 flow 層權威狀態。
 *
 * 此模型包含完整桌況與未公開資料，僅供伺服器內部的 repository 與 use case 使用，
 * 不得直接作為網路傳輸模型。對外資料必須先依讀取者身分投影為快照。
 *
 * @property tableState 麻將規則運算使用的完整桌況。
 * @property flowConfig 不影響麻將規則的流程與觀看設定。
 * @property remainingReserveMillisByPlayerId 每位玩家在整場遊戲中尚未使用的保留思考時間毫秒數。
 */
data class Game(
    val tableState: TableState,
    val flowConfig: GameFlowConfig,
    val remainingReserveMillisByPlayerId: Map<Uuid, Long> = tableState.players.associate {
        it.id to flowConfig.timeControl.reserveSeconds * 1_000L
    },
) {
    init {
        val playerIds = tableState.players.mapTo(mutableSetOf()) { it.id }
        require(remainingReserveMillisByPlayerId.keys == playerIds) {
            "Remaining reserve time must contain exactly the game players"
        }
        require(remainingReserveMillisByPlayerId.values.all { it >= 0L }) {
            "Remaining reserve time must not be negative"
        }
    }

    /** 與實體麻將桌及 [TableState] 共用的穩定識別碼。 */
    val id: Uuid get() = tableState.id
}
