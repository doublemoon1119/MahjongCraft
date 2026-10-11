package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundContinuationContext
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundDirective
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolver
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.FabricDebugCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import kotlin.uuid.Uuid

/**
 * 開發用的 [WinRoundContinuationResolver]：模擬「胡牌後本局繼續」的流程，讓中途胡牌這條路徑不必等到
 * 真的實作出某個支援它的規則（例如雀魂赤血之戰）才能進遊戲驗證。
 *
 * 規則刻意做到最簡單、與任何真實麻將規則無關——它存在的唯一目的是把已經完成的底層機制推上實機：
 * 已完成玩家會被跳過（[TableState.finishedPlayerIds]）、
 * 中途胡牌演出走獨立時間軸不擋其他玩家、贏家立牌蓋起來但副露維持原狀。分數結算沿用既有胡牌結算，
 * 不做任何額外調整。
 *
 * 判定：本次贏家標記為已完成；剩餘 active 玩家**少於兩位**時回傳
 * [WinRoundDirective.EndRound] 讓本局照常結束（一個人打不下去），否則回傳
 * [WinRoundDirective.ContinueRound]，回合交給榮和放銃者（自摸時為贏家）之後的第一位 active 玩家。
 *
 * 只在 [MinecraftEnvironment.isDevelopment]
 * 為 `true` 時註冊，比照 [FabricDebugCommand] 的既有做法——正式打包發布的產物裡這個 resolver
 * 根本沒被註冊過。
 *
 * @property ruleModuleId 這個實例服務的規則模組 ID；registry 依此過濾，因此每個規則模組各註冊一個實例。
 * @property state 共用的執行期開關。
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

    /** 從 [referenceId] 的座位往後找第一位不在 [finishedAfter] 內的玩家。 */
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
