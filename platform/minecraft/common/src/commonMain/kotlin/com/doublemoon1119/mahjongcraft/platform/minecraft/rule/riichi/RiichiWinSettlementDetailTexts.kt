package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiWinSettlementIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.FallbackWinSettlementDetailTextFormatter
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementDetailTextFormatter
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementTextKeys

/** 內建日麻胡牌詳情的顯示文字。 */
internal object RiichiWinSettlementDetailTexts {
    /** 有專屬名稱的役滿倍數上限；超過時使用 [YAKUMAN_N_KEY]。 */
    private const val NAMED_YAKUMAN_MULTIPLIER_MAX = 6

    /** 役滿倍數 translation key 的共用前綴。 */
    private const val YAKUMAN_KEY_PREFIX = MinecraftModMetadata.MOD_ID + ".game.score.yakuman_"

    /** 沒有專屬名稱的役滿倍數 translation key；參數為倍數。 */
    private const val YAKUMAN_N_KEY = YAKUMAN_KEY_PREFIX + "nx"

    /** 役種欄位：役種名稱，右側為翻數或役滿倍數；役滿條目以強調樣式顯示。 */
    val yaku: WinSettlementDetailTextFormatter = object : WinSettlementDetailTextFormatter {
        override fun quantities(quantities: List<WinSettlementQuantity>): PresentationValue.TextValue = FallbackWinSettlementDetailTextFormatter.quantities(quantities)

        override fun entries(entries: List<WinSettlementDetailEntry>): PresentationValue.EntryListValue = PresentationValue.EntryListValue(
            entries.map { entry ->
                val name = RiichiYakuTranslationKeys.keyForEntry(entry.id) ?: entry.id
                val quantity = entry.quantity
                when (quantity?.unitId) {
                    RiichiWinSettlementIds.HAN -> PresentationValue.EntryListValue.Entry(
                        translationKey = name,
                        trailingTranslationKey = WinSettlementTextKeys.HAN,
                        trailingTranslationArgument = quantity.amount.toString(),
                    )
                    RiichiWinSettlementIds.YAKUMAN -> yakumanText(quantity.amount).let { text ->
                        PresentationValue.EntryListValue.Entry(
                            translationKey = name,
                            trailingTranslationKey = text.translationKey,
                            trailingTranslationArgument = text.arguments.singleOrNull(),
                            highlighted = true,
                        )
                    }
                    null -> PresentationValue.EntryListValue.Entry(translationKey = name)
                    else -> FallbackWinSettlementDetailTextFormatter.entries(listOf(entry)).entries.single().copy(translationKey = name)
                }
            },
        )
    }

    /** 翻符欄位：有符數時為「N翻 M符」，否則為「N 翻」。 */
    val hanFu: WinSettlementDetailTextFormatter = quantitiesFormatter { quantities ->
        val han = quantities.singleOrNull { it.unitId == RiichiWinSettlementIds.HAN }
        val fu = quantities.singleOrNull { it.unitId == RiichiWinSettlementIds.FU }
        when {
            han == null || quantities.size != listOfNotNull(han, fu).size -> null
            fu != null -> PresentationValue.TextValue(WinSettlementTextKeys.HAN_FU, listOf(han.amount.toString(), fu.amount.toString()))
            else -> PresentationValue.TextValue(WinSettlementTextKeys.HAN, listOf(han.amount.toString()))
        }
    }

    /** 役滿合計欄位：役滿、兩倍役滿等倍數名稱。 */
    val yakumanTotal: WinSettlementDetailTextFormatter = quantitiesFormatter { quantities ->
        quantities.singleOrNull()?.takeIf { it.unitId == RiichiWinSettlementIds.YAKUMAN }?.let { yakumanText(it.amount) }
    }

    /**
     * 役滿倍數的顯示文字。
     *
     * @param multiplier 役滿倍數。
     * @return 1～6 倍使用專屬名稱，其餘使用帶倍數參數的通用名稱。
     */
    private fun yakumanText(multiplier: Int): PresentationValue.TextValue = if (multiplier in 1..NAMED_YAKUMAN_MULTIPLIER_MAX) {
        PresentationValue.TextValue("${YAKUMAN_KEY_PREFIX}${multiplier}x")
    } else {
        PresentationValue.TextValue(YAKUMAN_N_KEY, listOf(multiplier.toString()))
    }

    /**
     * 建立只處理數值的格式化器；無法辨識的數值與條目使用通用格式。
     *
     * @param format 回傳 null 表示無法辨識。
     * @return 格式化器。
     */
    private fun quantitiesFormatter(format: (List<WinSettlementQuantity>) -> PresentationValue.TextValue?): WinSettlementDetailTextFormatter = object : WinSettlementDetailTextFormatter {
        override fun quantities(quantities: List<WinSettlementQuantity>): PresentationValue.TextValue = format(quantities) ?: FallbackWinSettlementDetailTextFormatter.quantities(quantities)

        override fun entries(entries: List<WinSettlementDetailEntry>): PresentationValue.EntryListValue = FallbackWinSettlementDetailTextFormatter.entries(entries)
    }
}

/** 登記內建日麻胡牌詳情欄位的顯示文字。 */
internal fun WinSettlementPresentationTemplateRegistry.registerRiichiWinSettlementDetailTexts() {
    registerDetailTextFormatter(RiichiWinSettlementIds.YAKU_FIELD, RiichiWinSettlementDetailTexts.yaku)
    registerDetailTextFormatter(RiichiWinSettlementIds.HAN_FU_FIELD, RiichiWinSettlementDetailTexts.hanFu)
    registerDetailTextFormatter(RiichiWinSettlementIds.YAKUMAN_TOTAL_FIELD, RiichiWinSettlementDetailTexts.yakumanTotal)
}
