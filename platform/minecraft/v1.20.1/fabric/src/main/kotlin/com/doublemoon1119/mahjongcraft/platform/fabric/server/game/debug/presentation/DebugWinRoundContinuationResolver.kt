package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundContinuationContext
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundDirective
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolver
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import kotlin.uuid.Uuid

/**
 * 開發環境用的和牌後續局判定器，不改變既有和牌分數。
 * 將本次新贏家標記為完成；未完成玩家少於兩位時結束本局，
 * 否則由放銃者（自摸時為贏家）之後第一位未完成玩家繼續行動。
 * 未開啟桌級模式時不提供判定；只在開發環境註冊，優先級低於正式規則判定器。
 *
 * @property ruleModuleId 此實例服務的規則模組識別碼。
 * @property state 各桌採用的開發模式。
 */
class DebugWinRoundContinuationResolver(
    override val ruleModuleId: String,
    private val state: DebugWinRoundContinuationState,
) : WinRoundContinuationResolver {
    // ruleModuleId 本身已經是 namespaced（例如 `mahjongcraft:riichi`），id() 不接受 path 內再帶
    // namespace，因此只取 path 段。
    override val id: String = MahjongCraftMetadata.id("debug_continuing_win/${ruleModuleId.substringAfter(':')}")

    /** 排在所有真實規則 resolver 之後，開啟時也不會蓋掉規則本身的判定。 */
    override val priority: Int = Int.MAX_VALUE

    override fun resolve(context: WinRoundContinuationContext, ruleModule: MahjongRuleModule<*>): WinRoundDirective? {
        val tableState = context.settledTableState
        val settlementDetail = state.modeFor(tableState.id).settlementDetail ?: return null
        val newlyFinishedPlayerIds = context.winnerPlayerIds - tableState.finishedPlayerIds
        if (newlyFinishedPlayerIds.isEmpty()) return WinRoundDirective.EndRound
        val finishedAfter = tableState.finishedPlayerIds + newlyFinishedPlayerIds
        // 只剩一位 active 就沒得繼續打了；ContinueRound 本身也禁止把所有人都標記成完成。
        if (tableState.players.count { it.id !in finishedAfter } < 2) return WinRoundDirective.EndRound
        // 榮和時回合接在放銃者之後（維持原本的順位感），自摸時接在贏家之後。
        val referenceId = context.ronDiscarderId ?: newlyFinishedPlayerIds.first()
        val nextPlayerId = nextActiveAfter(context, referenceId, finishedAfter) ?: return WinRoundDirective.EndRound
        return WinRoundDirective.ContinueRound(
            newlyFinishedPlayerIds = newlyFinishedPlayerIds,
            nextPlayerId = nextPlayerId,
            settlementDetail = settlementDetail,
        )
    }

    /**
     * 從指定座位循環往後找第一位尚未完成的玩家。
     *
     * @param context 已完成結算的對局情境。
     * @param referenceId 起算的玩家識別碼。
     * @param finishedAfter 本次結算後所有已完成的玩家。
     * @return 下一位仍在局中的玩家；起算玩家不存在或全員完成時為 null。
     */
    private fun nextActiveAfter(
        context: WinRoundContinuationContext,
        referenceId: Uuid,
        finishedAfter: Set<Uuid>,
    ): Uuid? {
        val players = context.settledTableState.players
        val referenceIndex = players.indexOfFirst { it.id == referenceId }
        if (referenceIndex < 0) return null
        return (1..players.size)
            .map { offset -> players[(referenceIndex + offset) % players.size] }
            .firstOrNull { it.id !in finishedAfter }
            ?.id
    }
}
