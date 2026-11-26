package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/**
 * MahjongCraft 內建胡牌付款原因（[WinSettlementResult.paymentReasonIdsByPlayerId]）的穩定識別碼。
 *
 * 不屬於任何單一規則：概念相同的規則共用同一個 ID，呈現層也只需登記一次顯示設定。
 */
object BuiltInPaymentReasonIds {
    /** 包牌：玩家因責任歸屬而替其他人承擔付款。 */
    val PAO: String = MahjongCraftMetadata.id("pao")
}
