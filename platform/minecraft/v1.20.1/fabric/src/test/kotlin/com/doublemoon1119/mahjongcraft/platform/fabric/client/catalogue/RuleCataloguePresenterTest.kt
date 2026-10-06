package com.doublemoon1119.mahjongcraft.platform.fabric.client.catalogue

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.MinecraftRuleCatalogueScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogue
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueBrowseStatus
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueBrowser
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueCategory
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueConfigSource
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueExample
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueLabel
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueTileGroup
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueTileGroupRole
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證規則一覽 card 的文字、牌例排列與缺少翻譯／圖案時的處理。 */
class RuleCataloguePresenterTest {
    /** 條目文字依序為名稱、標籤、說明與無法使用的原因，長文字換行後不超出 card。 */
    @Test
    fun `card shows name labels description and reason in order within the width`() {
        val entry = entry(
            labels = listOf(RuleCatalogueLabel(LABEL_A), RuleCatalogueLabel(LABEL_B)),
            reason = REASON,
            description = LONG_DESCRIPTION,
        )
        val card = presenter().measure(entry = entry, width = CARD_WIDTH)

        val texts = card.lines.map { it.text }
        assertEquals(TEXT.getValue(NAME), texts.first())
        assertEquals(listOf(TEXT.getValue(LABEL_A), RuleCataloguePresenter.LABEL_SEPARATOR, TEXT.getValue(LABEL_B)), texts.subList(1, 4))
        assertEquals(1, card.lines.subList(1, 4).map { it.y }.distinct().size)
        assertTrue(card.lines.count { it.color == RuleCataloguePresenter.DESCRIPTION_COLOR } > 1, "long descriptions must wrap")
        assertEquals(TEXT.getValue(REASON), card.lines.last().text)
        assertEquals(RuleCataloguePresenter.UNAVAILABLE_COLOR, card.lines.last().color)
        assertInside(card)
        assertTrue(card.lines.zipWithNext().all { (first, second) -> first.y <= second.y })
    }

    /** 有說明的標籤在指到時顯示說明，沒有說明的標籤與分隔符號不顯示提示。 */
    @Test
    fun `labels with descriptions show them as tooltips`() {
        val card = presenter().measure(
            entry = entry(labels = listOf(RuleCatalogueLabel(LABEL_A), RuleCatalogueLabel(LABEL_B, LABEL_B_DESCRIPTION))),
            width = CARD_WIDTH,
        )
        val (plain, separator, described) = card.lines.subList(1, 4)

        assertTrue(plain.tooltip.isEmpty())
        assertTrue(separator.tooltip.isEmpty())
        assertEquals(listOf(TEXT.getValue(LABEL_B_DESCRIPTION)), described.tooltip)
        assertEquals(plain.x + plain.width, separator.x)
        assertEquals(separator.x + separator.width, described.x)
    }

    /** 標籤說明缺少翻譯時，提示改為缺少翻譯的說明。 */
    @Test
    fun `missing label descriptions report the missing key`() {
        val card = presenter().measure(entry = entry(labels = listOf(RuleCatalogueLabel(LABEL_A, MISSING_KEY))), width = CARD_WIDTH)

        assertEquals(listOf("missing translation $MISSING_KEY"), card.lines[1].tooltip)
    }

    /** 放不下的標籤整個移到下一行，行首不放分隔符號，每個標籤仍各自帶提示且都在 card 內。 */
    @Test
    fun `labels that do not fit move to the next line whole`() {
        val labels = List(4) { RuleCatalogueLabel(LABEL_B, LABEL_B_DESCRIPTION) }
        val card = presenter().measure(entry = entry(labels = labels), width = NARROW_CARD_WIDTH + 60)
        val labelLines = card.lines.filter { it.color == RuleCataloguePresenter.LABEL_COLOR }

        assertEquals(4, labelLines.size)
        assertTrue(labelLines.map { it.y }.distinct().size > 1)
        assertTrue(labelLines.all { it.text == TEXT.getValue(LABEL_B) && it.tooltip.isNotEmpty() })
        val separators = card.lines.filter { it.text == RuleCataloguePresenter.LABEL_SEPARATOR }
        separators.forEach { separator -> assertTrue(separator.x > RuleCataloguePresenter.PADDING) }
        assertInside(card, width = NARROW_CARD_WIDTH + 60)
    }

