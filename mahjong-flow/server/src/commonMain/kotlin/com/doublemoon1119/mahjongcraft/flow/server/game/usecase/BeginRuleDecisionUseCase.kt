package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingGameTransition
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTiming
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameAbortHandler
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import kotlin.uuid.Uuid

/**
 * 在指定時間點詢問規則要不要請玩家做選擇，需要時開啟規則選擇視窗。
 *
 * @property gameRepository 讀取與更新權威遊戲。
 * @property moduleRegistry 依桌況設定取得規則模組。
 * @property resolverRegistry 取得規則提供的選擇流程解析器。
 * @property snapshotSynchronizer 更新選擇流程變更後的觀看者快照。
 * @property abortGameHandler 規則契約無法安全繼續時，以失敗交易讀到的對局為基準中止它。
 */
class BeginRuleDecisionUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val resolverRegistry: RuleDecisionResolverRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    private val abortGameHandler: GameAbortHandler,
) {
    /**
     * 在 [timing] 開啟規則選擇視窗；規則不需要選擇時不改變對局。
     *
     * 已經有視窗開著時不再開新的，只寫入 [transition]。[transition] 與開窗在同一筆寫入中保存，視窗關閉後才接續它。
     *
     * @param gameId 對局識別碼。
     * @param timing 目前的時間點。
     * @param transition 同時排定、等視窗關閉後才執行的流程；沒有時為 null。
     * @return 是否開啟了視窗；對局不存在時回傳錯誤。
     * @throws RuleContractViolationException 規則需要玩家選擇但沒有時間設定時；對局會先嘗試中止。
     */
    suspend operator fun invoke(
        gameId: Uuid,
        timing: RuleDecisionTiming,
        transition: PendingGameTransition? = null,
    ): Outcome<Boolean, GameError> {
        var contractBasis: Game? = null
        val result = try {
            gameRepository.updateGame(
                gameId,
                history = { _, _, update -> update.historyDrafts },
            ) { game ->
                if (game == null) {
                    return@updateGame null to BeginUpdate(Outcome.Error(GameError.GameNotFound(gameId)), emptyList())
                }
                val scheduled = transition?.let { game.copy(pendingTransition = it) } ?: game
                if (scheduled.pendingRuleDecision != null) {
                    return@updateGame scheduled to BeginUpdate(Outcome.Success(false), emptyList())
                }
                contractBasis = game
                val opened = RuleDecisionWindows.open(scheduled, timing, moduleRegistry.getModule(game.tableState.config), resolverRegistry)
                opened.game to BeginUpdate(Outcome.Success(opened.game.pendingRuleDecision != null), opened.historyDrafts)
            }
        } catch (failure: RuleContractViolationException) {
            contractBasis?.let { basis ->
                abortGameHandler.abort(basis, failure.reasonId, failure.message ?: "Rule decision contract violation")
            }
            throw failure
        }
        if ((result.outcome as? Outcome.Success)?.value == true) snapshotSynchronizer.syncAll(gameId)
        return result.outcome
    }

    /**
     * 原子更新後的開窗結果與歷史事實。
     *
     * @property outcome use case 對呼叫端回傳的結果。
     * @property historyDrafts 開啟視窗時要記錄的事實；沒有開窗時為空。
     */
    private data class BeginUpdate(
        val outcome: Outcome<Boolean, GameError>,
        val historyDrafts: List<HistoryEventDraft>,
    )
}
