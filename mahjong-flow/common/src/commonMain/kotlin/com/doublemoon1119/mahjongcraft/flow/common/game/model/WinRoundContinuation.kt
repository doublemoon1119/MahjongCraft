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

/** 中途胡牌演出的規則中立呈現模式。 */
enum class ContinuingWinPresentationMode {
    /** 既有胡牌／役滿演出，再接既有 win settlement 面板。 */
    FULL,

    /** 略過胡牌特效，只顯示既有結算面板。 */
    SETTLEMENT_ONLY,

    /** 不建立世界演出，但保留 chat、同步與權威分數。 */
    NONE,
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
     * @property presentationMode 這次胡牌的呈現模式；目前只保留欄位供未來的演出佇列擴充使用，尚未
     * 影響任何實際呈現行為。
     */
    data class ContinueRound(
        val newlyFinishedPlayerIds: Set<Uuid>,
        val nextPlayerId: Uuid,
        val presentationMode: ContinuingWinPresentationMode,
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
