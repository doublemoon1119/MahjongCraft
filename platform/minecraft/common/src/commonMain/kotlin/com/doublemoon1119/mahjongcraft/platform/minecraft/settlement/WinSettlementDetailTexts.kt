package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity

/**
 * 把一個胡牌詳情欄位的語意值轉成結算面板與歷史畫面使用的文字。
 *
 * 規則輸出的是 ID 與數值，例如「役種 `mahjongcraft:riichi/yaku/tanyao`、1 翻」；格式化器決定它在 Minecraft 中顯示成哪個
 * translation key 與參數。
 */
interface WinSettlementDetailTextFormatter {
    /**
     * 轉換有單位的數值。
     *
     * @param quantities 依規則順序排列的數值。
     * @return 顯示文字。
     */
    fun quantities(quantities: List<WinSettlementQuantity>): PresentationValue.TextValue

    /**
     * 轉換條目清單。
     *
     * @param entries 依規則順序排列的條目。
     * @return 保留原順序的顯示條目。
     */
    fun entries(entries: List<WinSettlementDetailEntry>): PresentationValue.EntryListValue
}

/**
 * 沒有登記格式化器的欄位使用的格式：以 ID 與原始數值顯示，不崩潰也不冒用其他規則的文字。
 *
 * ID 放在 translation key 位置；Minecraft 找不到對應翻譯時直接顯示 key 字串，因此玩家看得到可辨識的 ID。
 */
object FallbackWinSettlementDetailTextFormatter : WinSettlementDetailTextFormatter {
    override fun quantities(quantities: List<WinSettlementQuantity>): PresentationValue.TextValue = PresentationValue.TextValue(
        quantities.joinToString(" ") { "${it.amount} ${it.unitId}" },
    )

    override fun entries(entries: List<WinSettlementDetailEntry>): PresentationValue.EntryListValue = PresentationValue.EntryListValue(
        entries.map { entry ->
            PresentationValue.EntryListValue.Entry(
                translationKey = entry.id,
                trailingText = entry.quantity?.let { "${it.amount} ${it.unitId}" }.orEmpty(),
            )
        },
    )
}

/**
 * 依欄位 ID 取得格式化器；沒有登記時使用 [FallbackWinSettlementDetailTextFormatter]。
 *
 * @param fieldId 胡牌詳情欄位 ID。
 * @return 該欄位的格式化器。
 */
fun WinSettlementPresentationTemplateRegistry.detailTextFormatter(fieldId: String): WinSettlementDetailTextFormatter = findDetailTextFormatter(fieldId) ?: FallbackWinSettlementDetailTextFormatter
