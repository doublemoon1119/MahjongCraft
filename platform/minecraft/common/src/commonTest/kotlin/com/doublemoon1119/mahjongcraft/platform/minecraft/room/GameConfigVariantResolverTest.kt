package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 驗證玩法的列出、判斷、套用與登記檢查。 */
class GameConfigVariantResolverTest {
    /** 沒有登記玩法的規則只有標準玩法，任何設定都符合它。 */
    @Test
    fun `rule without variants always matches standard`() {
        val definition = definition(variants = emptyList())

        assertEquals(listOf(GameConfigVariantDefinition.STANDARD_ID), GameConfigVariantResolver.options(definition).map { it.id })
        val changed = GameConfig(RiichiRuleConfig(allowOpenTanyao = false, useLocalYaku = true, minimumWinConstraint = 4))
        assertEquals(GameConfigVariantDefinition.STANDARD_ID, GameConfigVariantResolver.current(definition, changed)?.id)
    }

    /** 標準玩法排在最前面，其後依登記順序排列。 */
    @Test
    fun `options list standard first then registered order`() {
        val definition = definition(variants = listOf(closedHandVariant(), localVariant()))

        assertEquals(
            expected = listOf(GameConfigVariantDefinition.STANDARD_ID, "test:closed_hand", "test:local"),
            actual = GameConfigVariantResolver.options(definition).map { it.id },
        )
    }

    /** 用來判斷玩法的欄位符合哪個玩法就顯示哪個；玩法沒有指定的欄位必須是規則預設值。 */
    @Test
    fun `current variant follows the defining fields`() {
        val definition = definition(variants = listOf(closedHandVariant(), localVariant()))

        assertEquals(GameConfigVariantDefinition.STANDARD_ID, GameConfigVariantResolver.current(definition, GameConfig(RiichiRuleConfig()))?.id)
        assertEquals(
            expected = "test:closed_hand",
            actual = GameConfigVariantResolver.current(definition, GameConfig(RiichiRuleConfig(allowOpenTanyao = false)))?.id,
        )
        assertEquals(
            expected = "test:local",
            actual = GameConfigVariantResolver.current(definition, GameConfig(RiichiRuleConfig(useLocalYaku = true)))?.id,
        )
    }

    /** 用來判斷玩法的欄位不符合任何玩法時沒有目前玩法，畫面顯示自訂。 */
    @Test
    fun `defining fields matching no variant resolve to custom`() {
        val definition = definition(variants = listOf(closedHandVariant(), localVariant()))
        val mixed = GameConfig(RiichiRuleConfig(allowOpenTanyao = false, useLocalYaku = true))

        assertNull(GameConfigVariantResolver.current(definition, mixed))
    }

    /** 改動玩法沒有用到的欄位不影響目前玩法。 */
    @Test
    fun `other fields do not affect the current variant`() {
        val definition = definition(variants = listOf(closedHandVariant()))
        val config = GameConfig(RiichiRuleConfig(allowOpenTanyao = false, minimumWinConstraint = 4))

        assertEquals("test:closed_hand", GameConfigVariantResolver.current(definition, config)?.id)
    }

    /** 套用玩法只改用來判斷玩法的欄位，其他設定維持原值；玩法沒有指定的欄位回到規則預設值。 */
    @Test
    fun `applying a variant only changes the defining fields`() {
        val definition = definition(variants = listOf(closedHandVariant(), localVariant()))
        val start = GameConfig(RiichiRuleConfig(useLocalYaku = true, minimumWinConstraint = 4))

        val applied = GameConfigVariantResolver.apply(definition, start, "test:closed_hand")

        assertEquals(RiichiRuleConfig(allowOpenTanyao = false, useLocalYaku = false, minimumWinConstraint = 4), applied.ruleConfig)
        assertEquals(start.flowConfig, applied.flowConfig)
        val standard = GameConfigVariantResolver.apply(definition, applied, GameConfigVariantDefinition.STANDARD_ID)
        assertEquals(RiichiRuleConfig(minimumWinConstraint = 4), standard.ruleConfig)
        assertFailsWith<IllegalArgumentException> { GameConfigVariantResolver.apply(definition, start, "test:missing") }
    }

    /** 玩法只能指定本規則宣告的可編輯欄位與合法的值，也不能使用保留或重複的 ID。 */
    @Test
    fun `invalid variants are rejected by the definition`() {
        assertFailsWith<IllegalArgumentException> { definition(variants = listOf(variant("test:empty", emptyMap()))) }
        assertFailsWith<IllegalArgumentException> {
            definition(variants = listOf(variant("test:unknown", mapOf("test:missing" to GameConfigPresentationValue.BooleanValue(true)))))
        }
        assertFailsWith<IllegalArgumentException> {
            definition(variants = listOf(variant("test:read_only", mapOf(READ_ONLY to GameConfigPresentationValue.BooleanValue(true)))))
        }
        assertFailsWith<IllegalArgumentException> {
            definition(variants = listOf(variant("test:wrong_kind", mapOf(OPEN_TANYAO to GameConfigPresentationValue.IntegerValue(1)))))
        }
        val reservedId = variant(GameConfigVariantDefinition.STANDARD_ID, mapOf(OPEN_TANYAO to GameConfigPresentationValue.BooleanValue(false)))
        assertFailsWith<IllegalArgumentException> { definition(variants = listOf(reservedId)) }
        assertFailsWith<IllegalArgumentException> { definition(variants = listOf(closedHandVariant(), closedHandVariant())) }
    }

