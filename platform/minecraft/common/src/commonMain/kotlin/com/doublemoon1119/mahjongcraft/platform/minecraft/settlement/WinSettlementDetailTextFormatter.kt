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
    /** 轉換有單位的數值。 */
    fun quantities(quantities: List<WinSettlementQuantity>): PresentationValue.TextValue

    /** 轉換條目清單。 */
    fun entries(entries: List<WinSettlementDetailEntry>): PresentationValue.EntryListValue
}
