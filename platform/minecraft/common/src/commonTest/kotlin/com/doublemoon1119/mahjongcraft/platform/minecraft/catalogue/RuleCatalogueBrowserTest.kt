package com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證規則目錄瀏覽器的選擇、搜尋、分類、配置來源與失敗狀態。 */
class RuleCatalogueBrowserTest {
    /** 規則選項合併已知來源與目前規則，且以穩定 ID 排序去重。 */
    @Test
    fun `rule options merge known provider and context ids in stable order`() {
        val registry = registry(
            RecordingProvider(PROVIDER_B, TaiwanRuleConfig()),
            RecordingProvider(PROVIDER_C, TaiwanRuleConfig()),
        )
        val knownIds = mutableListOf(PROVIDER_A, PROVIDER_B, PROVIDER_B, PROVIDER_D)
        val browser = RuleCatalogueBrowser(registry, names(), knownIds, RuleCatalogueBrowseContext(PROVIDER_E))
        knownIds.clear()

        assertEquals(listOf(PROVIDER_A, PROVIDER_B, PROVIDER_C, PROVIDER_D, PROVIDER_E), browser.snapshot(::translate).ruleOptions.map { it.ruleModuleId })
    }

    /** 沒有開啟脈絡時優先選擇有目錄來源的規則；完全沒有來源時才使用已知 ID。 */
    @Test
    fun `general browser prefers registered provider over known only rule`() {
        val browser = RuleCatalogueBrowser(
            registry(RecordingProvider(PROVIDER_C, TaiwanRuleConfig())),
            names(),
            listOf(PROVIDER_A, PROVIDER_D),
        )

        assertEquals(PROVIDER_C, browser.snapshot(::translate).selectedRuleModuleId)
        val fallback = RuleCatalogueBrowser(RuleCatalogueRegistryImpl(), names(), listOf(PROVIDER_D))
        assertEquals(PROVIDER_D, fallback.snapshot(::translate).selectedRuleModuleId)
    }

    /** 缺少規則名稱翻譯時保留規則 ID，不影響選擇。 */
    @Test
    fun `missing rule name translation falls back to rule id`() {
        val browser = RuleCatalogueBrowser(
            registry(RecordingProvider(PROVIDER_A, TaiwanRuleConfig())),
            names(),
            listOf(PROVIDER_A),
        )

        assertEquals(PROVIDER_A, browser.snapshot { null }.ruleOptions.single().displayName)
    }

    /** 切換規則改用一般說明，切回原規則恢復原本配置且不改寫配置來源。 */
    @Test
    fun `switching away and back restores opening configuration`() {
        val provider = RecordingProvider(PROVIDER_A, TaiwanRuleConfig())
        val other = RecordingProvider(PROVIDER_B, TaiwanRuleConfig())
        listOf(RuleCatalogueConfigSource.ROOM, RuleCatalogueConfigSource.ROOM_DRAFT, RuleCatalogueConfigSource.HISTORY).forEachIndexed { index, source ->
            val config = RiichiRuleConfig(redDoraCount = index)
            val browser = RuleCatalogueBrowser(
                registry(provider, other),
                names(),
                listOf(PROVIDER_A, PROVIDER_B),
                RuleCatalogueBrowseContext(PROVIDER_A, source, config),
            )

            assertEquals(source, browser.snapshot(::translate).configSource)
            assertTrue(browser.selectRule(PROVIDER_B))
            assertEquals(RuleCatalogueConfigSource.GENERAL, browser.snapshot(::translate).configSource)
            assertTrue(browser.selectRule(PROVIDER_A))
            assertEquals(source, browser.snapshot(::translate).configSource)
            assertEquals(config, provider.received.last())
        }
    }

    /** 缺少原配置時不偷用預設配置；明確選擇一般說明後才呼叫預設配置。 */
    @Test
    fun `missing opening config does not call default until general explanation`() {
        val provider = RecordingProvider(PROVIDER_A, TaiwanRuleConfig())
        val browser = RuleCatalogueBrowser(
            registry(provider),
            names(),
            listOf(PROVIDER_A),
            RuleCatalogueBrowseContext(PROVIDER_A, RuleCatalogueConfigSource.ROOM, null),
        )

        assertEquals(RuleCatalogueBrowseStatus.CONFIG_UNAVAILABLE, browser.snapshot(::translate).status)
        assertEquals(0, provider.defaultCalls)
        assertTrue(browser.useGeneralExplanation())
        assertFalse(browser.useGeneralExplanation())
        assertEquals(RuleCatalogueBrowseStatus.AVAILABLE, browser.snapshot(::translate).status)
        assertEquals(1, provider.defaultCalls)
        assertTrue(browser.restoreOpeningConfig())
        assertFalse(browser.restoreOpeningConfig())
        assertEquals(RuleCatalogueBrowseStatus.CONFIG_UNAVAILABLE, browser.snapshot(::translate).status)

        val general = browserFor(provider)
        assertFalse(general.useGeneralExplanation())
        assertFalse(general.restoreOpeningConfig())
    }