    /** 與標準玩法或另一個玩法指定相同值的玩法無法區分，登記時拒絕。 */
    @Test
    fun `registry rejects variants that cannot be told apart`() {
        val sameAsStandard = variant("test:same_as_standard", mapOf(OPEN_TANYAO to GameConfigPresentationValue.BooleanValue(true)))
        val duplicate = variant("test:duplicate", mapOf(OPEN_TANYAO to GameConfigPresentationValue.BooleanValue(false)))

        assertFailsWith<IllegalArgumentException> {
            GameConfigPresentationRegistryImpl(RuleDecisionResolverRegistry()).register(definition(variants = listOf(sameAsStandard)))
        }
        assertFailsWith<IllegalArgumentException> {
            GameConfigPresentationRegistryImpl(RuleDecisionResolverRegistry())
                .register(definition(variants = listOf(closedHandVariant(), duplicate)))
        }
    }

    /** 登記後查詢到的定義保留玩法，且不受登記時傳入的集合之後被修改影響。 */
    @Test
    fun `registry keeps a detached copy of the variants`() {
        val values = mutableMapOf<String, GameConfigPresentationValue>(OPEN_TANYAO to GameConfigPresentationValue.BooleanValue(false))
        val registry = GameConfigPresentationRegistryImpl(RuleDecisionResolverRegistry())
        registry.register(definition(variants = listOf(variant("test:closed_hand", values))))
        values[LOCAL_YAKU] = GameConfigPresentationValue.BooleanValue(true)

        val found = assertNotNull(registry.find("test:rule"))

        assertEquals(setOf(OPEN_TANYAO), found.variants.single().fieldValues.keys)
        assertEquals(
            expected = "test:closed_hand",
            actual = GameConfigVariantResolver.current(found, GameConfig(RiichiRuleConfig(allowOpenTanyao = false)))?.id,
        )
    }

    /** 關閉副露斷么的測試玩法。 */
    private fun closedHandVariant() = variant("test:closed_hand", mapOf(OPEN_TANYAO to GameConfigPresentationValue.BooleanValue(false)))

    /** 開啟古役的測試玩法。 */
    private fun localVariant() = variant("test:local", mapOf(LOCAL_YAKU to GameConfigPresentationValue.BooleanValue(true)))

    /**
     * 建立測試玩法。
     *
     * @param id 玩法 ID。
     * @param values 玩法指定的欄位與值。
     * @return 使用測試翻譯鍵的玩法。
     */
    private fun variant(id: String, values: Map<String, GameConfigPresentationValue>) = GameConfigVariantDefinition(
        id = id,
        nameTranslationKey = "test.variant",
        descriptionTranslationKey = "test.variant.description",
        fieldValues = values,
    )

    /**
     * 建立有兩個可編輯開關、一個可編輯數值與一個唯讀欄位的測試規則定義。
     *
     * @param variants 規則提供的玩法。
     * @return 測試規則的設定呈現定義。
     */
    private fun definition(variants: List<GameConfigVariantDefinition>) = GameConfigPresentationDefinition(
        ruleModuleId = "test:rule",
        descriptionTranslationKey = "test.rule.description",
        selectable = true,
        defaultRuleConfig = ::RiichiRuleConfig,
        categories = listOf(GameConfigCategoryDefinition(CATEGORY, "test.category")),
        fields = listOf(
            BuiltInGameConfigFields.boolField(
                id = OPEN_TANYAO,
                category = CATEGORY,
                nameKey = "test.open_tanyao",
                descriptionKey = "test.open_tanyao.description",
                read = { (it.ruleConfig as RiichiRuleConfig).allowOpenTanyao },
                update = { config, enabled -> config.copy(ruleConfig = (config.ruleConfig as RiichiRuleConfig).copy(allowOpenTanyao = enabled)) },
            ),
            BuiltInGameConfigFields.boolField(
                id = LOCAL_YAKU,
                category = CATEGORY,
                nameKey = "test.local_yaku",
                descriptionKey = "test.local_yaku.description",
                read = { (it.ruleConfig as RiichiRuleConfig).useLocalYaku },
                update = { config, enabled -> config.copy(ruleConfig = (config.ruleConfig as RiichiRuleConfig).copy(useLocalYaku = enabled)) },
            ),
            BuiltInGameConfigFields.intField(
                id = "test:minimum_win",
                category = CATEGORY,
                nameKey = "test.minimum_win",
                descriptionKey = "test.minimum_win.description",
                minimum = 1,
                maximum = 13,
                read = { (it.ruleConfig as RiichiRuleConfig).minimumWinConstraint },
                update = { config, number -> config.copy(ruleConfig = (config.ruleConfig as RiichiRuleConfig).copy(minimumWinConstraint = number)) },
            ),
            GameConfigFieldDefinition(
                id = READ_ONLY,
                categoryId = CATEGORY,
                nameTranslationKey = "test.read_only",
                descriptionTranslationKey = "test.read_only.description",
                editor = GameConfigEditorSpec.BooleanToggle,
                isEditable = false,
                read = { _: GameConfig -> GameConfigPresentationValue.BooleanValue(true) },
            ),
        ),
        variants = variants,
    )

    private companion object {
        /** 測試欄位的分類。 */
        const val CATEGORY = "test:category"

        /** 副露斷么開關欄位。 */
        const val OPEN_TANYAO = "test:open_tanyao"

        /** 古役開關欄位。 */
        const val LOCAL_YAKU = "test:local_yaku"

        /** 唯讀欄位。 */
        const val READ_ONLY = "test:read_only"
    }
}
