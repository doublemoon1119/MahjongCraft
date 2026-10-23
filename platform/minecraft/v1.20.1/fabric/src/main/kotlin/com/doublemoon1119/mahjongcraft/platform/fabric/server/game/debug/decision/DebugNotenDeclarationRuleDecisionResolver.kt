package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.decision

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRuleDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionInputSpec
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionStep
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionSubmission
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTiming
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolution
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolver
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiFamilyRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule.DebugDeclareNotenAction
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule.DebugNotenDeclarationRiichiRuleModule

/**
 * [DebugNotenDeclarationRiichiRuleModule] 的規則選擇：一般流局結算前讓聽牌玩家選擇公開聽牌或宣告不聽，本局結算後
 * 請所有玩家確認。
 *
 * 一般流局結算前只詢問仍在局中、沒有立直且手牌聽牌的玩家；沒有這樣的玩家時直接結算。宣告不聽的玩家記錄
 * [DebugDeclareNotenAction]，由規則模組的一般流局結算當作不聽。逾時或 AI 沒有選擇時採用第一個選項，也就是公開聽牌。
 */
internal class DebugNotenDeclarationRuleDecisionResolver : RuleDecisionResolver {
    override val ruleModuleId: String = DebugNotenDeclarationRiichiRuleModule.ID

    override fun ruleDecisionConfig(ruleConfig: MahjongRuleConfig): RuleDecisionConfig = RuleDecisionConfig(DECISION_SECONDS)

    override fun begin(
        timing: RuleDecisionTiming,
        tableState: TableState,
        ruleModule: MahjongRuleModule<*>,
    ): RuleDecisionStep? = when (timing) {
        RuleDecisionTiming.BEFORE_EXHAUSTIVE_DRAW -> notenDeclarationStep(tableState, ruleModule)
        RuleDecisionTiming.ROUND_END -> RuleDecisionStep(
            stepId = ROUND_END_CONFIRMATION_STEP,
            stepIndex = 0,
            inputSpecsByPlayerId = tableState.players.associate { it.id to RuleDecisionInputSpec.Confirmation },
        )
        RuleDecisionTiming.ROUND_START, RuleDecisionTiming.WIN_CONTINUES -> null
    }

    override fun resolve(
        tableState: TableState,
        decision: PendingRuleDecision,
        ruleModule: MahjongRuleModule<*>,
    ): RuleDecisionResolution {
        if (decision.stepId != NOTEN_DECLARATION_STEP) return RuleDecisionResolution(tableState, nextStep = null)
        val declaredIds = decision.submissionsByPlayerId
            .filterValues { submission -> (submission as? RuleDecisionSubmission.Choice)?.optionId == DECLARE_NOTEN_OPTION }
            .keys
        val players = tableState.players.map { player ->
            if (player.id in declaredIds) player.recordAction(GameAction.Extension(DebugDeclareNotenAction)) else player
        }
        return RuleDecisionResolution(tableState.copy(players = players), nextStep = null)
    }

    /**
     * 建立一般流局結算前的宣告不聽步驟。
     *
     * @param tableState 牌山已摸完的桌況。
     * @param ruleModule 目前對局使用的規則模組。
     * @return 有可以宣告不聽的玩家時為選擇步驟，否則為 null。
     */
    private fun notenDeclarationStep(tableState: TableState, ruleModule: MahjongRuleModule<*>): RuleDecisionStep? {
        val riichiModule = ruleModule as? RiichiFamilyRuleModule<*> ?: return null
        val tenpaiIds = riichiModule.declareExhaustiveDraw(tableState, emptySet()).tenpaiPlayerIds
        val choices = RuleDecisionInputSpec.SingleChoice(listOf(REVEAL_TENPAI_OPTION, DECLARE_NOTEN_OPTION))
        val inputSpecs = tableState.activePlayers
            .filter { player -> player.id in tenpaiIds && (player.playerRuleState as? RiichiPlayerState)?.isRiichi != true }
            .associate { it.id to choices }
        if (inputSpecs.isEmpty()) return null
        return RuleDecisionStep(stepId = NOTEN_DECLARATION_STEP, stepIndex = 0, inputSpecsByPlayerId = inputSpecs)
    }

    companion object {
        /** 一般流局結算前選擇公開聽牌或宣告不聽的步驟 ID。 */
        val NOTEN_DECLARATION_STEP: String = MahjongCraftMetadata.id("debug_noten_declaration")

        /** 本局結算後所有玩家確認的步驟 ID。 */
        val ROUND_END_CONFIRMATION_STEP: String = MahjongCraftMetadata.id("debug_round_end_confirmation")

        /** 公開聽牌的選項 ID。 */
        val REVEAL_TENPAI_OPTION: String = MahjongCraftMetadata.id("debug_reveal_tenpai")

        /** 宣告不聽的選項 ID，與記錄下來的動作共用同一個 ID 與用語。 */
        val DECLARE_NOTEN_OPTION: String = DebugDeclareNotenAction.id

        /** 每位玩家的選擇秒數。 */
        private const val DECISION_SECONDS = 15
    }
}