    /** 區分缺少來源、不支援配置、空目錄、搜尋無結果與沒有規則。 */
    @Test
    fun `browser reports distinct unavailable and empty states`() {
        assertEquals(
            RuleCatalogueBrowseStatus.MISSING_PROVIDER,
            RuleCatalogueBrowser(
                RuleCatalogueRegistryImpl(),
                names(),
                listOf(PROVIDER_A),
                RuleCatalogueBrowseContext(PROVIDER_A),
            ).snapshot(::translate).status,
        )

        val unsupported = RecordingProvider(PROVIDER_A, TaiwanRuleConfig()) { null }
        assertEquals(
            RuleCatalogueBrowseStatus.UNSUPPORTED_CONFIG,
            RuleCatalogueBrowser(registry(unsupported), names(), listOf(PROVIDER_A), RuleCatalogueBrowseContext(PROVIDER_A, RuleCatalogueConfigSource.ROOM, RiichiRuleConfig())).snapshot(::translate).status,
        )

        val empty = RecordingProvider(PROVIDER_A, TaiwanRuleConfig()) { RuleCatalogue(emptyList(), emptyList()) }
        assertEquals(RuleCatalogueBrowseStatus.EMPTY_CATALOGUE, browserFor(empty).snapshot(::translate).status)

        val noResults = browserFor(RecordingProvider(PROVIDER_A, TaiwanRuleConfig()))
        assertTrue(noResults.setSearch("not-present"))
        assertEquals(RuleCatalogueBrowseStatus.NO_RESULTS, noResults.snapshot(::translate).status)

        val noRules = RuleCatalogueBrowser(RuleCatalogueRegistryImpl(), names(), emptyList())
        assertEquals(RuleCatalogueBrowseStatus.NO_RULES, noRules.snapshot(::translate).status)
    }

    /** 搜尋比對名稱、敘述、標籤與 ID，忽略大小寫及前後空白，並保留來源順序。 */
    @Test
    fun `search matches translated fields ids and labels without requery`() {
        val provider = RecordingProvider(PROVIDER_A, TaiwanRuleConfig())
        val browser = browserFor(provider)
        val initialCalls = provider.catalogueCalls

        assertTrue(browser.setSearch("entry"))
        assertEquals(listOf("example:first", "example:second", "example:text"), browser.snapshot(::translate).entries.map { it.id })
        assertEquals(emptyList(), browser.snapshot { null }.entries)
        assertEquals(initialCalls, provider.catalogueCalls)
        assertTrue(browser.setSearch("  FIRST ENTRY  "))
        assertEquals(listOf("example:first"), browser.snapshot(::translate).entries.map { it.id })
        assertTrue(browser.setSearch("STRUCTURAL"))
        assertEquals(listOf("example:first"), browser.snapshot(::translate).entries.map { it.id })
        assertTrue(browser.setSearch("  LABEL  "))
        assertEquals(listOf("example:second"), browser.snapshot(::translate).entries.map { it.id })
        assertEquals(initialCalls, provider.catalogueCalls)
        assertTrue(browser.setSearch("example:first"))
        assertEquals(listOf("example:first"), browser.snapshot { null }.entries.map { it.id })
        assertFalse(browser.setSearch("example:first"))
        assertTrue(browser.setSearch("example:text"))
        assertEquals(listOf("example:text"), browser.snapshot(::translate).entries.map { it.id })
    }

    /** 分類只接受目前目錄的分類；切換規則清除分類但保留搜尋文字。 */
    @Test
    fun `category selection intersects search and selection resets category`() {
        val first = RecordingProvider(PROVIDER_A, TaiwanRuleConfig())
        val second = RecordingProvider(PROVIDER_B, TaiwanRuleConfig())
        val browser = RuleCatalogueBrowser(registry(first, second), names(), listOf(PROVIDER_A, PROVIDER_B))

        assertFalse(browser.selectRule(PROVIDER_A))
        assertFalse(browser.selectRule("example:unknown"))
        assertTrue(browser.selectCategory(CATEGORY_B))
        assertFalse(browser.selectCategory(CATEGORY_B))
        assertFalse(browser.selectCategory("example:unknown"))
        assertTrue(browser.setSearch("entry"))
        assertEquals(listOf("example:second", "example:text"), browser.snapshot(::translate).entries.map { it.id })
        assertTrue(browser.selectRule(PROVIDER_B))
        val state = browser.snapshot(::translate)
        assertEquals(null, state.categoryId)
        assertEquals("entry", state.searchText)
    }

