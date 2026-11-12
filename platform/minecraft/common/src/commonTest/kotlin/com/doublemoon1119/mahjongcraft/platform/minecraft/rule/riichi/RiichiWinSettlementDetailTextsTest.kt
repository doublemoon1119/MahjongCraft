package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiWinSettlementIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiYakuType
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.EntryEmphasis
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationValue
import kotlin.test.Test
import kotlin.test.assertEquals

/** [RiichiWinSettlementDetailTexts] 把日麻詳情語意值轉成既有翻譯鍵的測試。 */
class RiichiWinSettlementDetailTextsTest {
    /** 一般役種右側為翻數，役滿右側為倍數名稱並強調，流局滿貫沒有右側值。 */
    @Test
    fun `yaku entries show han yakuman multipliers and plain entries`() {
        val entries = RiichiWinSettlementDetailTexts.yaku.entries(
            listOf(
                RiichiWinSettlementIds.yakuEntry(RiichiYakuType.Tanyao, 1),
                RiichiWinSettlementIds.yakumanEntry(RiichiYakuType.Daisangen, 1),
                RiichiWinSettlementIds.yakumanEntry(RiichiYakuType.KokushiMusou13, 2),
                WinSettlementDetailEntry(RiichiWinSettlementIds.NAGASHI_MANGAN),
            ),
        ).entries

        assertEquals(
            listOf(
                PresentationValue.EntryListValue.Entry(
                    translationKey = RiichiYakuTranslationKeys.keyFor(RiichiYakuType.Tanyao),
                    trailingTranslationKey = RiichiTranslationKeys.SETTLEMENT_HAN,
                    trailingTranslationArgument = "1",
                ),
                PresentationValue.EntryListValue.Entry(
                    translationKey = RiichiYakuTranslationKeys.keyFor(RiichiYakuType.Daisangen),
                    trailingTranslationKey = "mahjongcraft.riichi.settlement.yakuman_1x",
                    emphasis = EntryEmphasis.HIGHLIGHTED,
                ),
                PresentationValue.EntryListValue.Entry(
                    translationKey = RiichiYakuTranslationKeys.keyFor(RiichiYakuType.KokushiMusou13),
                    trailingTranslationKey = "mahjongcraft.riichi.settlement.yakuman_2x",
                    emphasis = EntryEmphasis.HIGHLIGHTED,
                ),
                PresentationValue.EntryListValue.Entry(translationKey = RiichiYakuTranslationKeys.NAGASHI_MANGAN),
            ),
            entries,
        )
    }

    /** 沒有專屬名稱的役滿倍數使用帶倍數參數的通用名稱。 */
    @Test
    fun `large yakuman multipliers use the generic name with the multiplier`() {
        val total = RiichiWinSettlementDetailTexts.yakumanTotal.quantities(listOf(WinSettlementQuantity(RiichiWinSettlementIds.YAKUMAN, 7)))

        assertEquals(PresentationValue.TextValue("mahjongcraft.riichi.settlement.yakuman_nx", listOf("7")), total)
    }

    /** 有符數時顯示翻與符，否則只顯示翻。 */
    @Test
    fun `han fu shows fu only when present`() {
        assertEquals(
            PresentationValue.TextValue(RiichiTranslationKeys.SETTLEMENT_HAN_FU, listOf("3", "30")),
            RiichiWinSettlementDetailTexts.hanFu.quantities(
                listOf(WinSettlementQuantity(RiichiWinSettlementIds.HAN, 3), WinSettlementQuantity(RiichiWinSettlementIds.FU, 30)),
            ),
        )
        assertEquals(
            PresentationValue.TextValue(RiichiTranslationKeys.SETTLEMENT_HAN, listOf("6")),
            RiichiWinSettlementDetailTexts.hanFu.quantities(listOf(WinSettlementQuantity(RiichiWinSettlementIds.HAN, 6))),
        )
    }

    /** 無法辨識的單位與條目以 ID 與原始數值顯示。 */
    @Test
    fun `unrecognized units fall back to identifiers and raw amounts`() {
        assertEquals(
            PresentationValue.TextValue("2 custom:point"),
            RiichiWinSettlementDetailTexts.hanFu.quantities(listOf(WinSettlementQuantity("custom:point", 2))),
        )
        assertEquals(
            PresentationValue.EntryListValue.Entry(translationKey = "custom:pattern", trailingText = "4 custom:point"),
            RiichiWinSettlementDetailTexts.yaku.entries(listOf(WinSettlementDetailEntry("custom:pattern", WinSettlementQuantity("custom:point", 4)))).entries.single(),
        )
    }
}
