package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.decision

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BuiltInMinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.BundledRiichiMinecraftExtension
import kotlin.test.Test
import kotlin.test.assertNotNull

/** 驗證 `decision_hud` 預覽情境使用正式對局登記過的動作。 */
class DecisionHudPreviewTest {
    private val vocabulary = GameActionVocabularyRegistryImpl().apply {
        BuiltInMinecraftMahjongExtension.registerGameActionVocabulary(this)
        BundledRiichiMinecraftExtension.registerGameActionVocabulary(this)
    }

    /** 每個預覽情境的每張動作卡都對得上已登記的日麻動作用語。 */
    @Test
    fun `every preview action is a registered riichi action`() {
        FabricDebugDecisionCommand.DecisionHudPreview.entries.forEach { preview ->
            preview.prompt(decisionKey = "preview", analysisTileId = null).actions.forEach { action ->
                assertNotNull(
                    vocabulary.find(BuiltInRuleModuleIds.RIICHI, action.actionId),
                    "${preview.commandName} uses an unregistered action ${action.actionId}",
                )
            }
        }
    }
}
