package com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 驗證中立目錄契約、第三方資料與配置解析邊界。 */
class RuleCatalogueRegistryTest {
    /** 非日麻單位與自訂牌種不依賴共用契約中的內建規則分支。 */
    @Test
    fun `third party catalogue supports custom units tiles and text only entries`() {
        val registry = RuleCatalogueRegistryImpl()
        registry.register(TestProvider())
        val result = assertIs<RuleCatalogueResolution.Available>(registry.resolve(RULE_ID))
        assertTrue(result.usesDefaultConfig)
        assertEquals(listOf(RuleCatalogueLabel("example:points")), result.catalogue.entries.first().labels)
        assertEquals(Tile.Extension(TileTypeId("example", "dragon")), result.catalogue.entries.first().examples.single().groups.single().tiles.single())
        assertTrue(result.catalogue.entries.last().examples.isEmpty())
    }

    /** 實際配置不被預設配置替代，缺失規則不回退至已登記來源。 */
    @Test
    fun `unsupported config and missing provider are distinct without fallback`() {
        val registry = RuleCatalogueRegistryImpl()
        registry.register(TestProvider())
        assertEquals(RuleCatalogueResolution.UnsupportedConfig, registry.resolve(RULE_ID, RiichiRuleConfig()))
        assertEquals(RuleCatalogueResolution.MissingProvider, registry.resolve("other:rule"))
        val result = assertIs<RuleCatalogueResolution.Available>(registry.resolve(RULE_ID, TaiwanRuleConfig()))
        assertFalse(result.usesDefaultConfig)
    }

    /** 不適用原因只在有實際設定時保留；一般說明沒有實際設定可對照，不帶原因。 */
    @Test
    fun `unavailable reasons appear only with an actual config`() {
        val registry = RuleCatalogueRegistryImpl()
        registry.register(TestProvider())

        val general = assertIs<RuleCatalogueResolution.Available>(registry.resolve(RULE_ID))
        val actual = assertIs<RuleCatalogueResolution.Available>(registry.resolve(RULE_ID, TaiwanRuleConfig()))

        assertTrue(general.catalogue.entries.all { it.unavailableReasonTranslationKey == null })
        assertEquals("example.text.unavailable", actual.catalogue.entries.last().unavailableReasonTranslationKey)
    }

    /** 重複登記拒絕且不覆蓋原來源，凍結後仍可讀取。 */
    @Test
    fun `duplicate registration and mutation after freeze are rejected`() {
        val registry = RuleCatalogueRegistryImpl()
        registry.register(TestProvider())
        assertFailsWith<IllegalArgumentException> { registry.register(TestProvider()) }
        registry.freeze()
        assertTrue(registry.isFrozen)
        assertFailsWith<IllegalStateException> { registry.register(TestProvider()) }
        assertIs<RuleCatalogueResolution.Available>(registry.resolve(RULE_ID))
    }

    /** 註冊鍵為獨立快照，不隨後續註冊變更。 */
    @Test
    fun `registration keys are independent snapshots`() {
        val registry = RuleCatalogueRegistryImpl()
        val snapshot = registry.registrationKeys
        registry.register(TestProvider())
        assertTrue(snapshot.isEmpty())
        assertEquals(setOf(RULE_ID), registry.registrationKeys)
    }

    /** ID 與關聯驗證不容許錯誤目錄流入呈現。 */
    @Test
    fun `invalid identifiers duplicate entries and unknown categories are rejected`() {
        assertFailsWith<IllegalArgumentException> { RuleCatalogueCategory("invalid", "category") }
        val catalogue = sampleCatalogue()
        assertFailsWith<IllegalArgumentException> { catalogue.copy(categories = catalogue.categories + catalogue.categories) }
        assertFailsWith<IllegalArgumentException> { catalogue.copy(entries = catalogue.entries + catalogue.entries) }
        assertFailsWith<IllegalArgumentException> { catalogue.copy(categories = emptyList()) }
        assertFailsWith<IllegalArgumentException> { catalogue.entries.first().copy(nameTranslationKey = "") }
    }

    /** 和牌張與局部示意的語意不依牌組位置猜測。 */
    @Test
    fun `examples distinguish winning tiles and partial illustrations`() {
        val tile = Tile.Extension(TileTypeId("example", "dragon"))
        assertFailsWith<IllegalArgumentException> { RuleCatalogueTileGroup(RuleCatalogueTileGroupRole.WINNING_TILE, listOf(tile, tile)) }
        assertFailsWith<IllegalArgumentException> { RuleCatalogueExample(false, emptyList()) }
        assertFalse(sampleCatalogue().entries.first().examples.single().completeHand)
    }
}

/**
 * 測試用非日麻來源；配置型別檢查由來源掌握。
 *
 * @property ruleModuleId 第三方規則識別碼。
 */
private class TestProvider : RuleCatalogueProvider {
    override val ruleModuleId: String = RULE_ID

    /** 建立一般說明配置。 */
    override fun defaultRuleConfig(): MahjongRuleConfig = TaiwanRuleConfig()

    /** 不匹配配置回傳 null，避免預設配置冒充實際配置。 */
    override fun catalogue(config: MahjongRuleConfig): RuleCatalogue? = if (config is TaiwanRuleConfig) sampleCatalogue() else null
}

/** 測試規則識別碼。 */
private const val RULE_ID = "example:rule"

/** 建立具有自訂價值單位、局部示意與純文字條目的目錄。 */
private fun sampleCatalogue(): RuleCatalogue = RuleCatalogue(
    categories = listOf(RuleCatalogueCategory("example:category", "example.category")),
    entries = listOf(
        RuleCatalogueEntry(
            id = "example:dragon",
            categoryId = "example:category",
            nameTranslationKey = "example.dragon",
            descriptionTranslationKey = "example.dragon.description",
            labels = listOf(RuleCatalogueLabel("example:points")),
            examples = listOf(
                RuleCatalogueExample(
                    completeHand = false,
                    groups = listOf(RuleCatalogueTileGroup(RuleCatalogueTileGroupRole.ILLUSTRATION, listOf(Tile.Extension(TileTypeId("example", "dragon"))))),
                ),
            ),
        ),
        RuleCatalogueEntry(
            id = "example:text",
            categoryId = "example:category",
            nameTranslationKey = "example.text",
            descriptionTranslationKey = "example.text.description",
            unavailableReasonTranslationKey = "example.text.unavailable",
        ),
    ),
)
