package com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue

import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/**
 * 各規則共用的目錄分類代號。規則使用同一個代號時，規則一覽與其他畫面能以相同方式找到並排列該分類。
 */
object BuiltInRuleCatalogueCategoryIds {
    /** 「基本玩法」：牌與手牌、和牌條件、賽程等規則流程。規則一覽把它排在「全部分類」之前。 */
    val BASICS: String = MahjongCraftMetadata.id("basics")
}