    /** 沒有範例的條目不保留牌例區，也沒有牌面。 */
    @Test
    fun `entries without examples have no tile area`() {
        val presenter = presenter()
        val plain = presenter.measure(entry = entry(), width = CARD_WIDTH)
        val withExample = presenter.measure(entry = entry(examples = listOf(example(complete = true))), width = CARD_WIDTH)

        assertTrue(plain.tiles.isEmpty())
        assertTrue(withExample.height > plain.height)
        assertEquals(plain.height, plain.lines.last().y + RuleCataloguePresenter.LINE_HEIGHT + RuleCataloguePresenter.PADDING)
    }

    /** 多個範例依原順序顯示，各有完整手牌或局部示意的標題，牌組標題與牌面都在 card 內。 */
    @Test
    fun `multiple examples keep order and titles`() {
        val card = presenter().measure(
            entry = entry(examples = listOf(example(complete = true, context = CONTEXT), example(complete = false))),
            width = CARD_WIDTH,
        )
        val titles = card.lines.filter { it.color == RuleCataloguePresenter.EXAMPLE_TITLE_COLOR }

        assertEquals(listOf(TEXT.getValue(MinecraftRuleCatalogueScreenKeys.EXAMPLE_COMPLETE), TEXT.getValue(MinecraftRuleCatalogueScreenKeys.EXAMPLE_PARTIAL)), titles.map { it.text })
        assertTrue(card.lines.any { it.text == TEXT.getValue(CONTEXT) })
        val firstExampleTiles = card.tiles.filter { it.placement.y < titles[1].y }
        assertEquals(14, firstExampleTiles.size)
        assertTrue(card.tiles.drop(14).all { it.placement.y > titles[1].y })
        assertInside(card)
    }

    /** 四槓加雀頭的 18 張完整範例在窄 card 中換列，所有牌面都在 card 內且按原順序。 */
    @Test
    fun `eighteen tile example wraps inside a narrow card`() {
        val groups = List(4) { RuleCatalogueTileGroup(RuleCatalogueTileGroupRole.CLOSED_KAN, List(4) { Tile.Honor.East }) } +
            RuleCatalogueTileGroup(RuleCatalogueTileGroupRole.HAND, listOf(Tile.Honor.Red, Tile.Honor.Red))
        val card = presenter().measure(
            entry = entry(examples = listOf(RuleCatalogueExample(completeHand = true, groups = groups))),
            width = NARROW_CARD_WIDTH,
        )

        assertEquals(18, card.tiles.size)
        assertEquals(groups.flatMap { it.tiles }, card.tiles.map { it.placement.tile })
        assertInside(card, width = NARROW_CARD_WIDTH)
        assertTrue(card.tiles.map { it.placement.y }.distinct().size > 1)
    }

    /** 缺少翻譯時顯示翻譯鍵，並在指向該行時提示缺少的翻譯。 */
    @Test
    fun `missing translations show the key with a tooltip`() {
        val card = presenter().measure(entry = entry(name = MISSING_KEY), width = CARD_WIDTH)
        val line = card.lines.first()

        assertEquals(MISSING_KEY, line.text)
        assertEquals(listOf("missing translation $MISSING_KEY"), line.tooltip)
        assertTrue(card.lines.drop(1).all { it.tooltip.isEmpty() })
    }

