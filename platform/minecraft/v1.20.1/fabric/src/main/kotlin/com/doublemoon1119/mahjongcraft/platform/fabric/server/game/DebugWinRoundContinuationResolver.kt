package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ContinuingWinSettlementMode
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundContinuationContext
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundDirective
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/**
 * `/mahjongcraft debug continuing_win <mode>` 目前選定的中途胡牌模式。
 *
 * @property settlementMode 中途胡牌時採用的結算面板模式；`null` 代表功能關閉。
 */
enum class DebugWinRoundContinuationMode(val settlementMode: ContinuingWinSettlementMode?) {
    /** 關閉：resolver 一律回傳 `null`，胡牌後立即結束本局，與正式行為完全相同。 */
    OFF(settlementMode = null),

    /** 中途胡牌，胡牌演出照常，接完整結算面板。 */
    FULL(settlementMode = ContinuingWinSettlementMode.FULL),

    /** 中途胡牌，胡牌演出照常，接精簡結算面板（只有贏家、放銃者與分數）。 */
    BRIEF(settlementMode = ContinuingWinSettlementMode.BRIEF),
}

/**
 * [DebugWinRoundContinuationResolver] 的執行期開關，由 `/mahjongcraft debug continuing_win` 切換。
 *
 * 之所以需要一個獨立的可變狀態物件，而不是讓指令直接把 resolver 註冊進
 * [WinRoundContinuationResolverRegistry]：該 registry 在 bootstrap 就 `freeze()`，之後禁止再註冊
 * （這是刻意的設計，讓判定順序在整個 server session 內固定不變）。因此 resolver 本身在開發環境固定
 * 註冊、但預設沒有任何桌子開啟時完全 inert，由指令逐桌切換模式。
 *
 * **以桌為範圍**：模式存在以 tableId 為鍵的 map 裡（[com.doublemoon1119.mahjongcraft.logic.table.TableState.id]
 * 即該桌的 tableId），指令只影響執行者目前所在的那一桌。全伺服器共用的單一開關會讓同一個開發伺服器上
 * 其他桌莫名其妙進入中途胡牌流程，而且開啟後會一直有效到有人記得手動關掉。
 *
 * 條目在該桌的對局結束（`FabricGamePresentationPublisher` 發布 match settlement 時）或該桌被破壞
 * （`FabricTableLifecycleService` 清理孤兒桌時）就會清除，不會累積。
 *
 * 會被遊戲主執行緒（resolver 判定）與指令執行緒同時讀寫，因此用 [ConcurrentHashMap]。
 */
@Single
class DebugWinRoundContinuationState {
    private val modesByTableId = ConcurrentHashMap<Uuid, DebugWinRoundContinuationMode>()

    /** [tableId] 目前的模式；沒有設定過時為 [DebugWinRoundContinuationMode.OFF]。 */
    fun modeFor(tableId: Uuid): DebugWinRoundContinuationMode = modesByTableId[tableId] ?: DebugWinRoundContinuationMode.OFF

    /** 設定 [tableId] 的模式；設為 [DebugWinRoundContinuationMode.OFF] 等同移除條目。 */
    fun setMode(tableId: Uuid, mode: DebugWinRoundContinuationMode) {
        if (mode == DebugWinRoundContinuationMode.OFF) {
            modesByTableId.remove(tableId)
        } else {
            modesByTableId[tableId] = mode
        }
    }

    /** 清除 [tableId] 的設定；該桌對局結束或桌子被破壞時呼叫。 */
    fun clear(tableId: Uuid) {
        modesByTableId.remove(tableId)
    }

    /** 目前仍有設定的桌子，供指令回報影響範圍。 */
    fun activeTableIds(): Set<Uuid> = modesByTableId.keys.toSet()
}

/**
 * 開發用的 [WinRoundContinuationResolver]：模擬「胡牌後本局繼續」的流程，讓中途胡牌這條路徑不必等到
 * 真的實作出某個支援它的規則（例如雀魂赤血之戰）才能進遊戲驗證。
 *
 * 規則刻意做到最簡單、與任何真實麻將規則無關——它存在的唯一目的是把已經完成的底層機制推上實機：
 * 已完成玩家會被跳過（[com.doublemoon1119.mahjongcraft.logic.table.TableState.finishedPlayerIds]）、
 * 中途胡牌演出走獨立時間軸不擋其他玩家、贏家立牌蓋起來但副露維持原狀。分數結算沿用既有胡牌結算，
 * 不做任何額外調整。
 *
 * 判定：本次贏家標記為已完成；剩餘 active 玩家**少於兩位**時回傳
 * [WinRoundDirective.EndRound] 讓本局照常結束（一個人打不下去），否則回傳
 * [WinRoundDirective.ContinueRound]，回合交給榮和放銃者（自摸時為贏家）之後的第一位 active 玩家。
 *
 * 只在 [com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment.isDevelopment]
 * 為 `true` 時註冊，比照 [FabricDebugAnimationCommand] 的既有做法——正式打包發布的產物裡這個 resolver
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
        val settlementMode = state.modeFor(tableState.id).settlementMode ?: return null
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
            settlementMode = settlementMode,
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

/** 登記開發用的中途胡牌 resolver，內建的每個規則模組各一個實例。 */
fun WinRoundContinuationResolverRegistry.registerDebugWinRoundContinuationResolvers(
    state: DebugWinRoundContinuationState,
) {
    listOf(BuiltInRuleModuleIds.RIICHI, BuiltInRuleModuleIds.TAIWAN).forEach { ruleModuleId ->
        register(DebugWinRoundContinuationResolver(ruleModuleId = ruleModuleId, state = state))
    }
}
