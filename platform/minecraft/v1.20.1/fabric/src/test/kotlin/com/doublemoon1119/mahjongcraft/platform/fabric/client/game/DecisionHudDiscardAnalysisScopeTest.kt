package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DiscardReadinessAnalysisDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HandReadinessAnalysisDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionActionDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionActionTileSelectionDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionPromptDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 動作選牌期間手牌分析來源切換的測試。 */
class DecisionHudDiscardAnalysisScopeTest {
    /** 選取立直牌張時使用該動作專屬的捨牌分析，未選取動作時沿用一般分析。 */
    @Test
    fun `test riichi selection uses action-scoped analyses`() {
        val ordinary = analysis("ordinary")
        val riichi = analysis("riichi")
        val prompt = PlayerDecisionPromptDto(
            decisionKey = "decision",
            actions = listOf(
                PlayerDecisionActionDto(
                    token = "riichi",
                    actionId = RiichiGameAction.Riichi.id,
                    tileSelection = PlayerDecisionActionTileSelectionDto(
                        eligibleTileIds = listOf("tile"),
                        minCount = 1,
                        maxCount = 1,
                        discardAnalyses = listOf(riichi),
                    ),
                    referenceTileAssetKey = null,
                    previewTileAssetKeys = emptyList(),
                    claimedTileIndex = null,
                ),
            ),
            discardAnalyses = listOf(ordinary),
            ruleModuleId = null,
            triggerTileAssetKey = null,
            triggerPlayerId = null,
            triggerPlayerName = null,
            triggerPlayerRelation = null,
            triggerActionId = null,
            ruleDecision = null,
        )

        assertEquals(listOf(riichi), prompt.discardAnalysesForAction("riichi"))
        assertEquals(listOf(ordinary), prompt.discardAnalysesForAction(null))
    }

    /** 未知動作或沒有專屬分析時沿用 prompt 的一般分析。 */
    @Test
    fun `test absent action analyses fall back to prompt analyses`() {
        val ordinary = analysis("ordinary")
        val prompt = PlayerDecisionPromptDto(
            decisionKey = "decision",
            actions = listOf(
                PlayerDecisionActionDto(
                    token = "action",
                    actionId = "mahjongcraft:action",
                    tileSelection = PlayerDecisionActionTileSelectionDto(
                        eligibleTileIds = listOf("tile"),
                        minCount = 1,
                        maxCount = 1,
                        discardAnalyses = emptyList(),
                    ),
                    referenceTileAssetKey = null,
                    previewTileAssetKeys = emptyList(),
                    claimedTileIndex = null,
                ),
            ),
            discardAnalyses = listOf(ordinary),
            ruleModuleId = null,
            triggerTileAssetKey = null,
            triggerPlayerId = null,
            triggerPlayerName = null,
            triggerPlayerRelation = null,
            triggerActionId = null,
            ruleDecision = null,
        )

        assertEquals(listOf(ordinary), prompt.discardAnalysesForAction("action"))
        assertEquals(listOf(ordinary), prompt.discardAnalysesForAction("missing"))
    }

    /** 指向合法捨牌時優先選取該牌的投影分析。 */
    @Test
    fun `pointing at a legal discard prefers its projected analysis`() {
        val projected = analysis("tile")
        val current = HandReadinessAnalysisDto("mahjongcraft:riichi", emptyList(), statusIndicatorId = null)
        val prompt = PlayerDecisionPromptDto(
            decisionKey = "decision",
            ruleModuleId = "mahjongcraft:riichi",
            discardAnalyses = listOf(projected),
            actions = emptyList(),
            triggerTileAssetKey = null,
            triggerPlayerId = null,
            triggerPlayerName = null,
            triggerPlayerRelation = null,
            triggerActionId = null,
            ruleDecision = null,
        )

        val selected = assertIs<HandAnalysisSelection.AfterDiscard>(
            selectHandAnalysis(prompt, null, "tile", current),
        )

        assertEquals(projected, selected.analysis)
    }

    /** 沒有合法指向牌時使用目前手牌分析。 */
    @Test
    fun `current hand analysis is used without a legal pointed discard`() {
        val current = HandReadinessAnalysisDto("mahjongcraft:riichi", emptyList(), statusIndicatorId = null)

        val selected = assertIs<HandAnalysisSelection.Current>(
            selectHandAnalysis(null, null, null, current),
        )

        assertEquals(current, selected.analysis)
    }

    /**
     * 建立只需辨識來源的最小捨牌分析。
     *
     * @param id 作為來源識別的牌 ID。
     * @return 不含等待牌與狀態指示的捨牌分析。
     */
    private fun analysis(id: String) = DiscardReadinessAnalysisDto(
        discardTileId = id,
        waitingTiles = emptyList(),
        statusIndicatorId = null,
    )
}
