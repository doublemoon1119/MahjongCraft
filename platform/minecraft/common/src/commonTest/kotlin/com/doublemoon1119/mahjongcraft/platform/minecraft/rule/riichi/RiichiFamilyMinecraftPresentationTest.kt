package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RIICHI_GAME_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardReadinessAnalyzer
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.BuiltInGameActionVoiceSoundIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundPresentationRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropDescriberRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 驗證日麻呈現可以綁定到任意規則模組 ID。 */
class RiichiFamilyMinecraftPresentationTest {
    /** 立直與九種九牌一定登記；拔北只在規則可以拔北時登記。 */
    @Test
    fun `pull north wording is registered only when the rule allows it`() {
        val registry = GameActionVocabularyRegistryImpl().apply {
            RiichiFamilyMinecraftPresentation.registerGameActionVocabulary(this, VARIANT_ID, includesPullNorth = true)
            RiichiFamilyMinecraftPresentation.registerGameActionVocabulary(this, OTHER_VARIANT_ID, includesPullNorth = false)
        }

        assertNotNull(registry.find(VARIANT_ID, RiichiGameAction.Riichi.id))
        assertNotNull(registry.find(VARIANT_ID, RiichiGameAction.PullNorth.id))
        assertNotNull(registry.find(OTHER_VARIANT_ID, RiichiGameAction.Riichi.id))
        assertNull(registry.find(OTHER_VARIANT_ID, RiichiGameAction.PullNorth.id))
    }

    /** 振聽、語音、桌面物件、牌面與結算模板都綁定到登記的規則模組 ID，其他 ID 不受影響。 */
    @Test
    fun `bindings follow the registered rule module id`() {
        val statuses = DecisionStatusDisplayNameRegistryImpl().apply { RiichiFamilyMinecraftPresentation.registerDecisionStatusDisplayNames(this, VARIANT_ID) }
        val sounds = GameActionSoundPresentationRegistryImpl().apply { RiichiFamilyMinecraftPresentation.registerGameActionSounds(this, VARIANT_ID) }
        val props = TablePropDescriberRegistryImpl().apply { RiichiFamilyMinecraftPresentation.registerTableProps(this, VARIANT_ID) }
        val tiles = MinecraftTileAssetRegistryImpl().apply { RiichiFamilyMinecraftPresentation.registerTileAppearance(this, VARIANT_ID) }
        val templates = WinSettlementPresentationTemplateRegistryImpl().apply {
            register(RiichiWinSettlementTemplates.RIICHI)
            RiichiFamilyMinecraftPresentation.registerWinSettlementTemplate(this, VARIANT_ID)
        }

        assertNotNull(statuses.find(VARIANT_ID, RiichiDiscardReadinessAnalyzer.StatusIds.PERMANENT_FURITEN))
        assertNull(statuses.find(OTHER_VARIANT_ID, RiichiDiscardReadinessAnalyzer.StatusIds.PERMANENT_FURITEN))
        assertEquals(BuiltInGameActionVoiceSoundIds.RIICHI, sounds.find(VARIANT_ID, RIICHI_GAME_ACTION)?.soundId)
        assertSame(RiichiTableProps, props.find(VARIANT_ID))
        assertNull(props.find(OTHER_VARIANT_ID))
        assertTrue(VARIANT_ID in tiles.ruleAppearanceBindingKeys)
        assertEquals(RiichiWinSettlementTemplates.RIICHI.key, templates.findTemplateForRule(VARIANT_ID)?.key)
    }

    private companion object {
        /** 沿用日麻呈現的規則模組。 */
        const val VARIANT_ID: String = "test:riichi_variant"

        /** 另一個沿用日麻呈現、不能拔北的規則模組。 */
        const val OTHER_VARIANT_ID: String = "test:other_riichi_variant"
    }
}
