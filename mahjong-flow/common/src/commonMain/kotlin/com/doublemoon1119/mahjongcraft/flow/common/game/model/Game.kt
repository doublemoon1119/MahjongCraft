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
 * @property forcedAutoPlayPlayerIds 已耗盡思考時間、目前這一次決策必須由伺服器自動操作的玩家。只鎖住
 *   逾時當下那一次決策——[com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator]
 *   每次替這裡的玩家送出自動命令後就會立即移除，玩家的下一次決策仍會拿到自己完整的
 *   `baseSeconds`，不會被永久接管。
 * @property isMatchOver 整場對局是否已依規則的 `GameLength` 結束（見
 *   [com.doublemoon1119.mahjongcraft.flow.server.game.usecase.AdvanceRoundUseCase]）。一旦成立，
 *   `tableState` 維持結束當下的樣子不再變動；`AiTurnDriver`／`ForcedAutoPlayDriver` 都會檢查這個
 *   欄位並提前跳過，避免對已經沒有牌可摸的桌況重複嘗試摸牌、重複觸發流局結算。
 */
data class Game(
    val tableState: TableState,
    val flowConfig: GameFlowConfig,
    val remainingReserveMillisByPlayerId: Map<Uuid, Long> = tableState.players.associate {
        it.id to flowConfig.timeControl.reserveSeconds * 1_000L
    },
    val forcedAutoPlayPlayerIds: Set<Uuid> = emptySet(),
    val isMatchOver: Boolean = false,
) {
    init {
        val playerIds = tableState.players.mapTo(mutableSetOf()) { it.id }
        require(remainingReserveMillisByPlayerId.keys == playerIds) {
            "Remaining reserve time must contain exactly the game players"
        }
        require(remainingReserveMillisByPlayerId.values.all { it >= 0L }) {
            "Remaining reserve time must not be negative"
        }
        require(forcedAutoPlayPlayerIds.all { it in playerIds }) {
            "Forced auto-play players must belong to the game"
        }
    }

    /** 與實體麻將桌及 [TableState] 共用的穩定識別碼。 */
    val id: Uuid get() = tableState.id
}