    /** 第三方自訂價值單位、文字條目與擴充牌種維持原資料，不被瀏覽器改寫。 */
    @Test
    fun `third party units text entries and extension tiles remain unchanged`() {
        val browser = browserFor(RecordingProvider(PROVIDER_A, TaiwanRuleConfig()))
        val state = browser.snapshot(::translate)
        val first = state.entries.first()
        val labelled = state.entries.first { it.id == "example:second" }
        assertEquals(listOf("example:points"), labelled.labelTranslationKeys)
        assertEquals(Tile.Extension(TileTypeId("example", "custom_tile")), first.examples.single().groups.single().tiles.single())
        assertEquals("example:text", state.entries.last().id)
        assertTrue(state.entries.last().examples.isEmpty())
    }

    /** Provider 來源例外不被瀏覽器誤轉成空目錄。 */
    @Test
    fun `provider exceptions remain visible to caller`() {
        val provider = RecordingProvider(PROVIDER_A, TaiwanRuleConfig()) { error("provider failure") }
        assertFailsWith<IllegalStateException> { browserFor(provider) }
    }

    /** 解析失敗不應切換選擇或遺失原本已成功的內容。 */
    @Test
    fun `failed rule selection keeps previous snapshot`() {
        val original = RecordingProvider(PROVIDER_A, TaiwanRuleConfig())
        val failing = RecordingProvider(PROVIDER_C, TaiwanRuleConfig()) { error("provider failure") }
        val browser = browserFor(original, failing)
        val before = browser.snapshot(::translate)

        assertFailsWith<IllegalStateException> { browser.selectRule(PROVIDER_C) }
        val after = browser.snapshot(::translate)
        assertEquals(before.selectedRuleModuleId, after.selectedRuleModuleId)
        assertEquals(before.entries, after.entries)
    }

    /** 配置不支援時須明確選擇一般說明，且切回原配置仍保留不支援狀態。 */
    @Test
    fun `unsupported opening config requires explicit general explanation`() {
        val provider = RecordingProvider(PROVIDER_A, TaiwanRuleConfig()) { config ->
            if (config is TaiwanRuleConfig) sampleCatalogue() else null
        }
        val browser = RuleCatalogueBrowser(
            registry(provider),
            names(),
            emptyList(),
            RuleCatalogueBrowseContext(PROVIDER_A, RuleCatalogueConfigSource.HISTORY, RiichiRuleConfig()),
        )
        assertEquals(RuleCatalogueBrowseStatus.UNSUPPORTED_CONFIG, browser.snapshot(::translate).status)
        assertEquals(0, provider.defaultCalls)
        assertTrue(browser.useGeneralExplanation())
        assertEquals(RuleCatalogueConfigSource.GENERAL, browser.snapshot(::translate).configSource)
        assertEquals(RuleCatalogueBrowseStatus.AVAILABLE, browser.snapshot(::translate).status)
        assertTrue(browser.restoreOpeningConfig())
        assertEquals(RuleCatalogueBrowseStatus.UNSUPPORTED_CONFIG, browser.snapshot(::translate).status)
        assertEquals(RuleCatalogueConfigSource.HISTORY, browser.snapshot(::translate).configSource)
    }

    /** 無目錄規則不能殘留原條目，返回原規則後仍保留搜尋但已清除分類。 */
    @Test
    fun `missing provider selection clears old catalogue and entries`() {
        val browser = RuleCatalogueBrowser(registry(RecordingProvider(PROVIDER_A, TaiwanRuleConfig())), names(), listOf(PROVIDER_D))
        browser.selectCategory(CATEGORY_B)
        browser.setSearch("entry")
        assertTrue(browser.selectRule(PROVIDER_D))
        val missing = browser.snapshot(::translate)
        assertEquals(RuleCatalogueBrowseStatus.MISSING_PROVIDER, missing.status)
        assertEquals(null, missing.catalogue)
        assertTrue(missing.entries.isEmpty())
        assertFalse(browser.selectCategory(CATEGORY_B))
        assertTrue(browser.selectRule(PROVIDER_A))
        assertEquals(null, browser.snapshot(::translate).categoryId)
        assertEquals(3, browser.snapshot(::translate).entries.size)
        browser.setSearch("absent")
        assertEquals(RuleCatalogueBrowseStatus.NO_RESULTS, browser.snapshot(::translate).status)
        browser.setSearch("  ")
        assertEquals(RuleCatalogueBrowseStatus.AVAILABLE, browser.snapshot(::translate).status)
    }

