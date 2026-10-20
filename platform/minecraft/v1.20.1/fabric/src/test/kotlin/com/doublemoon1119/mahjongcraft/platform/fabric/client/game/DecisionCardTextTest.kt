package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabulary
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionActionDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionPromptDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.preparation.RoundPreparationDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistryImpl
import net.minecraft.text.TranslatableTextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** 驗證第三方動作卡的選填文字不影響命令提交。 */
class DecisionCardTextTest {
    /** 第三方登記的中央說明與提示進入卡片，沒有登記的內建動作維持空值。 */
    @Test
    fun `carries optional card text without changing action submission`() {
        val resolver = DecisionTextResolver(
            actionVocabulary = GameActionVocabularyRegistryImpl().apply {
                register(
                    GameActionVocabularyDefinition(
                        ruleModuleId = BuiltInRuleModuleIds.RIICHI,
                        actionId = "sample:declare",
                        vocabulary = GameActionVocabulary(
                            labelKey = "sample.declare",
                            cardBodyKey = "sample.declare.body",
                            cardHintKey = "sample.declare.hint",
                        ),
                    ),
                )
            },
            decisionStatusDisplayNames = DecisionStatusDisplayNameRegistryImpl(),
            exhaustiveDrawReasonDisplayNames = ExhaustiveDrawReasonDisplayNameRegistryImpl(),
            roundPreparationDisplayNames = RoundPreparationDisplayNameRegistryImpl(),
        )
        val entries = decisionEntriesFrom(resolver, promptOf("sample:declare", "mahjongcraft:ron"))
        val custom = entries.single { (it.label.content as? TranslatableTextContent)?.key == "sample.declare" }
        assertEquals("sample.declare.body", (custom.cardBody?.content as TranslatableTextContent).key)
        assertEquals("sample.declare.hint", (custom.cardHint?.content as TranslatableTextContent).key)
        assertEquals("token-sample:declare", assertIs<DecisionEntryIntent.SubmitAction>(custom.intent).token)
        val builtIn = entries.single { it !== custom }
        assertNull(builtIn.cardBody)
        assertNull(builtIn.cardHint)
    }

    /**
     * 建立只含指定動作的 prompt。
     *
     * @param actionIds 要放入 prompt 的動作 ID。
     * @return 使用測試決策鍵與指定動作的 prompt。
     */
    private fun promptOf(vararg actionIds: String) = PlayerDecisionPromptDto(
        decisionKey = "card-text-decision",
        ruleModuleId = BuiltInRuleModuleIds.RIICHI,
        actions = actionIds.map {
            PlayerDecisionActionDto(
                token = "token-$it",
                actionId = it,
                referenceTileAssetKey = null,
                previewTileAssetKeys = emptyList(),
                claimedTileIndex = null,
                tileSelection = null,
            )
        },
        triggerTileAssetKey = null,
        triggerPlayerId = null,
        triggerPlayerName = null,
        triggerPlayerRelation = null,
        triggerActionId = null,
        preparation = null,
        discardAnalyses = emptyList(),
    )
}
