package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementTranslationKeys
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayFactDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayIdentityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayPlayerIdentityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayTransactionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundOutcomeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryWinDetailFieldDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryWinDetailValueDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryWinnerDetailsDto
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.registerBuiltInWinSettlementTemplates
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
        val templates = WinSettlementPresentationTemplateRegistryImpl().apply { registerBuiltInWinSettlementTemplates() }
        val presenter = presenter()
        val dora = presenter.detailLabel("mahjongcraft:riichi", "mahjongcraft:riichi_dora", templates)
        val ura = presenter.detailLabel("mahjongcraft:riichi", "mahjongcraft:riichi_ura_dora", templates)
        assertEquals(WinSettlementTranslationKeys.DORA_INDICATOR, (dora?.content as? TranslatableTextContent)?.key)
        assertEquals(WinSettlementTranslationKeys.URA_DORA_INDICATOR, (ura?.content as? TranslatableTextContent)?.key)
        assertNull(presenter.detailLabel("custom:rule", "mahjongcraft:riichi_dora", templates))
        assertNull(presenter.detailLabel("mahjongcraft:riichi", "custom:field", templates))
        assertNull(presenter.detailLabel(null, "mahjongcraft:riichi_dora", templates))
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
        val first = HistoryWinDetailFieldDto("custom:points", HistoryWinDetailValueDto.Text("custom.score.points", listOf("3")))
        val second = HistoryWinDetailFieldDto("custom:patterns", HistoryWinDetailValueDto.Entries(listOf(HistoryWinDetailValueDto.Entries.EntryDto("custom.pattern"))))
        val outcome = HistoryRoundOutcomeDto(
            "mahjongcraft:ron",
            listOf(1, 2),
            mapOf(0 to 20000, 1 to 28000, 2 to 27000),
            RoundCompletionClassification.WIN.name,
            listOf(0),
            null,
            scoreChangesBySeat = mapOf(0 to -5000, 1 to 3000, 2 to 2000),
            winnerDetails = listOf(HistoryWinnerDetailsDto(2, "custom:rule", listOf(second)), HistoryWinnerDetailsDto(1, "custom:rule", listOf(first))),
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
        val result = present(HistoryRoundOutcomeDto("mahjongcraft:tsumo", listOf(0), mapOf(0 to 26100), RoundCompletionClassification.WIN.name, emptyList(), null))
        assertNull(result.rows[0].scoreChange)
        assertTrue(result.rows.all { it.detailFields.isEmpty() })
    }

    /** 翻譯單位由規則傳入，不將第三方條目套用日麻翻數。 */
    @Test
    fun `detail entries preserve rule supplied units and arguments`() {
        val value = HistoryWinDetailValueDto.Entries(
            listOf(
                HistoryWinDetailValueDto.Entries.EntryDto("custom.pattern", trailingTranslationKey = "custom.points", trailingTranslationArgument = "7"),
                HistoryWinDetailValueDto.Entries.EntryDto("custom.other", trailingText = "3 stars"),
            ),
        )
        val rows = presenter().detailText(value)
        assertEquals("custom.pattern", (rows[0].content as TranslatableTextContent).key)
        val trailing = rows[0].siblings.last().content as TranslatableTextContent
        assertEquals("custom.points", trailing.key)
        assertEquals(listOf("7"), trailing.args.toList())
        assertTrue(rows[1].string.endsWith("3 stars"))
        val summary = presenter().detailText(HistoryWinDetailValueDto.Text("custom.summary", listOf("7", "bonus"))).single()
        assertEquals(listOf("7", "bonus"), (summary.content as TranslatableTextContent).args.toList())
    }

    /** 建立不包含規則專用邏輯的測試呈現來源。
     * @return 空動作與流局名稱 registry 的 presenter。
     */
    private fun presenter(): HistoryRoundEventPresenter = HistoryRoundEventPresenter(GameActionVocabularyRegistryImpl(), ExhaustiveDrawReasonDisplayNameRegistryImpl())

    /** 建立單次和牌結算的事件呈現資料。
     * @param outcome 已保存的安全結算資料。
     * @return 依初始座位排列的結算呈現結果。
     */
    private fun present(outcome: HistoryRoundOutcomeDto): HistoryOutcomePresentation {
        val events = HistoryRoundEventsDto(
            HistoryReplayIdentityDto("match", "table", (0..2).map { HistoryReplayPlayerIdentityDto(it, null, null) }),
            1,
            listOf(HistoryReplayTransactionDto(0, 100L, false, 0, listOf(HistoryReplayFactDto.Completion("win_settled", outcome)))),
            null,
            emptyList(),
        )
        val fact = presenter().present(events, "custom:rule").transactions.single().facts.single()
        assertEquals(MinecraftHistoryScreenKeys.ROUND_WIN_SETTLEMENT, (fact.text.content as TranslatableTextContent).key)
        return checkNotNull(fact.outcome)
    }
}
