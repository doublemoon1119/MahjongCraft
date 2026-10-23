package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import kotlinx.serialization.Serializable

/**
 * [Game] 中不屬於麻將規則桌況的可變 runtime 狀態。
 *
 * @property remainingReserveMillisByPlayerId 以玩家 UUID 字串索引的剩餘保留思考時間毫秒數。
 * @property forcedAutoPlayPlayerIds 已進入強制自動操作的玩家 UUID 字串集合。
 * @property enabledAutomaticControlIdsByPlayerId 以玩家 UUID 字串索引的本局自動操作控制 ID。
 * @property automaticControlRevision 本局自動操作集合的權威 revision。
 * @property isMatchOver 整場對局是否已結束，見 [Game.isMatchOver]。
 * @property pendingTransition 呈現結束後尚待完成的權威流程，見 [Game.pendingTransition]。
 * @property roundCompletion 最近一次本局結算的權威摘要。
 * @property matchEndReasonId 整場終局的完整 namespaced 原因；尚未終局時為 null。
 * @property hostId 開局時的房主 UUID 字串，見 [Game.hostId]。
 * @property roomPlayerIds 開局前房間成員的固定顯示順序。
 * @property interruptedBaseMillisByPlayerId 因 server session 結束而中斷的那一次決策，其尚未使用的
 *   基本思考時間毫秒數，以玩家 UUID 字串索引。
 * @property matchId 整場對局的穩定 UUID 字串。
 * @property aiPlayerStrategyKeys 以玩家 UUID 字串索引的 AI 策略 key，見 [Game.aiPlayerStrategyKeys]。
 * @property pendingRuleDecision 尚未完成的規則選擇流程；沒有時為 null。
 */
@Serializable
data class GameRuntimeStatePersistenceDto(
    val remainingReserveMillisByPlayerId: Map<String, Long>,
    val forcedAutoPlayPlayerIds: Set<String>,
    val enabledAutomaticControlIdsByPlayerId: Map<String, Set<String>>,
    val automaticControlRevision: Long,
    val isMatchOver: Boolean,
    val pendingTransition: PendingGameTransitionPersistenceDto?,
    val roundCompletion: RoundCompletionSummaryPersistenceDto?,
    val matchEndReasonId: String?,
    val pendingRuleDecision: PendingRuleDecisionPersistenceDto?,
    val hostId: String,
    val roomPlayerIds: List<String>,
    val interruptedBaseMillisByPlayerId: Map<String, Long>,
    val matchId: String,
    val aiPlayerStrategyKeys: Map<String, String>,
)