    /** 缺少圖案的牌使用未知牌面並提示牌種，其他牌照常顯示。 */
    @Test
    fun `missing tile art falls back to the unknown face with a tooltip`() {
        val custom = Tile.Extension(TileTypeId("example", "flower_plum"))
        val card = presenter().measure(
            entry = entry(
                examples = listOf(
                    RuleCatalogueExample(
                        completeHand = false,
                        groups = listOf(RuleCatalogueTileGroup(RuleCatalogueTileGroupRole.ILLUSTRATION, listOf(Tile.Honor.East, custom))),
                    ),
                ),
            ),
            width = CARD_WIDTH,
        )

        assertEquals("east", card.tiles[0].assetKey)
        assertTrue(card.tiles[0].tooltip.isEmpty())
        assertEquals(UNKNOWN_TILE_ASSET_KEY, card.tiles[1].assetKey)
        assertEquals(listOf("missing art example:flower_plum"), card.tiles[1].tooltip)
    }

    /** 牌組標題比可用寬度長時截短，不超出 card。 */
    @Test
    fun `long group headings are trimmed`() {
        val card = presenter(extra = mapOf(MinecraftRuleCatalogueScreenKeys.GROUP_WINNING_TILE to "W".repeat(80))).measure(
            entry = entry(
                examples = listOf(
                    RuleCatalogueExample(
                        completeHand = true,
                        groups = listOf(RuleCatalogueTileGroup(RuleCatalogueTileGroupRole.WINNING_TILE, listOf(Tile.Honor.North))),
                    ),
                ),
            ),
            width = NARROW_CARD_WIDTH,
        )

        assertInside(card, width = NARROW_CARD_WIDTH)
    }

    /** 自訂分類、自訂牌種與非日麻價值標籤的第三方規則，不需修改共用畫面就能篩選並排成 card。 */
    @Test
    fun `third party rules render through the shared presenter`() {
        val custom = Tile.Extension(TileTypeId("example", "season_spring"))
        val catalogue = RuleCatalogue(
            categories = listOf(
                RuleCatalogueCategory(id = "example:bonus", nameTranslationKey = "example.category.bonus"),
                RuleCatalogueCategory(id = "example:patterns", nameTranslationKey = "example.category.patterns"),
            ),
            entries = listOf(
                RuleCatalogueEntry(
                    id = "example:season",
                    categoryId = "example:bonus",
                    nameTranslationKey = "example.entry.season",
                    descriptionTranslationKey = "example.entry.season.description",
                    labels = listOf(RuleCatalogueLabel("example.label.one_point")),
                    examples = listOf(
                        RuleCatalogueExample(
                            completeHand = false,
                            groups = listOf(RuleCatalogueTileGroup(RuleCatalogueTileGroupRole.ILLUSTRATION, listOf(custom))),
                        ),
                    ),
                ),
                RuleCatalogueEntry(
                    id = "example:plain",
                    categoryId = "example:patterns",
                    nameTranslationKey = "example.entry.plain",
                    descriptionTranslationKey = "example.entry.plain.description",
                ),
            ),
        )
        val registry = RuleCatalogueRegistryImpl().apply { register(FixedProvider(THIRD_PARTY_RULE, catalogue)) }
        val names = RuleModuleDisplayNameRegistryImpl().apply { register(THIRD_PARTY_RULE, "example.rule") }
        val presenter = presenter(
            extra = mapOf(
                "example.rule" to "Example Rule",
                "example.entry.season" to "Seasons",
                "example.entry.season.description" to "Each season tile scores a point.",
                "example.label.one_point" to "1 point",
                "example.entry.plain" to "Plain",
                "example.entry.plain.description" to "No examples.",
            ),
        )
        val browser = RuleCatalogueBrowser(catalogues = registry, ruleNames = names, knownRuleIds = listOf(THIRD_PARTY_RULE))

        val all = browser.snapshot(presenter::translation)
        assertEquals(RuleCatalogueBrowseStatus.AVAILABLE, all.status)
        assertEquals("Example Rule", all.ruleOptions.single().displayName)
        val cards = all.entries.map { presenter.measure(entry = it, width = CARD_WIDTH) }
        assertEquals(listOf("Seasons", "Plain"), cards.map { it.lines.first().text })
        assertEquals("1 point", cards[0].lines[1].text)
        assertEquals(UNKNOWN_TILE_ASSET_KEY, cards[0].tiles.single().assetKey)
        assertTrue(cards[1].tiles.isEmpty())
        cards.forEach { assertInside(it) }
        assertTrue(browser.selectCategory("example:patterns"))
        assertEquals(listOf("example:plain"), browser.snapshot(presenter::translation).entries.map { it.id })
        assertTrue(browser.setSearch("season"))
        assertEquals(RuleCatalogueBrowseStatus.NO_RESULTS, browser.snapshot(presenter::translation).status)
    }

