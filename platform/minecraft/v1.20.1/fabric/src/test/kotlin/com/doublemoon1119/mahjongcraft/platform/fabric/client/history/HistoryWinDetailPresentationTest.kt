package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFactTypeKeys
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiWinSettlementIds
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayFactDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayIdentityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayPlayerIdentityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayTransactionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundOutcomeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryWinDetailFieldDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryWinDetailQuantityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryWinDetailValueDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryWinnerDetailsDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BuiltInMinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BundledMinecraftMahjongExtensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.RiichiYakuTranslationKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundOutcomeDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementTextKeys
import net.minecraft.text.TranslatableTextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證歷史和牌明細與單次分差僅使用伺服器提供的已驗證資料。 */
class HistoryWinDetailPresentationTest {
    /** 指示牌欄位標題來自正式規則模板，未知欄位或模板不冒用內建名稱。 */
    @Test
    fun `indicator labels come from registered settlement templates`() {
        val templates = WinSettlementPresentationTemplateRegistryImpl().apply {
            BuiltInMinecraftMahjongExtension.registerWinSettlementPresentationTemplates(this)
            BundledMinecraftMahjongExtensions.all.forEach { it.registerWinSettlementPresentationTemplates(this) }
        }
        val presenter = presenter()
        val dora = presenter.detailLabel("mahjongcraft:riichi", RiichiWinSettlementIds.DORA_FIELD, templates)
        val ura = presenter.detailLabel("mahjongcraft:riichi", RiichiWinSettlementIds.URA_DORA_FIELD, templates)
        assertEquals(WinSettlementTextKeys.DORA_INDICATOR, (dora?.content as? TranslatableTextContent)?.key)
        assertEquals(WinSettlementTextKeys.URA_DORA_INDICATOR, (ura?.content as? TranslatableTextContent)?.key)
        assertNull(presenter.detailLabel("custom:rule", RiichiWinSettlementIds.DORA_FIELD, templates))
        assertNull(presenter.detailLabel("mahjongcraft:riichi", "custom:field", templates))
        assertNull(presenter.detailLabel(null, RiichiWinSettlementIds.DORA_FIELD, templates))
    }

    /** 分差保留正負號、零變化及柔和色彩，負數最小值不溢位。 */
    @Test
    fun `score changes preserve signs and colors`() {
        val presenter = presenter()
        assertEquals("+1100", presenter.scoreChangeText(1100).string)
        assertEquals("−500", presenter.scoreChangeText(-500).string)
        assertEquals("±0", presenter.scoreChangeText(0).string)
        assertEquals("−2147483648", presenter.scoreChangeText(Int.MIN_VALUE).string)
        assertEquals(0x91c998, presenter.scoreChangeText(1).style.color?.rgb)
        assertEquals(0xde9696, presenter.scoreChangeText(-1).style.color?.rgb)
        assertEquals(0xaaaaaa, presenter.scoreChangeText(0).style.color?.rgb)
    }

    /** 多家和牌保留各自詳情，不依座位排列將役種交給另一位玩家。 */
    @Test
    fun `multiple winners keep their own details and transaction deltas`() {
        val first = HistoryWinDetailFieldDto("custom:points", HistoryWinDetailValueDto.Quantities(listOf(HistoryWinDetailQuantityDto("custom:point", 3))))
        val second = HistoryWinDetailFieldDto("custom:patterns", HistoryWinDetailValueDto.Entries(listOf(HistoryWinDetailValueDto.Entries.EntryDto("custom:pattern", quantity = null))))
        val outcome = HistoryRoundOutcomeDto(
            "mahjongcraft:ron",
            listOf(1, 2),
            mapOf(0 to 20000, 1 to 28000, 2 to 27000),
            RoundCompletionClassification.WIN.name,
            listOf(0),
            null,
            scoreChangesBySeat = mapOf(0 to -5000, 1 to 3000, 2 to 2000),
            winnerDetails = listOf(HistoryWinnerDetailsDto(2, listOf(second), hand = null), HistoryWinnerDetailsDto(1, listOf(first), hand = null)),
            hasEarlierWinSettlement = false,
        )
        val result = present(outcome)
        assertEquals(listOf(-5000, 3000, 2000), result.rows.map { it.scoreChange })
        assertTrue(result.rows[0].detailFields.isEmpty())
        assertEquals(listOf(first), result.rows[1].detailFields)
        assertEquals(listOf(second), result.rows[2].detailFields)
    }

    /** 缺少分差或和牌明細的歷史不從初始分數猜測，也不補出役種。 */
    @Test
    fun `missing saved information remains absent`() {
        val result = present(HistoryRoundOutcomeDto("mahjongcraft:tsumo", listOf(0), mapOf(0 to 26100), RoundCompletionClassification.WIN.name, emptyList(), null, scoreChangesBySeat = emptyMap(), winnerDetails = emptyList(), hasEarlierWinSettlement = false))
        assertNull(result.rows[0].scoreChange)
        assertTrue(result.rows.all { it.detailFields.isEmpty() })
    }

