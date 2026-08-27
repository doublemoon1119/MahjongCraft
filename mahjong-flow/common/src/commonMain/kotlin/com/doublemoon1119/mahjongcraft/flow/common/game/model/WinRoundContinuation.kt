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
 * `com.doublemoon1119.mahjongcraft.logic.module.WinResolutionResult`（番數、役種等呈現細節）——
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
 * 中途胡牌的規則中立**結算面板**詳細程度。
 *
 * 刻意只描述面板，不描述整段演出：胡牌演出本身（強制理牌重排、把贏家立牌倒下攤開、降臨特效，以及
 * 役滿成立時的 showcase）在任何模式下都完整播放，不可省略——它是「這個人胡了、退出本局」在世界裡
 * 唯一的視覺訊號，其他仍在局中的玩家必須看見，否則會有玩家憑空從本局消失。真正需要依情境調整的
 * 是面板：它是要**讀**的，會讓其他人乾等。
 */
enum class ContinuingWinSettlementMode {
    /** 既有的完整結算面板：役種、番符明細、手牌重現與名次變動。 */
    FULL,

    /**
     * 跳過贏家詳情，面板直接顯示分數變動。
     *
     * 手牌、胡牌張、寶牌與役種明細一律不重現——牌桌上已經攤開了，面板再畫一次只是拖時間，而
     * 「誰放銃給誰」從分數增減本來就看得出來。中途胡牌可能一局發生好幾次，面板是唯一會讓其他仍在
     * 局中的玩家乾等的東西，因此這裡選最快的收尾。
     */
    BRIEF,
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
 * 重啟後也沒有任何路徑會去消費殘留值，持久化換不到任何恢復能力。真正需要跨重啟的是演出本身，
 * 而那已經由 `MahjongTableBlockEntity` 的呈現時間軸與各實體自己的 NBT 動畫佇列負責。
 *
 * @property winnerPlayerIds 這次一起成立的所有贏家。
 * @property celebration 胡牌特效／役滿展示請求。
 * @property settlement 結算面板請求。
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
     * @property settlementMode 這次胡牌的結算面板詳細程度；整段胡牌演出不受它影響，一律完整播放。
     */
    data class ContinueRound(
        val newlyFinishedPlayerIds: Set<Uuid>,
        val nextPlayerId: Uuid,
        val settlementMode: ContinuingWinSettlementMode,
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