    /** 每個瀏覽狀態與設定來源都有對應文字，有內容時不顯示狀態說明。 */
    @Test
    fun `every status and source has text`() {
        RuleCatalogueBrowseStatus.entries.forEach { status ->
            val key = catalogueStatusKey(status)
            if (status == RuleCatalogueBrowseStatus.AVAILABLE) {
                assertEquals(null, key)
            } else {
                assertTrue(key in MinecraftRuleCatalogueScreenKeys.ALL, status.name)
            }
        }
        RuleCatalogueTileGroupRole.entries.forEach { assertTrue(roleKey(it) in MinecraftRuleCatalogueScreenKeys.ALL, it.name) }
        RuleCatalogueConfigSource.entries.forEach {
            assertTrue(catalogueSourceKey(it) in MinecraftRuleCatalogueScreenKeys.ALL, it.name)
        }
    }

    /**
     * 確認 card 內所有文字與牌面都在左右留白之內，且不超出 card 高度。
     *
     * @param card 受測 card。
     * @param width card 寬度。
     */
    private fun assertInside(card: CatalogueCard, width: Int = CARD_WIDTH) {
        val right = width - RuleCataloguePresenter.PADDING
        card.lines.forEach { line ->
            assertTrue(line.x >= RuleCataloguePresenter.PADDING && line.x + line.width <= right, "line '${line.text}' exceeds the card")
            assertTrue(line.y + RuleCataloguePresenter.LINE_HEIGHT <= card.height)
        }
        card.tiles.forEach { tile ->
            val placement = tile.placement
            assertTrue(placement.x >= RuleCataloguePresenter.PADDING && placement.x + placement.width <= right, "tile exceeds the card")
            assertTrue(placement.y + placement.height <= card.height)
        }
    }

    /**
     * 建立使用固定字寬與測試翻譯的 presenter。
     *
     * @param extra 額外的測試翻譯。
     * @return 測試用 presenter。
     */
    private fun presenter(extra: Map<String, String> = emptyMap()): RuleCataloguePresenter {
        val texts = TEXT + extra
        return RuleCataloguePresenter(
            translate = texts::get,
            metrics = FixedWidthMetrics,
            tileArt = { tile ->
                when (tile) {
                    is Tile.Extension -> CatalogueTileArt(assetKey = UNKNOWN_TILE_ASSET_KEY, missingName = "${tile.typeId.namespace}:${tile.typeId.path}")
                    Tile.Honor.East -> CatalogueTileArt(assetKey = "east")
                    else -> CatalogueTileArt(assetKey = tile.toString())
                }
            },
        )
    }

    /**
     * 建立測試條目。
     *
     * @param name 名稱翻譯鍵。
     * @param description 說明翻譯鍵。
     * @param labels 標籤。
     * @param reason 無法使用的原因翻譯鍵。
     * @param examples 範例。
     * @return 測試條目。
     */
    private fun entry(
        name: String = NAME,
        description: String = DESCRIPTION,
        labels: List<RuleCatalogueLabel> = emptyList(),
        reason: String? = null,
        examples: List<RuleCatalogueExample> = emptyList(),
    ): RuleCatalogueEntry = RuleCatalogueEntry(
        id = "example:entry",
        categoryId = "example:category",
        nameTranslationKey = name,
        descriptionTranslationKey = description,
        labels = labels,
        unavailableReasonTranslationKey = reason,
        examples = examples,
    )