    /** 規則名稱與搜尋翻譯由當前解析器取得，空白翻譯安全退回 ID 或鍵。 */
    @Test
    fun `language snapshots update names and search without resolving again`() {
        val provider = RecordingProvider(PROVIDER_A, TaiwanRuleConfig())
        val browser = browserFor(provider)
        browser.setSearch("役種")
        assertEquals(RuleCatalogueBrowseStatus.NO_RESULTS, browser.snapshot(::translate).status)
        val localized = browser.snapshot { key ->
            when (key) {
                "rule.$PROVIDER_A" -> "自訂規則"
                "example:first.name" -> "役種"
                else -> null
            }
        }
        assertEquals("自訂規則", localized.ruleOptions.single().displayName)
        assertEquals(listOf("example:first"), localized.entries.map { it.id })
        assertEquals(1, provider.catalogueCalls)
        browser.setSearch("example:first.name")
        val missing = browser.snapshot { "  " }
        assertEquals(PROVIDER_A, missing.ruleOptions.single().displayName)
        assertEquals(listOf("example:first"), missing.entries.map { it.id })
    }

    /** 一般說明與原設定解析失敗都不更改來源、分類或內容。 */
    @Test
    fun `failed config source changes preserve previous snapshot`() {
        var failGeneral = true
        var failActual = false
        val provider = RecordingProvider(PROVIDER_A, TaiwanRuleConfig()) { config ->
            check(!(config is TaiwanRuleConfig && failGeneral) && !(config is RiichiRuleConfig && failActual)) { "Provider failure" }
            sampleCatalogue()
        }
        val browser = RuleCatalogueBrowser(
            registry(provider),
            names(),
            emptyList(),
            RuleCatalogueBrowseContext(PROVIDER_A, RuleCatalogueConfigSource.ROOM_DRAFT, RiichiRuleConfig()),
        )
        browser.selectCategory(CATEGORY_B)
        browser.setSearch("entry")
        val before = browser.snapshot(::translate)
        assertFailsWith<IllegalStateException> { browser.useGeneralExplanation() }
        assertEquals(before, browser.snapshot(::translate))
        failGeneral = false
        assertTrue(browser.useGeneralExplanation())
        failActual = true
        val general = browser.snapshot(::translate)
        assertFailsWith<IllegalStateException> { browser.restoreOpeningConfig() }
        assertEquals(general, browser.snapshot(::translate))
    }

