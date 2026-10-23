package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DiscardReadinessAnalysisDto
import kotlinx.serialization.Serializable

/**
 * 只傳給取得決策權玩家的決策提示。
 *
 * 所有牌張 ID 都是該玩家已知的實體手牌；分析結果只包含自身與公開資訊，不包含暗手或牌山內容。
 *
 * @property decisionKey 此次決策的穩定 key。
 * @property ruleModuleId 這局採用的規則模組 ID，決定動作與狀態的用語；規則未知時為 null，呈現層改用中立預設。
 * @property actions 可供玩家選擇的動作候選。
 * @property triggerTileAssetKey 觸發反應的牌面 asset key。
 * @property triggerPlayerId 觸發反應的玩家 ID。
 * @property triggerPlayerName 觸發反應的玩家名稱。
 * @property triggerPlayerRelation 觸發玩家相對於目前玩家的位置。
 * @property triggerActionId 觸發反應的動作 ID。
 * @property ruleDecision 規則選擇提示。
 * @property discardAnalyses 目前狀態下的捨牌分析。
 */
@Serializable
data class PlayerDecisionPromptDto(
    val decisionKey: String,
    val ruleModuleId: String?,
    val actions: List<PlayerDecisionActionDto>,
    val triggerTileAssetKey: String?,
    val triggerPlayerId: String?,
    val triggerPlayerName: String?,
    val triggerPlayerRelation: DecisionPlayerRelationDto?,
    val triggerActionId: String?,
    val ruleDecision: RuleDecisionPromptDto?,
    val discardAnalyses: List<DiscardReadinessAnalysisDto>,
)
