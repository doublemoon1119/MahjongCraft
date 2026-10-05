package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 一次胡牌（自摸／榮和，含搶槓；不含流局）即時結算完成後，供
 * `com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolver` 判斷本局
 * 是否真的結束的規則中立上下文。
 *
 * 一炮多響只在全部贏家的分數都已結算進 [settledTableState] 之後呼叫一次，[winnerPlayerIds] 會包含
 * 這次一起成立的所有贏家，不逐位呼叫。
 *
 * 刻意不攜帶 `MahjongRuleModule.declareTsumo`／`declareRon` 算出的原始
 * `WinResolutionResult`（番數、役種等詳情）——
 * [settledTableState] 的分數與 `actionHistory` 已經是套用結算後的最終值，「本局是否結束」這個判斷
 * 只需要看結算後的桌況即可，不需要重新理解規則特有的算役細節。
 *
 * @property previousTableState 這次胡牌指令送出前的桌況（尚未套用本次結算），用於還原放銃者／搶槓
 * 宣告者身分（此時 `pendingReaction`／`pendingKanReaction` 仍未清除）。
 * @property settledTableState 本次結算完成後的權威桌況：分數與贏家的 `actionHistory` 皆已是最終值，
 * 但 `finishedPlayerIds`／`currentPlayerIndex` 仍是結算前的值，尚未套用 resolver 這次的決策。
 * @property winnerPlayerIds 這次一起成立的所有贏家 Uuid。
 * @property ronDiscarderId 榮和（含搶槓）時的放銃者／暗槓或加槓宣告者 Uuid；自摸時為 null。
 * @property winningTileId 胡牌張的 Uuid。
 */
data class WinRoundContinuationContext(
    val previousTableState: TableState,
    val settledTableState: TableState,
    val winnerPlayerIds: Set<Uuid>,
    val ronDiscarderId: Uuid?,
    val winningTileId: Uuid,
)

/**
 * 中途胡牌（本局在胡牌後繼續）時，這次結算要提供給玩家的資訊範圍。
 *
 * 只影響結算內容；「這位玩家胡了」本身一律要讓所有玩家知道，不受此設定影響。
 */
enum class ContinuingWinSettlementDetail {
    /** 提供贏家的完整詳情（例如役種、翻符、手牌）與分數變動。 */
    WINNER_DETAILS,

    /** 只提供分數變動；贏家的牌已經公開在桌上，詳情不再另外提供。 */
    SCORE_CHANGES_ONLY,
}

/**
 * 一次胡牌剛結算完成、但還沒決定該立即播放或延後播放的完整呈現內容。
 *
 * 由 `DeclareTsumoUseCase`／`RespondToDiscardUseCase`／`RespondToKanUseCase` 建構後交給
 * `WinPresentationHandoff` 暫存（而不是直接發布），再由 `ResolveWinRoundContinuationUseCase` 依本次
 * 的 [WinRoundDirective] 取走。
 *
 * 之所以需要一個暫存交接點，而不是讓 use case 直接回傳給呼叫端：`GameActionRouter` 對所有指令（含
 * 第三方擴充指令）統一回傳 `Outcome<Unit, GameError>`，要讓演出內容穿過它就得改動每一種指令的回傳
 * 型別，連與胡牌無關的摸牌／捨牌都會被迫認識胡牌演出型別。走交接點則讓建構邏輯留在原本就持有所需
 * registry 的 use case 內。
 *
 * 交接點刻意**不持久化**：它只在單次指令派發內存活（use case 寫入、同一次派發的收斂階段就取走），
 * 重啟後也沒有任何路徑會去消費殘留值，持久化換不到任何恢復能力。跨重啟的呈現狀態由平台自行保存。
 *
 * @property winnerPlayerIds 這次一起成立的所有贏家。
 * @property celebration 胡牌演出請求。
 * @property settlement 結算請求。
 */
data class SettledWinPresentation(
    val winnerPlayerIds: Set<Uuid>,
    val celebration: WinCelebrationRequest,
    val settlement: WinSettlementPresentationRequest,
) {
    init {
        require(winnerPlayerIds.isNotEmpty()) { "A settled win presentation must have at least one winner" }
    }
}

/** 一次胡牌即時結算完成後，本局後續應採取的權威決策。 */
sealed interface WinRoundDirective {
    /** 結束本局，維持既有日麻／台麻流程（進莊/連莊判定照舊）。 */
    data object EndRound : WinRoundDirective

    /**
     * 將 [newlyFinishedPlayerIds] 標記為本局已完成，回合交給 [nextPlayerId] 繼續，本局不結束。
     *
     * @property newlyFinishedPlayerIds 這次新標記為已完成的玩家；必須屬於本桌且尚未列在
     * [TableState.finishedPlayerIds]，見 [applyTo]。
     * @property nextPlayerId 套用後應輪到的玩家；必須仍是 active（不在套用後的 finished 集合內）。
     * @property settlementDetail 這次胡牌結算要提供的資訊範圍。
     */
    data class ContinueRound(
        val newlyFinishedPlayerIds: Set<Uuid>,
        val nextPlayerId: Uuid,
        val settlementDetail: ContinuingWinSettlementDetail,
    ) : WinRoundDirective
}

/**
 * 驗證並將 [WinRoundDirective.ContinueRound] 套用到 [state]，回傳套用後的新 [TableState]。
 *
 * @throws IllegalArgumentException [newlyFinishedPlayerIds] 內含不屬於本桌、或已經是 finished 的玩家；
 * 或套用後將導致所有玩家皆 finished（resolver 遇到這種終止條件應改回傳 [WinRoundDirective.EndRound]）；
 * 或 [nextPlayerId] 不屬於本桌、或套用後仍是 finished。
 */
fun WinRoundDirective.ContinueRound.applyTo(state: TableState): TableState {
    val playerIds = state.players.mapTo(mutableSetOf()) { it.id }
    require(newlyFinishedPlayerIds.all { it in playerIds }) {
        "newlyFinishedPlayerIds must belong to this table: $newlyFinishedPlayerIds"
    }
    require(newlyFinishedPlayerIds.none { it in state.finishedPlayerIds }) {
        "newlyFinishedPlayerIds must not already be finished: $newlyFinishedPlayerIds"
    }
    val updatedFinishedPlayerIds = state.finishedPlayerIds + newlyFinishedPlayerIds
    require(updatedFinishedPlayerIds.size < state.playerCount) {
        "ContinueRound must leave at least one active player; return EndRound instead once the end condition is met"
    }
    require(nextPlayerId in playerIds && nextPlayerId !in updatedFinishedPlayerIds) {
        "nextPlayerId must be an active player on this table: $nextPlayerId"
    }
    return state.copy(
        finishedPlayerIds = updatedFinishedPlayerIds,
        currentPlayerIndex = state.players.indexOfFirst { it.id == nextPlayerId },
    )
}