    /** 已知 ID 與開啟脈絡都必須符合 namespaced 識別碼契約。 */
    @Test
    fun `invalid known and context identifiers are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            RuleCatalogueBrowser(RuleCatalogueRegistryImpl(), names(), listOf("invalid"))
        }
        assertFailsWith<IllegalArgumentException> {
            RuleCatalogueBrowseContext("invalid")
        }
        assertFailsWith<IllegalArgumentException> {
            RuleCatalogueBrowseContext(source = RuleCatalogueConfigSource.HISTORY)
        }
        assertFailsWith<IllegalArgumentException> {
            RuleCatalogueBrowseContext(PROVIDER_A, RuleCatalogueConfigSource.GENERAL, TaiwanRuleConfig())
        }
    }

    /**
     * 建立只有指定來源的瀏覽器。
     *
     * @param providers 測試目錄來源，第一個來源作為開啟規則。
     * @return 使用測試名稱 registry 的瀏覽器。
     */
    private fun browserFor(vararg providers: RecordingProvider): RuleCatalogueBrowser = RuleCatalogueBrowser(
        registry(*providers),
        names(),
        providers.map { it.ruleModuleId },
        RuleCatalogueBrowseContext(providers.first().ruleModuleId),
    )

    /**
     * 建立測試用目錄 registry。
     *
     * @param providers 要登記的測試來源。
     * @return 可供瀏覽器解析的 registry。
     */
    private fun registry(vararg providers: RecordingProvider): RuleCatalogueRegistry = RuleCatalogueRegistryImpl().also { registry ->
        providers.forEach(registry::register)
    }

    /**
     * 建立測試用規則名稱 registry。
     *
     * @return 將規則 ID 轉成測試翻譯鍵的名稱 registry。
     */
    private fun names(): RuleModuleDisplayNameRegistry = object : RuleModuleDisplayNameRegistry {
        /** 測試名稱登記範圍。 */
        override val registrationKeys: Set<String> = setOf(PROVIDER_A, PROVIDER_B)

        /** 測試名稱來源不允許變更。 */
        override val isFrozen: Boolean = true

        /** 測試不執行額外的名稱註冊。 */
        override fun register(ruleModuleId: String, translationKey: String) = Unit

        /** 測試名稱來源已視為凍結。 */
        override fun freeze() = Unit

        /** 依規則 ID 提供翻譯測試鍵。 */
        override fun find(ruleModuleId: String): String? = "rule.$ruleModuleId"
    }

    /**
     * 測試用翻譯解析器，刻意保留可搜尋的字段差異。
     *
     * @param key 翻譯鍵。
     * @return 測試語言中的文字，或模擬缺少翻譯的 null。
     */
    private fun translate(key: String): String? = mapOf(
        "rule.$PROVIDER_A" to "Alpha",
        "rule.$PROVIDER_B" to "Beta",
        "example:first.name" to "First entry",
        "example:first.description" to "A structural example",
        "example:second.name" to "Second entry",
        "example:second.description" to "LABEL description",
        "example:points" to "Label",
        "example:text.name" to "Text entry",
    )[key]

    /**
     * 可記錄解析呼叫的測試來源。
     *
     * @property ruleModuleId 測試規則識別碼。
     * @property defaultConfig 一般說明使用的預設配置。
     * @property factory 依實際配置建立目錄的測試函式。
     */
    private class RecordingProvider(
        override val ruleModuleId: String,
        private val defaultConfig: MahjongRuleConfig,
        private val factory: (MahjongRuleConfig) -> RuleCatalogue? = { sampleCatalogue() },
    ) : RuleCatalogueProvider {
        /** 呼叫預設配置的次數。 */
        var defaultCalls: Int = 0

        /** 呼叫目錄工廠的次數。 */
        var catalogueCalls: Int = 0

        /** 目錄工廠收到的配置順序。 */
        val received = mutableListOf<MahjongRuleConfig>()

        /** 回傳測試用預設配置並記錄呼叫。 */
        override fun defaultRuleConfig(): MahjongRuleConfig {
            defaultCalls++
            return defaultConfig
        }

        /** 以測試工廠解析配置並記錄實際參數。 */
        override fun catalogue(config: MahjongRuleConfig): RuleCatalogue? {
            catalogueCalls++
            received += config
            return factory(config)
        }
    }

    /** 測試用的固定規則識別碼。 */
    private companion object {
        /** 第一個測試規則 ID。 */
        const val PROVIDER_A = "example:alpha"

        /** 第二個測試規則 ID。 */
        const val PROVIDER_B = "example:beta"

        /** 第三個測試規則 ID。 */
        const val PROVIDER_C = "example:charlie"

        /** 只有已知來源提供的測試規則 ID。 */
        const val PROVIDER_D = "example:delta"

        /** 只由開啟脈絡提供的測試規則 ID。 */
        const val PROVIDER_E = "example:echo"
    }
}

/** 測試目錄的第二分類。 */
private const val CATEGORY_B = "example:category_b"

/**
 * 建立含有搜尋欄位與擴充牌種的測試目錄。
 *
 * @return 包含分類、可搜尋條目與純文字條目的測試目錄。
 */
private fun sampleCatalogue(): RuleCatalogue = RuleCatalogue(
    categories = listOf(
        RuleCatalogueCategory("example:category_a", "example.category_a"),
        RuleCatalogueCategory(CATEGORY_B, "example.category_b"),
    ),
    entries = listOf(
        RuleCatalogueEntry(
            id = "example:first",
            categoryId = "example:category_a",
            nameTranslationKey = "example:first.name",
            descriptionTranslationKey = "example:first.description",
            examples = listOf(
                RuleCatalogueExample(
                    completeHand = false,
                    groups = listOf(
                        RuleCatalogueTileGroup(
                            RuleCatalogueTileGroupRole.ILLUSTRATION,
                            listOf(Tile.Extension(TileTypeId("example", "custom_tile"))),
                        ),
                    ),
                ),
            ),
        ),
        RuleCatalogueEntry(
            id = "example:second",
            categoryId = CATEGORY_B,
            nameTranslationKey = "example:second.name",
            descriptionTranslationKey = "example:second.description",
            labelTranslationKeys = listOf("example:points"),
        ),
        RuleCatalogueEntry("example:text", CATEGORY_B, "example:text.name", "example:text.description"),
    ),
)
