package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.decision

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BuiltInMinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.BundledRiichiMinecraftExtension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** 驗證 `decision_hud` 預覽情境使用正式對局登記過的動作。 */
class DecisionHudPreviewTest {
    private val vocabulary = GameActionVocabularyRegistryImpl().apply {
        BuiltInMinecraftMahjongExtension.registerGameActionVocabulary(this)
        BundledRiichiMinecraftExtension.registerGameActionVocabulary(this)
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
}
