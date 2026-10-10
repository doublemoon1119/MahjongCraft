package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/**
 * 依欄位 ID 取得格式化器；沒有登記時使用 [FallbackWinSettlementDetailTextFormatter]。
 *
 * @param fieldId 胡牌詳情欄位 ID。
 * @return 該欄位的格式化器。
 */
fun WinSettlementPresentationTemplateRegistry.detailTextFormatter(fieldId: String): WinSettlementDetailTextFormatter = findDetailTextFormatter(fieldId) ?: FallbackWinSettlementDetailTextFormatter
