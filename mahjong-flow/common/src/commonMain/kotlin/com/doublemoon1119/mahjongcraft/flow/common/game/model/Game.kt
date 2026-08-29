package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionSummary
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
 *   逾時當下那一次決策——`com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator`
 *   每次替這裡的玩家送出自動命令後就會立即移除，玩家的下一次決策仍會拿到自己完整的
 *   `baseSeconds`，不會被永久接管。
 * @property isMatchOver 整場對局是否已依規則的 `GameLength` 結束（見
 *   `com.doublemoon1119.mahjongcraft.flow.server.game.usecase.AdvanceRoundUseCase`）。一旦成立，
 *   `tableState` 維持結束當下的樣子不再變動；`AiTurnDriver`／`ForcedAutoPlayDriver` 都會檢查這個
 *   欄位並提前跳過，避免對已經沒有牌可摸的桌況重複嘗試摸牌、重複觸發流局結算。
 * @property pendingTransition 呈現動畫結束後尚待完成的權威流程；`null` 代表沒有待收斂的流程。
 * @property roundCompletion 最近一次本局結算的權威摘要；進入下一局後清除。
 * @property matchEndReasonId 整場終局的完整 namespaced 原因；尚未終局時為 null。
 * @property pendingRoundPreparation 發牌後、正常摸打前尚待完成的規則準備步驟；沒有步驟時為 null。
 * @property hostId 開局時的房主 Uuid，取自原本 `Room.hostId`——`com.doublemoon1119.mahjongcraft.flow.server.game.usecase.StartGameUseCase`
 *   把 Room 轉換成 Game 後，房主身分不再能從 `Room` 讀出，保留在這裡讓對局結束轉回 Room
 *   （見 `ReturnToRoomUseCase`）時能還原同一位房主，預設值取第一位玩家僅供未指定房主的測試情境使用；
 *   `tableState` 沒有任何玩家時（同樣僅見於測試情境）退回隨機值，不受下方驗證約束。
 */
data class Game(
    val tableState: TableState,
    val flowConfig: GameFlowConfig,
    val remainingReserveMillisByPlayerId: Map<Uuid, Long> = tableState.players.associate {
        it.id to flowConfig.timeControl.reserveSeconds * 1_000L
    },
    val forcedAutoPlayPlayerIds: Set<Uuid> = emptySet(),
    val isMatchOver: Boolean = false,
    val pendingTransition: PendingGameTransition? = null,
    val roundCompletion: RoundCompletionSummary? = null,
    val matchEndReasonId: String? = null,
    val pendingRoundPreparation: PendingRoundPreparation? = null,
    val hostId: Uuid = tableState.players.firstOrNull()?.id ?: Uuid.random(),
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
        require(pendingRoundPreparation?.participantPlayerIds?.all { it in playerIds } != false) {
            "Round preparation participants must belong to the game"
        }
        require(playerIds.isEmpty() || hostId in playerIds) {
            "Host must belong to the game"
        }
        require(roundCompletion?.settledScoresByPlayerId?.keys?.let { it == playerIds } != false) {
            "Round completion scores must contain exactly the game players"
        }
    }

    /** 與實體麻將桌及 [TableState] 共用的穩定識別碼。 */
    val id: Uuid get() = tableState.id
}
