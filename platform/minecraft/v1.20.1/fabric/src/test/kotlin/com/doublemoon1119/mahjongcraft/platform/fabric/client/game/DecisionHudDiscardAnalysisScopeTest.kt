package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DiscardReadinessAnalysisDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionActionTileSelectionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionPromptDto
import kotlin.test.Test
import kotlin.test.assertEquals

/** 動作選牌期間打牌分析來源切換的測試。 */
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

    /** 建立只需辨識來源的最小捨牌分析。 */
    private fun analysis(id: String) = DiscardReadinessAnalysisDto(
        discardTileId = id,
        waitingTiles = emptyList(),
    )
}
