package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRoundPreparation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolution
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BundledMinecraftMahjongExtensions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 驗證遊戲設定呈現 schema 的可編輯性、欄位契約與 registry 凍結。 */
class GameConfigPresentationRegistryTest {
    /** 第三方規則未宣告流程欄位時，registry 仍會補上完整且不重複的共用欄位。 */
    @Test
    fun `third party definitions receive shared flow fields exactly once`() {
        val registry = GameConfigPresentationRegistryImpl(RoundPreparationResolverRegistry())

        registry.register(minimalDefinition())

        val definition = assertNotNull(registry.find("test:rule"))
        assertEquals(1, definition.categories.count { it.id == "mahjongcraft:flow" })
        assertEquals(
            listOf(
                "mahjongcraft:base_seconds",
                "mahjongcraft:reserve_seconds",
                "mahjongcraft:spectating_policy",
                "mahjongcraft:spectator_hand_visibility",
            ),
            definition.fields.filter { it.categoryId == "mahjongcraft:flow" }.map { it.id },
        )
    }

    /** 宣告有開局準備的第三方規則，才會取得可編輯的準備時間欄位。 */
    @Test
    fun `third party preparation rules receive editable preparation time`() {
        val registry = GameConfigPresentationRegistryImpl(preparationRegistry())

        registry.register(minimalDefinition())

        val definition = assertNotNull(registry.find("test:rule"))
        val preparation = definition.fields.single { it.id == "mahjongcraft:preparation_base_seconds" }
        val editor = preparation.editor as GameConfigEditorSpec.IntegerInput
        assertEquals(1, editor.minimum)
        assertEquals(3600, editor.maximum)
        val config = GameConfig(RiichiRuleConfig())
        val configWithPreparation = config.copy(
            flowConfig = config.flowConfig.copy(roundPreparation = RoundPreparationConfig(47)),
        )
        assertEquals(
            47,
            (preparation.read(configWithPreparation) as GameConfigPresentationValue.IntegerValue).number,
        )
    }

    /** 同一規則的準備開關改變時，只在啟用的設定中加入準備時間欄位。 */
    @Test
    fun `config aware preparation presentation follows the rule config`() {
        val registry = GameConfigPresentationRegistryImpl(
            RoundPreparationResolverRegistry().apply { register(ConfigAwarePreparationResolver) },
        )
        registry.register(minimalDefinition())

        val enabled = assertNotNull(registry.find("test:rule", RiichiRuleConfig(redDoraCount = 3)))
        val disabled = assertNotNull(registry.find("test:rule", RiichiRuleConfig(redDoraCount = 0)))

        assertTrue(enabled.fields.any { it.id == "mahjongcraft:preparation_base_seconds" })
        assertTrue(disabled.fields.none { it.id == "mahjongcraft:preparation_base_seconds" })

        val retained = registry.normalizeFlowConfig(
            ruleModuleId = "test:rule",
            ruleConfig = RiichiRuleConfig(redDoraCount = 3),
            current = GameFlowConfig(roundPreparation = RoundPreparationConfig(47)),
        )
        assertEquals(47, retained.roundPreparation?.baseSeconds)
        assertEquals(
            null,
            registry.normalizeFlowConfig(
                ruleModuleId = "test:rule",
                ruleConfig = RiichiRuleConfig(redDoraCount = 0),
                current = retained,
            ).roundPreparation,
        )
    }

    /** 第三方不可自行宣告保留的流程分類或欄位；錯誤訊息會指出規則與衝突 ID。 */
    @Test
    fun `reserved flow category and fields are rejected with their owner`() {
        val categoryConflict = minimalDefinition().copy(
            categories = listOf(
                GameConfigCategoryDefinition("test:category", "test.category"),
                GameConfigCategoryDefinition("mahjongcraft:flow", "test.flow"),
            ),
        )
        val fieldConflict = minimalDefinition().copy(
            fields = listOf(
                GameConfigFieldDefinition(
                    id = "mahjongcraft:base_seconds",
                    categoryId = "test:category",
                    nameTranslationKey = "test.base_seconds",
                    descriptionTranslationKey = "test.base_seconds.description",
                    editor = GameConfigEditorSpec.IntegerInput(0, 3600),
                    isEditable = false,
                    read = { GameConfigPresentationValue.IntegerValue(30) },
                ),
            ),
        )

        val categoryFailure = assertFailsWith<IllegalArgumentException> {
            GameConfigPresentationRegistryImpl(RoundPreparationResolverRegistry()).register(categoryConflict)
        }
        val fieldFailure = assertFailsWith<IllegalArgumentException> {
            GameConfigPresentationRegistryImpl(RoundPreparationResolverRegistry()).register(fieldConflict)
        }

        assertTrue(categoryFailure.message.orEmpty().contains("test:rule"))
        assertTrue(categoryFailure.message.orEmpty().contains("mahjongcraft:flow"))
        assertTrue(fieldFailure.message.orEmpty().contains("test:rule"))
        assertTrue(fieldFailure.message.orEmpty().contains("mahjongcraft:base_seconds"))
    }

