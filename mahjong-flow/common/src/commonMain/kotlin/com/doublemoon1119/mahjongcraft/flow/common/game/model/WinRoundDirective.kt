package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/** 一次胡牌即時結算完成後，本局後續應採取的權威決策。 */
sealed interface WinRoundDirective {
    /** 結束本局，維持既有日麻／台麻流程（進莊/連莊判定照舊）。 */
    data object EndRound : WinRoundDirective

    /**
     * 將 [newlyFinishedPlayerIds] 標記為本局已完成，回合交給 [nextPlayerId] 繼續，本局不結束。
     *
     * @property newlyFinishedPlayerIds 這次新標記為已完成的玩家；必須屬於這場對局且尚未列在
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
