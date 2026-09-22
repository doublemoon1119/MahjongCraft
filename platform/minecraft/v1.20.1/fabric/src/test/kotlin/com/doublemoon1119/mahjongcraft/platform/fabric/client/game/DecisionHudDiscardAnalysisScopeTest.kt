package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DiscardReadinessAnalysisDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HandReadinessAnalysisDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionActionTileSelectionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionPromptDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 動作選牌期間手牌分析來源切換的測試。 */
class DecisionHudDiscardAnalysisScopeTest {
    /** 進入立直選牌後使用立直專屬分析，離開後恢復一般分析。 */
    @Test
    fun `test riichi selection uses action-scoped analyses`() {
        val ordinary = analysis("ordinary")
        val riichi = analysis("riichi")
        val prompt = PlayerDecisionPromptDto(
            decisionKey = "decision",
            actions = listOf(
                PlayerDecisionActionDto(
                    token = "riichi",
                    actionId = "mahjongcraft:riichi",
                    tileSelection = PlayerDecisionActionTileSelectionDto(
                        eligibleTileIds = listOf("tile"),
                        minCount = 1,
                        maxCount = 1,
                        discardAnalyses = listOf(riichi),
                    ),
                ),
            ),
            discardAnalyses = listOf(ordinary),
        )

        assertEquals(listOf(riichi), prompt.discardAnalysesForAction("riichi"))
        assertEquals(listOf(ordinary), prompt.discardAnalysesForAction(null))
    }

    /** 未知動作或沒有專屬分析的動作仍安全沿用一般分析。 */
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
                    ),
                ),
            ),
            discardAnalyses = listOf(ordinary),
        )

        assertEquals(listOf(ordinary), prompt.discardAnalysesForAction("action"))
        assertEquals(listOf(ordinary), prompt.discardAnalysesForAction("missing"))
    }

    @Test
    fun `pointing at a legal discard prefers its projected analysis`() {
        val projected = analysis("tile")
        val current = HandReadinessAnalysisDto("mahjongcraft:riichi", emptyList())
        val prompt = PlayerDecisionPromptDto(
            decisionKey = "decision",
            ruleModuleId = "mahjongcraft:riichi",
            discardAnalyses = listOf(projected),
        )

        val selected = assertIs<HandAnalysisSelection.AfterDiscard>(
            selectHandAnalysis(prompt, null, "tile", current),
        )

        assertEquals(projected, selected.analysis)
    }

    @Test
    fun `current hand analysis is used without a legal pointed discard`() {
        val current = HandReadinessAnalysisDto("mahjongcraft:riichi", emptyList())

        val selected = assertIs<HandAnalysisSelection.Current>(
            selectHandAnalysis(null, null, null, current),
        )

        assertEquals(current, selected.analysis)
    }

    /** 建立只需辨識來源的最小捨牌分析。 */
    private fun analysis(id: String) = DiscardReadinessAnalysisDto(
        discardTileId = id,
        waitingTiles = emptyList(),
    )
}
