package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanWinSettlementIds
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanTaiType
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.RiichiWinSettlementTemplates
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan.TaiwanWinSettlementPresentation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 驗證內建規則共用的結算版面骨架。 */
class StandardWinSettlementLayoutTest {
    /** 已登記所有內建模板與欄位的 registry。 */
    private val registry = WinSettlementPresentationTemplateRegistryImpl().apply { registerBundledWinSettlementTemplates() }

    /** 文字一律量成固定寬度，只關心牌列的高度。 */
    private val solver = PresentationLayoutSolver(
        registry,
        object : PresentationTextMeasurer {
            override fun measure(text: PresentationValue.TextValue): Float = 10f

            override fun measurePlain(text: String): Float = 10f
        },
    )

    /** 沒有手牌時規則專屬列往上補到手牌的位置，不留手牌列與間距。 */
    @Test
    fun `rule row moves up when the hand is not shown`() {
        val tileRows = tileRows(TaiwanWinSettlementPresentation.TEMPLATE)
        val flowers = ExtensionPresentationField(TaiwanWinSettlementPresentation.FLOWERS, PresentationValue.TileListValue(listOf("flower_spring")))

        val withHand = solver.measure(tileRows, snapshot(tileGroups = listOf(listOf(PresentationTile.Face("m1"))), extensionFields = listOf(flowers)))
        val flowersOnly = solver.measure(tileRows, snapshot(tileGroups = emptyList(), extensionFields = listOf(flowers)))
        val nothing = solver.measure(tileRows, snapshot(tileGroups = emptyList(), extensionFields = emptyList()))

        assertEquals(15f + 12f + 11f, withHand.height)
        assertEquals(11f, flowersOnly.height)
        assertEquals(PresentationNodeSize.ZERO.height, nothing.height)
    }

    /** 清單接在規則專屬列下面，沒有手牌時一起往上補，中間不留空白。 */
    @Test
    fun `entries follow the rule row without a gap`() {
        val tileRows = tileRows(TaiwanWinSettlementPresentation.TEMPLATE)
        val flowers = ExtensionPresentationField(TaiwanWinSettlementPresentation.FLOWERS, PresentationValue.TileListValue(listOf("flower_spring")))
        val tai = ExtensionPresentationField(
            TaiwanWinSettlementPresentation.TAI,
            PresentationValue.EntryListValue(listOf(PresentationValue.EntryListValue.Entry(translationKey = "test.tai"))),
        )

        val flowersAndTai = solver.measure(tileRows, snapshot(tileGroups = emptyList(), extensionFields = listOf(flowers, tai)))

        assertEquals(11f + 13.5f + 11f, flowersAndTai.height)
    }

    /** 內建台種都有翻譯鍵，台數清單不會顯示原始 ID。 */
    @Test
    fun `taiwan tai entries use translated names`() {
        val entries = TaiwanWinSettlementPresentation.taiText.entries(
            listOf(
                WinSettlementDetailEntry(TaiwanTaiType.FULL_FLUSH.id, WinSettlementQuantity(TaiwanWinSettlementIds.TAI_UNIT, 8)),
                WinSettlementDetailEntry("mahjongcraft:taiwan/tai/test_unknown", null),
            ),
        ).entries

        assertEquals(listOf("mahjongcraft.taiwan.tai.full_flush", "mahjongcraft:taiwan/tai/test_unknown"), entries.map { it.translationKey })
    }

    /** 內建規則都由骨架組成：面板尺寸相同，手牌與規則專屬列放在同一個位置。 */
    @Test
    fun `built in templates share the skeleton`() {
        val templates = listOf(
            RiichiWinSettlementTemplates.RIICHI,
            TaiwanWinSettlementPresentation.TEMPLATE,
            BuiltInWinSettlementTemplates.GENERIC,
        )

        templates.forEach { template ->
            val root = assertIs<PresentationLayout.Box>(template.root)
            assertEquals(StandardWinSettlementLayout.WIDTH, root.width)
            assertEquals(StandardWinSettlementLayout.HEIGHT, root.height)
            assertIs<PresentationLayout.Column>(tileRows(template))
        }
        assertEquals(registry.findTemplateForRule(BuiltInRuleModuleIds.TAIWAN)?.key, TaiwanWinSettlementPresentation.TEMPLATE_KEY)
    }

    /** 台麻標題使用共用的胡牌動作文字，由各規則的動作用詞決定，不寫死在版面裡。 */
    @Test
    fun `taiwan title uses the win action wording`() {
        val title = (TaiwanWinSettlementPresentation.TEMPLATE.root as PresentationLayout.Box).children.first().child

        assertEquals(BuiltInWinSettlementFieldIds.OUTCOME_TITLE, assertIs<PresentationLayout.Text>(title).fieldId)
        assertEquals(
            PresentationValue.TextValue("test.win_action"),
            registry.findFieldProvider(BuiltInWinSettlementFieldIds.OUTCOME_TITLE)?.provide(snapshot(emptyList(), emptyList())),
        )
        assertEquals(
            PresentationValue.TextValue(TaiwanWinSettlementPresentation.FLOWERS_LABEL_KEY),
            registry.findFieldProvider(TaiwanWinSettlementPresentation.FLOWERS_LABEL)?.provide(snapshot(emptyList(), emptyList())),
        )
        assertTrue(TaiwanWinSettlementPresentation.TEMPLATE.toString().contains("RepeatEntries"))
    }

    /** 台麻台種清單只有八台以上的台種使用強調樣式。 */
    @Test
    fun `taiwan tai entries highlight eight tai or more`() {
        val entries = TaiwanWinSettlementPresentation.taiText.entries(
            listOf(
                WinSettlementDetailEntry("mahjongcraft:taiwan/tai/eight_flowers", WinSettlementQuantity(TaiwanWinSettlementIds.TAI_UNIT, 8)),
                WinSettlementDetailEntry("mahjongcraft:taiwan/tai/test_small", WinSettlementQuantity(TaiwanWinSettlementIds.TAI_UNIT, 7)),
                WinSettlementDetailEntry("mahjongcraft:taiwan/tai/test_unknown", null),
            ),
        ).entries

        assertEquals(listOf(true, false, false), entries.map { it.highlighted })
    }

    /**
     * 取出骨架中的手牌與規則專屬列。
     *
     * @param template 由骨架組成的模板。
     * @return 手牌列與規則專屬列所在的直行。
     */
    private fun tileRows(template: WinSettlementPresentationTemplate): PresentationLayout = (template.root as PresentationLayout.Box).children
        .single { it.child is PresentationLayout.Column }
        .child

    /**
     * 組成只有牌列內容有意義的快照。
     *
     * @param tileGroups 手牌與副露。
     * @param extensionFields 規則提供的欄位。
     * @return 測試快照。
     */
    private fun snapshot(
        tileGroups: List<List<PresentationTile>>,
        extensionFields: List<ExtensionPresentationField>,
    ) = WinSettlementPresentationFieldSnapshot(
        outcomeId = "mahjongcraft:tsumo",
        isTsumo = true,
        winActionLabelKey = "test.win_action",
        winnerId = "winner",
        winnerDisplayName = "Winner",
        winnerIsAi = false,
        responsiblePlayerId = null,
        responsiblePlayerDisplayName = null,
        responsiblePlayerIsAi = false,
        totalScore = 1_000,
        tileAssetKeys = emptyList(),
        tileGroups = tileGroups,
        winningTileAssetKey = null,
        extensionFields = extensionFields,
    )
}