    /** 日麻詳情依登記的格式化器轉成既有翻譯鍵與參數。 */
    @Test
    fun `riichi detail values use the registered formatters`() {
        val templates = WinSettlementPresentationTemplateRegistryImpl().apply {
            BuiltInMinecraftMahjongExtension.registerWinSettlementPresentationTemplates(this)
            BundledMinecraftMahjongExtensions.all.forEach { it.registerWinSettlementPresentationTemplates(this) }
        }
        val yaku = HistoryWinDetailValueDto.Entries(
            listOf(
                HistoryWinDetailValueDto.Entries.EntryDto(RiichiWinSettlementIds.yaku(YakuType.Riichi), HistoryWinDetailQuantityDto(RiichiWinSettlementIds.HAN, 1)),
                HistoryWinDetailValueDto.Entries.EntryDto(RiichiWinSettlementIds.yaku(YakuType.Daisangen), HistoryWinDetailQuantityDto(RiichiWinSettlementIds.YAKUMAN, 2)),
            ),
        )
        val rows = presenter().detailText(RiichiWinSettlementIds.YAKU_FIELD, yaku, templates)
        assertEquals(RiichiYakuTranslationKeys.keyFor(YakuType.Riichi), (rows[0].content as TranslatableTextContent).key)
        val han = rows[0].siblings.last().content as TranslatableTextContent
        assertEquals(WinSettlementTextKeys.HAN, han.key)
        assertEquals(listOf("1"), han.args.toList())
        assertEquals(RiichiYakuTranslationKeys.keyFor(YakuType.Daisangen), (rows[1].content as TranslatableTextContent).key)
        assertEquals("mahjongcraft.game.score.yakuman_2x", (rows[1].siblings.last().content as TranslatableTextContent).key)

        val hanFu = HistoryWinDetailValueDto.Quantities(listOf(HistoryWinDetailQuantityDto(RiichiWinSettlementIds.HAN, 3), HistoryWinDetailQuantityDto(RiichiWinSettlementIds.FU, 30)))
        val summary = presenter().detailText(RiichiWinSettlementIds.HAN_FU_FIELD, hanFu, templates).single().content as TranslatableTextContent
        assertEquals(WinSettlementTextKeys.HAN_FU, summary.key)
        assertEquals(listOf("3", "30"), summary.args.toList())
    }

    /** 沒有登記格式化器的第三方欄位以 ID 與原始數值顯示，不套用日麻翻數。 */
    @Test
    fun `unregistered detail values show identifiers and raw amounts`() {
        val templates = WinSettlementPresentationTemplateRegistryImpl().apply {
            BuiltInMinecraftMahjongExtension.registerWinSettlementPresentationTemplates(this)
            BundledMinecraftMahjongExtensions.all.forEach { it.registerWinSettlementPresentationTemplates(this) }
        }
        val value = HistoryWinDetailValueDto.Entries(listOf(HistoryWinDetailValueDto.Entries.EntryDto("custom:pattern", HistoryWinDetailQuantityDto("custom:point", 7))))
        val row = presenter().detailText("custom:patterns", value, templates).single()
        assertEquals("custom:pattern", (row.content as TranslatableTextContent).key)
        assertTrue(row.string.endsWith("7 custom:point"))
    }

    /** 建立不包含規則專用邏輯的測試呈現來源。
     * @return 空動作與流局名稱 registry 的 presenter。
     */
    private fun presenter(): HistoryRoundEventPresenter = HistoryRoundEventPresenter(GameActionVocabularyRegistryImpl(), ExhaustiveDrawReasonDisplayNameRegistryImpl(), RoundOutcomeDisplayNameRegistryImpl())

    /** 建立單次和牌結算的事件呈現資料。
     * @param outcome 已保存的安全結算資料。
     * @return 依初始座位排列的結算呈現結果。
     */
    private fun present(outcome: HistoryRoundOutcomeDto): HistoryOutcomePresentation {
        val events = HistoryRoundEventsDto(
            HistoryReplayIdentityDto("match", "table", (0..2).map { HistoryReplayPlayerIdentityDto(it, null, null) }),
            1,
            listOf(HistoryReplayTransactionDto(0, 100L, false, 0, listOf(HistoryReplayFactDto.Completion(HistoryFactTypeKeys.WIN_SETTLED, outcome)))),
            null,
            emptyList(),
        )
        val fact = presenter().present(events, "custom:rule").transactions.single().facts.single()
        assertEquals(MinecraftHistoryScreenKeys.ROUND_WIN_SETTLEMENT, (fact.text.content as TranslatableTextContent).key)
        return checkNotNull(fact.outcome)
    }
}
