package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.decision

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.DebugMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BuiltInMinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.BundledRiichiMinecraftExtension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** 驗證 `decision_hud` 預覽使用內建及開發環境已登記的動作用語。 */
class DecisionHudPreviewTest {
    /** 預覽情境所需的內建動作用語登記表。 */
    private val vocabulary = GameActionVocabularyRegistryImpl().apply {
        BuiltInMinecraftMahjongExtension.registerGameActionVocabulary(this)
        BundledRiichiMinecraftExtension.registerGameActionVocabulary(this)
        DebugMahjongExtension(DebugWinRoundContinuationState()).registerGameActionVocabulary(this)
    }

    /** 每個預覽情境的每張動作卡都對得上該情境規則已登記的動作用語。 */
    @Test
    fun `every preview action is registered for its rule`() {
        FabricDebugDecisionCommand.DecisionHudPreview.entries.forEach { preview ->
            val prompt = preview.prompt(decisionKey = "preview", analysisTileId = null)
            prompt.actions.forEach { action ->
                assertNotNull(
                    vocabulary.find(prompt.ruleModuleId, action.actionId),
                    "${preview.commandName} uses an unregistered action ${action.actionId}",
                )
            }
        }
    }

    /** 拔北預覽使用三人日麻的動作用語，卡片顯示被拔出的北。 */
    @Test
    fun `pull north preview uses the three player rule`() {
        val prompt = FabricDebugDecisionCommand.DecisionHudPreview.PULL_NORTH.prompt(decisionKey = "preview", analysisTileId = null)

        assertEquals(BuiltInRuleModuleIds.RIICHI_THREE_PLAYER, prompt.ruleModuleId)
        assertEquals(listOf("north"), prompt.actions.single().previewTileAssetKeys)
        assertNotNull(vocabulary.find(prompt.ruleModuleId, prompt.actions.single().actionId)?.descriptionKey)
    }

    /** 單獨與混合預覽都使用同一張沒有牌張預覽的文字卡。 */
    @Test
    fun `text card preview shares its registered content with mixed preview`() {
        val standalone = FabricDebugDecisionCommand.DecisionHudPreview.TEXT_CARD.prompt("text", null).actions.single()
        val mixed = FabricDebugDecisionCommand.DecisionHudPreview.MIXED.prompt("mixed", null).actions
            .single { it.actionId == DebugDecisionActionIds.TEXT_CARD }
        assertEquals(standalone.actionId, mixed.actionId)
        assertEquals(emptyList(), standalone.previewTileAssetKeys)
        assertEquals(standalone.previewTileAssetKeys, mixed.previewTileAssetKeys)
        val display = assertNotNull(vocabulary.find(null, standalone.actionId))
        assertNotNull(display.cardBodyKey)
        assertNotNull(display.cardHintKey)
        assertNotNull(display.descriptionKey)
    }
}