    /**
     * 建立 13 張手牌加 1 張和牌張的範例。
     *
     * @param complete 是否為完整手牌。
     * @param context 情境說明翻譯鍵。
     * @return 測試範例。
     */
    private fun example(complete: Boolean, context: String? = null): RuleCatalogueExample = RuleCatalogueExample(
        completeHand = complete,
        groups = listOf(
            RuleCatalogueTileGroup(RuleCatalogueTileGroupRole.HAND, (1..9).map { Tile.Numeric(Tile.Suit.Bamboo, it) } + List(4) { Tile.Honor.South }),
            RuleCatalogueTileGroup(RuleCatalogueTileGroupRole.WINNING_TILE, listOf(Tile.Honor.South)),
        ),
        descriptionTranslationKey = context,
    )

    /** 每個字元 6 像素寬、逐字換行的測試字型。 */
    private object FixedWidthMetrics : RuleCatalogueTextMetrics {
        override fun width(text: String): Int = text.length * CHAR_WIDTH

        override fun wrap(text: String, maxWidth: Int): List<String> = text.chunked((maxWidth / CHAR_WIDTH).coerceAtLeast(1)).ifEmpty { listOf("") }

        override fun trim(text: String, maxWidth: Int): String = text.take((maxWidth / CHAR_WIDTH).coerceAtLeast(0))
    }

    /**
     * 回傳固定目錄的測試來源。
     *
     * @property ruleModuleId 規則 ID。
     * @property catalogue 固定目錄。
     */
    private class FixedProvider(
        override val ruleModuleId: String,
        private val catalogue: RuleCatalogue,
    ) : RuleCatalogueProvider {
        override fun defaultRuleConfig(): MahjongRuleConfig = TaiwanRuleConfig()

        override fun catalogue(config: MahjongRuleConfig): RuleCatalogue = catalogue
    }

    /** 測試資料。 */
    private companion object {
        /** 測試字寬。 */
        const val CHAR_WIDTH = 6

        /** 一般 card 寬度。 */
        const val CARD_WIDTH = 300

        /** 窄 card 寬度。 */
        const val NARROW_CARD_WIDTH = 100

        /** 第三方規則 ID。 */
        const val THIRD_PARTY_RULE = "example:season_rule"

        /** 名稱翻譯鍵。 */
        const val NAME = "test.name"

        /** 說明翻譯鍵。 */
        const val DESCRIPTION = "test.description"

        /** 長說明翻譯鍵。 */
        const val LONG_DESCRIPTION = "test.long_description"

        /** 第一個標籤翻譯鍵。 */
        const val LABEL_A = "test.label.a"

        /** 第二個標籤翻譯鍵。 */
        const val LABEL_B = "test.label.b"

        /** 第二個標籤的說明翻譯鍵。 */
        const val LABEL_B_DESCRIPTION = "test.label.b.description"

        /** 無法使用原因翻譯鍵。 */
        const val REASON = "test.reason"

        /** 範例情境翻譯鍵。 */
        const val CONTEXT = "test.context"

        /** 沒有翻譯的鍵。 */
        const val MISSING_KEY = "test.missing"

        /** 測試翻譯。 */
        val TEXT: Map<String, String> = mapOf(
            NAME to "Name",
            DESCRIPTION to "Short description.",
            LONG_DESCRIPTION to "A".repeat(120),
            LABEL_A to "2 han",
            LABEL_B to "Closed only",
            LABEL_B_DESCRIPTION to "Not valid after a call.",
            REASON to "Disabled by the current settings.",
            CONTEXT to "Seat wind: East.",
            MinecraftRuleCatalogueScreenKeys.EXAMPLE_COMPLETE to "Complete",
            MinecraftRuleCatalogueScreenKeys.EXAMPLE_PARTIAL to "Partial",
            MinecraftRuleCatalogueScreenKeys.GROUP_HAND to "Hand",
            MinecraftRuleCatalogueScreenKeys.GROUP_WINNING_TILE to "Win",
            MinecraftRuleCatalogueScreenKeys.GROUP_CLOSED_KAN to "Kan",
            MinecraftRuleCatalogueScreenKeys.GROUP_ILLUSTRATION to "Shape",
            MinecraftRuleCatalogueScreenKeys.MISSING_TRANSLATION to "missing translation %s",
            MinecraftRuleCatalogueScreenKeys.MISSING_ASSET to "missing art %s",
        )
    }
}
