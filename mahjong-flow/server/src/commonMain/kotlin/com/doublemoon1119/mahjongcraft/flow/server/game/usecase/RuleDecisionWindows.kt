package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingGameTransition
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRuleDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionStep
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTiming
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolution
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 在權威交易內開啟、推進與關閉規則選擇視窗的共用步驟。
 *
 * 只計算新的 [Game]，不寫入、不發布；呼叫端在同一筆寫入中一起保存，讓「開窗」「關窗並排定接續」都不會因中途當機而遺失。
 */
internal object RuleDecisionWindows {
    /**
     * 開窗或推進後的對局與要記錄的歷史事實。
     *
     * @property game 更新後的對局。
     * @property historyDrafts 這次要記錄的歷史事實；沒有開啟視窗時為空。
     */
    data class Update(val game: Game, val historyDrafts: List<HistoryEventDraft>)

    /**
     * 在 [timing] 詢問規則要不要開啟選擇視窗；規則不需要時原樣回傳。
     *
     * @param game 目前沒有開著視窗的對局。
     * @param timing 目前的時間點。
     * @param module 對局採用的規則模組。
     * @param resolverRegistry 規則提供的選擇流程。
     * @return 開窗後的對局，或原本的對局。
     * @throws RuleContractViolationException 需要玩家選擇卻沒有時間設定時。
     */
    fun open(
        game: Game,
        timing: RuleDecisionTiming,
        module: MahjongRuleModule<*>,
        resolverRegistry: RuleDecisionResolverRegistry,
    ): Update {
        check(game.pendingRuleDecision == null) { "A rule decision window is already open" }
        val resolver = resolverRegistry.find(module.id) ?: return Update(game, emptyList())
        val step = resolver.begin(timing, game.tableState, module) ?: return Update(game, emptyList())
        requireTimeConfiguration(game, step, module, resolverRegistry)
        return Update(
            game = game.copy(pendingRuleDecision = PendingRuleDecision.open(step, timing)),
            historyDrafts = listOf(HistoryEventDraft(actorPlayerId = null, fact = HistoryFact.RuleDecisionStarted(timing, step.stepId, step.stepIndex))),
        )
    }

    /**
     * 套用規則對收齊視窗的處理：寫入效果，再以新的視窗身分開啟下一步；沒有下一步時關閉視窗並排定接續。
     *
     * [RuleDecisionTiming.BEFORE_EXHAUSTIVE_DRAW] 的視窗關閉時排定一般流局結算；其他時間點的接續已由開窗前的流程排定，
     * 或直接回到一般摸打。[RuleDecisionTiming.ROUND_END] 時本局已結算，規則的處理結果不能改變任何玩家的分數。
     *
     * @param game 開著 [decision] 的對局。
     * @param decision 剛收齊或沒有參與者的視窗。
     * @param resolution 規則的處理結果。
     * @param module 對局採用的規則模組。
     * @param resolverRegistry 規則提供的選擇流程。
     * @return 推進或關閉視窗後的對局。
     * @throws RuleContractViolationException 下一步需要玩家選擇卻沒有時間設定時，或本局結算後的選擇改變了分數時。
     */
    fun advance(
        game: Game,
        decision: PendingRuleDecision,
        resolution: RuleDecisionResolution,
        module: MahjongRuleModule<*>,
        resolverRegistry: RuleDecisionResolverRegistry,
    ): Game {
        if (decision.timing == RuleDecisionTiming.ROUND_END && scoresOf(resolution.tableState) != scoresOf(game.tableState)) {
            throw RuleContractViolationException(
                GameAbortReasonIds.RULE_EXECUTION_FAILED,
                "Rule decisions after the round is settled cannot change scores",
            )
        }
        val next = resolution.nextStep?.let { step ->
            requireTimeConfiguration(game, step, module, resolverRegistry)
            PendingRuleDecision.open(step, decision.timing)
        }
        val settlesDraw = next == null && decision.timing == RuleDecisionTiming.BEFORE_EXHAUSTIVE_DRAW
        return game.copy(
            tableState = resolution.tableState,
            pendingRuleDecision = next,
            pendingTransition = if (settlesDraw) PendingGameTransition.SettleExhaustiveDraw else game.pendingTransition,
        )
    }

    /**
     * 取得每位玩家目前的分數。
     *
     * @param tableState 要讀取的桌況。
     * @return 以玩家識別碼索引的分數。
     */
    private fun scoresOf(tableState: TableState): Map<Uuid, Int> = tableState.players.associate { it.id to it.score }

    /**
     * 需要玩家輸入的步驟必須有時間設定，否則玩家沒有逾時的出路。
     *
     * @param game 目前的對局。
     * @param step 要開啟的步驟。
     * @param module 對局採用的規則模組。
     * @param resolverRegistry 規則提供的選擇流程。
     * @throws RuleContractViolationException 缺少時間設定時。
     */
    private fun requireTimeConfiguration(
        game: Game,
        step: RuleDecisionStep,
        module: MahjongRuleModule<*>,
        resolverRegistry: RuleDecisionResolverRegistry,
    ) {
        if (step.participantPlayerIds.isEmpty()) return
        if (resolverRegistry.findDefaultConfig(module.id, game.tableState.config) == null || game.flowConfig.ruleDecision == null) {
            throw RuleContractViolationException(
                GameAbortReasonIds.RULE_EXECUTION_FAILED,
                "Rule decision requires a time configuration for player input",
            )
        }
    }
}
