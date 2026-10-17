package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigCategoryDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigEditorSpec
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigFieldDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.ShowcasePalette
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.ShowcaseSound
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.ShowcaseVisualLayer
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 驗證集合的輸入與輸出均與登記內容隔離，不能繞過 freeze。 */
class RegistrationCollectionIsolationTest {
    /** 設定分類、欄位與巢狀選項均不共用呼叫端的可變集合。 */
    @Test
    fun `configuration registration owns nested collection snapshots`() {
        val options = mutableListOf("example:first", "example:second")
        val fields = mutableListOf(
            GameConfigFieldDefinition(
                id = "example:field",
                categoryId = "example:category",
                nameTranslationKey = "test.field",
                descriptionTranslationKey = "test.description",
                editor = GameConfigEditorSpec.SingleChoice(options),
                isEditable = false,
                read = { error("Registration must not invoke field callbacks") },
            ),
        )
        val categories = mutableListOf(
            GameConfigCategoryDefinition("example:category", "test.category"),
            GameConfigCategoryDefinition("example:other", "test.other"),
        )
        val registry = GameConfigPresentationRegistryImpl(
            RoundPreparationResolverRegistry(),
        )
        registry.register(
            definition = GameConfigPresentationDefinition(
                ruleModuleId = "example:rule",
                descriptionTranslationKey = "test.rule",
                selectable = true,
                defaultRuleConfig = { RiichiRuleConfig() },
                categories = categories,
                fields = fields,
            ),
        )
        val keys = registry.ruleModuleIds
        registry.freeze()
        options.clear()
        fields.clear()
        categories.clear()
        val first = assertNotNull(registry.find("example:rule"))
        assertEquals(3, first.categories.size)
        assertEquals(5, first.fields.size)
        val firstChoice = first.fields.single { it.id == "example:field" }.editor as GameConfigEditorSpec.SingleChoice
        assertEquals(listOf("example:first", "example:second"), firstChoice.optionIds)
        (first.categories as MutableList<*>).clear()
        (firstChoice.optionIds as MutableList<*>).clear()
        val second = assertNotNull(registry.find("example:rule"))
        assertEquals(3, second.categories.size)
        assertEquals(2, (second.fields.single { it.id == "example:field" }.editor as GameConfigEditorSpec.SingleChoice).optionIds.size)
        assertEquals(setOf("example:rule"), keys)
    }

    /** 演出視覺層與音效清單的輸入及查詢結果都無法改變已凍結的定義。 */
    @Test
    fun `showcase registration isolates caller and reader collections`() {
        val layers = ShowcaseVisualLayer.entries.toMutableSet()
        val sounds = mutableListOf(ShowcaseSound("example:first", 1), ShowcaseSound("example:second", 2))
        val registry = WinCelebrationShowcaseRegistryImpl()
        registry.register(
            definition = WinCelebrationShowcaseDefinition(
                cueKey = "example:cue",
                titleTranslationKey = "test.cue",
                titleImageResourceId = "example:textures/cue.png",
                palette = ShowcasePalette(-1, -1, -1),
                layers = layers,
                extraSounds = sounds,
            ),
        )
        registry.freeze()
        layers.clear()
        sounds.clear()
        val first = assertNotNull(registry.find("example:cue"))
        assertTrue(first.layers.isNotEmpty())
        assertEquals(2, first.extraSounds.size)
        (first.layers as MutableSet<*>).clear()
        (first.extraSounds as MutableList<*>).clear()
        val second = assertNotNull(registry.find("example:cue"))
        assertTrue(second.layers.isNotEmpty())
        assertEquals(2, second.extraSounds.size)
    }
}
