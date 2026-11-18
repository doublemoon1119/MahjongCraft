package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證玩法的列出、判斷、套用、鎖定與登記檢查。 */
class GameConfigVariantResolverTest {
    /** 沒有登記玩法的規則只有標準玩法，任何設定都符合它，所有欄位都顯示。 */
    @Test
    fun `rule without variants always matches standard`() {
        val definition = definition(variants = emptyList())

        assertEquals(listOf(GameConfigVariantDefinition.STANDARD_ID), GameConfigVariantResolver.options(definition).map { it.id })
        val changed = GameConfig(RiichiRuleConfig(allowOpenTanyao = false, useLocalYaku = true, minimumWinConstraint = 4))
        assertEquals(GameConfigVariantDefinition.STANDARD_ID, GameConfigVariantResolver.current(definition, changed).id)
        assertEquals(definition.fields, GameConfigVariantResolver.visibleFields(definition))
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

    /** 識別欄位符合哪個玩法就是哪個玩法；識別欄位不顯示在設定頁。 */
    @Test
    fun `current variant follows the identifying fields`() {
        val definition = definition(variants = listOf(closedHandVariant(), localVariant()))

        assertEquals(GameConfigVariantDefinition.STANDARD_ID, GameConfigVariantResolver.current(definition, GameConfig(RiichiRuleConfig())).id)
        assertEquals(
            expected = "test:closed_hand",
            actual = GameConfigVariantResolver.current(definition, GameConfig(RiichiRuleConfig(allowOpenTanyao = false))).id,
        )
        assertEquals(
            expected = "test:local",
            actual = GameConfigVariantResolver.current(definition, GameConfig(RiichiRuleConfig(useLocalYaku = true))).id,
        )
        assertEquals(listOf(MINIMUM_WIN, READ_ONLY), GameConfigVariantResolver.visibleFields(definition).map { it.id })
    }

    /** 識別欄位不符合任何玩法的設定對不上玩法：查詢不到，判斷目前玩法時拋出。 */
    @Test
    fun `identifying fields matching no variant are inconsistent`() {
        val definition = definition(variants = listOf(closedHandVariant(), localVariant()))
        val mixed = GameConfig(RiichiRuleConfig(allowOpenTanyao = false, useLocalYaku = true))

        assertNull(GameConfigVariantResolver.find(definition, mixed))
        assertFalse(GameConfigVariantResolver.isConsistent(definition, mixed))
        assertFailsWith<IllegalStateException> { GameConfigVariantResolver.current(definition, mixed) }
    }

    /** 改動識別欄位以外的欄位不影響目前玩法。 */
    @Test
    fun `other fields do not affect the current variant`() {
        val definition = definition(variants = listOf(closedHandVariant()))
        val config = GameConfig(RiichiRuleConfig(allowOpenTanyao = false, minimumWinConstraint = 4))

        assertEquals("test:closed_hand", GameConfigVariantResolver.current(definition, config).id)
    }

    /** 套用玩法只改識別欄位與鎖定欄位，其他設定維持原值。 */
    @Test
    fun `applying a variant only changes its identifying and locked fields`() {
        val definition = definition(variants = listOf(closedHandVariant(locksMinimumWin = true), localVariant()))
        val start = GameConfig(RiichiRuleConfig(useLocalYaku = true, minimumWinConstraint = 4, redDoraCount = 0))

        val applied = GameConfigVariantResolver.apply(definition, start, "test:closed_hand")

        assertEquals(RiichiRuleConfig(allowOpenTanyao = false, useLocalYaku = false, minimumWinConstraint = 2, redDoraCount = 0), applied.ruleConfig)
        assertEquals(start.flowConfig, applied.flowConfig)
        assertFailsWith<IllegalArgumentException> { GameConfigVariantResolver.apply(definition, start, "test:missing") }
    }

    /** 離開鎖定某欄位的玩法時，新玩法不鎖定的欄位還原為規則預設值，其他設定維持原值。 */
    @Test
    fun `leaving a variant restores the fields it locked to their defaults`() {
        val definition = definition(variants = listOf(closedHandVariant(locksMinimumWin = true), localVariant()))
        val closed = GameConfig(RiichiRuleConfig(allowOpenTanyao = false, minimumWinConstraint = 2, redDoraCount = 0))

        val standard = GameConfigVariantResolver.apply(definition, closed, GameConfigVariantDefinition.STANDARD_ID)
        val local = GameConfigVariantResolver.apply(definition, closed, "test:local")

        assertEquals(RiichiRuleConfig(redDoraCount = 0), standard.ruleConfig)
        assertEquals(RiichiRuleConfig(useLocalYaku = true, redDoraCount = 0), local.ruleConfig)
        val again = GameConfigVariantResolver.apply(definition, closed, "test:closed_hand")
        assertEquals(closed, again)
    }

    /** 鎖定欄位只在鎖定它的玩法下鎖定，且必須是固定的值設定才對得上玩法。 */
    @Test
    fun `locked fields apply only to their variant`() {
        val definition = definition(variants = listOf(closedHandVariant(locksMinimumWin = true)))
        val closed = GameConfig(RiichiRuleConfig(allowOpenTanyao = false, minimumWinConstraint = 2))

        assertEquals(LOCK_REASON, GameConfigVariantResolver.lockedField(definition, closed, MINIMUM_WIN)?.reasonTranslationKey)
        assertNull(GameConfigVariantResolver.lockedField(definition, GameConfig(RiichiRuleConfig()), MINIMUM_WIN))
        assertTrue(GameConfigVariantResolver.isConsistent(definition, closed))
        assertFalse(GameConfigVariantResolver.isConsistent(definition, GameConfig(RiichiRuleConfig(allowOpenTanyao = false, minimumWinConstraint = 3))))
        assertTrue(GameConfigVariantResolver.isConsistent(definition, GameConfig(RiichiRuleConfig(minimumWinConstraint = 3))))
        assertEquals(setOf(OPEN_TANYAO, LOCAL_YAKU, MINIMUM_WIN), GameConfigVariantResolver.variantFieldIds(definition))
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
        val reservedId = variant(GameConfigVariantDefinition.STANDARD_ID, identifying(openTanyao = false, localYaku = false))
        assertFailsWith<IllegalArgumentException> { definition(variants = listOf(reservedId)) }
        assertFailsWith<IllegalArgumentException> { definition(variants = listOf(closedHandVariant(), closedHandVariant())) }
    }

    /** 同一規則的玩法必須指定同一組識別欄位；識別欄位不能同時鎖定，鎖定欄位也必須是可編輯欄位的合法值。 */
    @Test
    fun `identifying and locked fields must follow the variant rules`() {
        val onlyOpenTanyao = variant("test:only_open_tanyao", mapOf(OPEN_TANYAO to GameConfigPresentationValue.BooleanValue(false)))
        assertFailsWith<IllegalArgumentException> { definition(variants = listOf(localVariant(), onlyOpenTanyao)) }
        assertFailsWith<IllegalArgumentException> {
            variant(
                id = "test:locks_identifying",
                identifyingValues = identifying(openTanyao = false, localYaku = false),
                lockedFields = mapOf(OPEN_TANYAO to GameConfigLockedField(GameConfigPresentationValue.BooleanValue(false), LOCK_REASON)),
            )
        }
        val locksUnknown = variant(
            id = "test:locks_unknown",
            identifyingValues = identifying(openTanyao = false, localYaku = false),
            lockedFields = mapOf("test:missing" to GameConfigLockedField(GameConfigPresentationValue.IntegerValue(1), LOCK_REASON)),
        )
        assertFailsWith<IllegalArgumentException> { definition(variants = listOf(locksUnknown)) }
        val locksOutOfRange = variant(
            id = "test:locks_out_of_range",
            identifyingValues = identifying(openTanyao = false, localYaku = false),
            lockedFields = mapOf(MINIMUM_WIN to GameConfigLockedField(GameConfigPresentationValue.IntegerValue(99), LOCK_REASON)),
        )
        assertFailsWith<IllegalArgumentException> { definition(variants = listOf(locksOutOfRange)) }
        assertFailsWith<IllegalArgumentException> { GameConfigLockedField(GameConfigPresentationValue.IntegerValue(1), " ") }
    }

    /** 與標準玩法或另一個玩法指定相同識別值的玩法無法區分，登記時拒絕。 */
    @Test
    fun `registry rejects variants that cannot be told apart`() {
        val sameAsStandard = variant("test:same_as_standard", identifying(openTanyao = true, localYaku = false))
        val duplicate = variant("test:duplicate", identifying(openTanyao = false, localYaku = false))

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
        val values = identifying(openTanyao = false, localYaku = false).toMutableMap()
        val registry = GameConfigPresentationRegistryImpl(RuleDecisionResolverRegistry())
        registry.register(definition(variants = listOf(variant("test:closed_hand", values))))
        values[MINIMUM_WIN] = GameConfigPresentationValue.IntegerValue(3)

        val found = assertNotNull(registry.find("test:rule"))

        assertEquals(setOf(OPEN_TANYAO, LOCAL_YAKU), found.variants.single().identifyingValues.keys)
        assertEquals(
            expected = "test:closed_hand",
            actual = GameConfigVariantResolver.current(found, GameConfig(RiichiRuleConfig(allowOpenTanyao = false))).id,
        )
    }

    /**
     * 關閉副露斷么的測試玩法。
     *
     * @param locksMinimumWin 是否把起胡番數鎖定為 2。
     * @return 測試玩法。
     */
    private fun closedHandVariant(locksMinimumWin: Boolean = false) = variant(
        id = "test:closed_hand",
        identifyingValues = identifying(openTanyao = false, localYaku = false),
        lockedFields = if (locksMinimumWin) {
            mapOf(MINIMUM_WIN to GameConfigLockedField(GameConfigPresentationValue.IntegerValue(2), LOCK_REASON))
        } else {
            emptyMap()
        },
    )

    /** 開啟古役的測試玩法。 */
    private fun localVariant() = variant("test:local", identifying(openTanyao = true, localYaku = true))

    /**
     * 建立兩個識別欄位的值。
     *
     * @param openTanyao 副露斷么開關的值。
     * @param localYaku 古役開關的值。
     * @return 識別欄位與值。
     */
    private fun identifying(openTanyao: Boolean, localYaku: Boolean): Map<String, GameConfigPresentationValue> = mapOf(
        OPEN_TANYAO to GameConfigPresentationValue.BooleanValue(openTanyao),
        LOCAL_YAKU to GameConfigPresentationValue.BooleanValue(localYaku),
    )

    /**
     * 建立測試玩法。
     *
     * @param id 玩法 ID。
     * @param identifyingValues 玩法的識別欄位與值。
     * @param lockedFields 玩法的鎖定欄位。
     * @return 使用測試翻譯鍵的玩法。
     */
    private fun variant(
        id: String,
        identifyingValues: Map<String, GameConfigPresentationValue>,
        lockedFields: Map<String, GameConfigLockedField> = emptyMap(),
    ) = GameConfigVariantDefinition(
        id = id,
        nameTranslationKey = "test.variant",
        descriptionTranslationKey = "test.variant.description",
        identifyingValues = identifyingValues,
        lockedFields = lockedFields,
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
                id = MINIMUM_WIN,
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

        /** 起胡番數欄位。 */
        const val MINIMUM_WIN = "test:minimum_win"

        /** 唯讀欄位。 */
        const val READ_ONLY = "test:read_only"

        /** 測試鎖定原因翻譯鍵。 */
        const val LOCK_REASON = "test.lock_reason"
    }
}
