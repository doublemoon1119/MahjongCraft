package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BundledMinecraftMahjongExtensions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 驗證遊戲設定呈現 schema 的可編輯性、欄位契約與 registry 凍結。 */
class GameConfigPresentationRegistryTest {
    /** 內建規則提供相應選項，台麻目前明確標示為不可選。 */
    @Test
    fun `built-in schemas expose editable Riichi and unavailable Taiwan rules`() {
        val registry = GameConfigPresentationRegistryImpl()

        BundledMinecraftMahjongExtensions.all.forEach { it.registerGameConfigPresentations(registry) }

        val riichi = assertNotNull(registry.find("mahjongcraft:riichi"))
        val taiwan = assertNotNull(registry.find("mahjongcraft:taiwan"))
        assertTrue(riichi.selectable)
        assertTrue(riichi.fields.any { it.id == "mahjongcraft:initial_score" && it.isEditable })
        assertEquals(
            listOf("mahjongcraft:red_dora_0", "mahjongcraft:red_dora_3", "mahjongcraft:red_dora_4"),
            (riichi.fields.single { it.id == "mahjongcraft:red_dora_count" }.editor as GameConfigEditorSpec.SingleChoice).optionIds,
        )
        assertEquals(
            "mahjongcraft.room.config.option.red_dora_0",
            (riichi.fields.single { it.id == "mahjongcraft:red_dora_count" }.editor as GameConfigEditorSpec.SingleChoice)
                .translationKeyFor("mahjongcraft:red_dora_0"),
        )
        assertTrue(riichi.categories.none { it.id == "mahjongcraft:technical" })
        val handVisibility = riichi.fields.single { it.id == "mahjongcraft:spectator_hand_visibility" }
        assertEquals(false, handVisibility.isEnabled(GameConfig(RiichiRuleConfig()).copy(flowConfig = GameConfig(RiichiRuleConfig()).flowConfig.copy(spectatingPolicy = SpectatingPolicy.DISABLED))))
        assertEquals(false, taiwan.selectable)
        assertNotNull(taiwan.unavailableReasonTranslationKey)
    }

    /** 可編輯欄位必須有 updater，重複規則 ID 不可登記。 */
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

    /** malformed config、field 與 option ID 會在 schema 建立時拒絕。 */
    @Test
    fun `malformed config IDs are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            GameConfigEditorSpec.SingleChoice(listOf("test:option", "Test:Option"))
        }
        assertFailsWith<IllegalArgumentException> {
            GameConfigFieldDefinition(
                id = "test:Field",
                categoryId = "test:category",
                nameTranslationKey = "test.field",
                descriptionTranslationKey = "test.field.description",
                editor = GameConfigEditorSpec.BooleanToggle,
                isEditable = false,
                read = { GameConfigPresentationValue.BooleanValue(true) },
            )
        }
    }

    /** 已凍結的 registry 拒絕後續登記。 */
    @Test
    fun `frozen registry rejects later registration`() {
        val registry = GameConfigPresentationRegistryImpl()
        registry.freeze()

        assertFailsWith<IllegalStateException> { registry.register(minimalDefinition()) }
    }

    /** 登記與查詢都不會暴露外部可變的選項 ID 或翻譯鍵集合。 */
    @Test
    fun `registry detaches single choice translation mappings`() {
        val optionIds = mutableListOf("test:option")
        val translationKeys = mutableMapOf("test:option" to "test.option")
        val definition = minimalDefinition().copy(
            fields = listOf(
                GameConfigFieldDefinition(
                    id = "test:choice",
                    categoryId = "test:category",
                    nameTranslationKey = "test.choice",
                    descriptionTranslationKey = "test.choice.description",
                    editor = GameConfigEditorSpec.SingleChoice(optionIds, translationKeys),
                    isEditable = false,
                    read = { GameConfigPresentationValue.ChoiceValue("test:option") },
                ),
            ),
        )
        val registry = GameConfigPresentationRegistryImpl()
        registry.register(definition)
        optionIds += "test:other"
        translationKeys["test:option"] = "test.changed"

        val registered = assertNotNull(registry.find("test:rule"))
        val editor = registered.fields.single().editor as GameConfigEditorSpec.SingleChoice
        assertEquals(listOf("test:option"), editor.optionIds)
        assertEquals("test.option", editor.translationKeyFor("test:option"))
    }

    /**
     * 建立可登記的最小唯讀設定 schema。
     *
     * @return 含一個唯讀布林欄位的設定定義。
     */
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