    /** 準備時間欄位是保留欄位，規則不可自行重複登記。 */
    @Test
    fun `reserved preparation field is rejected with and without preparation support`() {
        val conflict = minimalDefinition().copy(
            fields = listOf(
                GameConfigFieldDefinition(
                    id = "mahjongcraft:preparation_base_seconds",
                    categoryId = "test:category",
                    nameTranslationKey = "test.preparation",
                    descriptionTranslationKey = "test.preparation.description",
                    editor = GameConfigEditorSpec.IntegerInput(1, 3600),
                    isEditable = false,
                    read = { GameConfigPresentationValue.IntegerValue(30) },
                ),
            ),
        )

        listOf(RoundPreparationResolverRegistry(), preparationRegistry()).forEach { resolvers ->
            assertFailsWith<IllegalArgumentException> {
                GameConfigPresentationRegistryImpl(resolvers).register(conflict)
            }
        }
    }

    /** 開局準備的預設秒數必須落在畫面可編輯的範圍內。 */
    @Test
    fun `invalid preparation default is rejected`() {
        val registry = GameConfigPresentationRegistryImpl(preparationRegistry(defaultSeconds = 3601))

        assertFailsWith<IllegalArgumentException> {
            registry.register(minimalDefinition())
        }
    }

    /** 內建規則提供相應選項，台麻目前明確標示為不可選。 */
    @Test
    fun `built-in schemas expose editable Riichi and unavailable Taiwan rules`() {
        val registry = GameConfigPresentationRegistryImpl(RoundPreparationResolverRegistry())

        BundledMinecraftMahjongExtensions.all.forEach { it.registerGameConfigPresentations(registry) }

        val riichi = assertNotNull(registry.find("mahjongcraft:riichi"))
        val taiwan = assertNotNull(registry.find("mahjongcraft:taiwan"))
        assertTrue(riichi.selectable)
        assertEquals(1, riichi.categories.count { it.id == "mahjongcraft:flow" })
        assertEquals(4, riichi.fields.count { it.categoryId == "mahjongcraft:flow" })
        assertTrue(riichi.fields.none { it.id == "mahjongcraft:preparation_base_seconds" })
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
        val registry = GameConfigPresentationRegistryImpl(RoundPreparationResolverRegistry())
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
        val registry = GameConfigPresentationRegistryImpl(RoundPreparationResolverRegistry())
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
        val registry = GameConfigPresentationRegistryImpl(RoundPreparationResolverRegistry())
        registry.register(definition)
        optionIds += "test:other"
        translationKeys["test:option"] = "test.changed"

        val registered = assertNotNull(registry.find("test:rule"))
        val editor = registered.fields.single { it.id == "test:choice" }.editor as GameConfigEditorSpec.SingleChoice
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

    /**
     * 建立測試用的局前準備解析器 registry。
     *
     * @param defaultSeconds 測試規則提供的預設準備秒數。
     * @return 含測試規則解析器的 registry。
     */
    private fun preparationRegistry(defaultSeconds: Int = 30) = RoundPreparationResolverRegistry().apply {
        register(TestPreparationResolver(defaultSeconds))
    }

    /**
     * 僅提供預設時間的測試解析器，不執行開局流程。
     *
     * @property defaultSeconds 登記時提供的預設準備秒數。
     */
    private class TestPreparationResolver(
        private val defaultSeconds: Int,
    ) : RoundPreparationResolver {
        override val ruleModuleId: String = "test:rule"
        override fun roundPreparationConfig(ruleConfig: MahjongRuleConfig): RoundPreparationConfig = RoundPreparationConfig(defaultSeconds)

        override fun begin(
            tableState: TableState,
            ruleModule: MahjongRuleModule<*>,
        ): Nothing = error("not used")

        override fun resolve(
            tableState: TableState,
            preparation: PendingRoundPreparation,
            ruleModule: MahjongRuleModule<*>,
        ): RoundPreparationResolution = error("not used")
    }

    private object ConfigAwarePreparationResolver : RoundPreparationResolver {
        override val ruleModuleId: String = "test:rule"

        override fun roundPreparationConfig(ruleConfig: MahjongRuleConfig): RoundPreparationConfig? = if ((ruleConfig as? RiichiRuleConfig)?.redDoraCount == 3) RoundPreparationConfig(30) else null

        override fun begin(
            tableState: TableState,
            ruleModule: MahjongRuleModule<*>,
        ): Nothing = error("not used")

        override fun resolve(
            tableState: TableState,
            preparation: PendingRoundPreparation,
            ruleModule: MahjongRuleModule<*>,
        ): RoundPreparationResolution = error("not used")
    }
}
