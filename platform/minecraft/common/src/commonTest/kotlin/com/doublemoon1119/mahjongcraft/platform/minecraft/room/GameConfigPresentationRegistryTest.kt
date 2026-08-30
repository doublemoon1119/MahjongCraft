package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GameConfigPresentationRegistryTest {
    @Test
    fun `built-in schemas expose editable Riichi and unavailable Taiwan rules`() {
        val registry = GameConfigPresentationRegistryImpl()

        registry.registerBuiltInGameConfigPresentations()

        val riichi = assertNotNull(registry.find("mahjongcraft:riichi"))
        val taiwan = assertNotNull(registry.find("mahjongcraft:taiwan"))
        assertTrue(riichi.selectable)
        assertTrue(riichi.fields.any { it.id == "mahjongcraft:initial_score" && it.isEditable })
        assertEquals(
            listOf("mahjongcraft:red_dora_0", "mahjongcraft:red_dora_3", "mahjongcraft:red_dora_4"),
            (riichi.fields.single { it.id == "mahjongcraft:red_dora_count" }.editor as GameConfigEditorSpec.SingleChoice).optionIds,
        )
        assertTrue(riichi.categories.none { it.id == "mahjongcraft:technical" })
        val handVisibility = riichi.fields.single { it.id == "mahjongcraft:spectator_hand_visibility" }
        assertEquals(false, handVisibility.isEnabled(GameConfig(RiichiRuleConfig()).copy(flowConfig = GameConfig(RiichiRuleConfig()).flowConfig.copy(spectatingPolicy = SpectatingPolicy.DISABLED))))
        assertEquals(false, taiwan.selectable)
        assertNotNull(taiwan.unavailableReasonTranslationKey)
    }

    @Test
    fun `editable fields require updater and duplicate IDs are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            GameConfigFieldDefinition(
                id = "test:field",
                categoryId = "test:category",
                nameTranslationKey = "test.field",
                descriptionTranslationKey = "test.field.description",
                editor = GameConfigEditorSpec.BooleanToggle,
                isEditable = true,
                read = { GameConfigPresentationValue.BooleanValue(true) },
            )
        }
        val registry = GameConfigPresentationRegistryImpl()
        val definition = minimalDefinition()
        registry.register(definition)
        assertFailsWith<IllegalArgumentException> { registry.register(definition) }
    }

    @Test
    fun `frozen registry rejects later registration`() {
        val registry = GameConfigPresentationRegistryImpl()
        registry.freeze()

        assertFailsWith<IllegalStateException> { registry.register(minimalDefinition()) }
    }

    private fun minimalDefinition() = GameConfigPresentationDefinition(
        ruleModuleId = "test:rule",
        descriptionTranslationKey = "test.rule.description",
        selectable = true,
        defaultRuleConfig = ::RiichiRuleConfig,
        categories = listOf(GameConfigCategoryDefinition("test:category", "test.category")),
        fields = listOf(
            GameConfigFieldDefinition(
                id = "test:field",
                categoryId = "test:category",
                nameTranslationKey = "test.field",
                descriptionTranslationKey = "test.field.description",
                editor = GameConfigEditorSpec.BooleanToggle,
                isEditable = false,
                read = { _: GameConfig -> GameConfigPresentationValue.BooleanValue(true) },
            ),
        ),
    )
}
