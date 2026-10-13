package com.doublemoon1119.mahjongcraft.flow.server.game.service

/**
 * 完整規則模組 ID 與胡牌詳情解析器的註冊定義。
 *
 * @property ruleModuleId 規則模組識別碼。
 * @property resolver 對應規則的詳情解析器。
 */
data class WinSettlementDetailResolverDefinition(
    val ruleModuleId: String,
    val resolver: WinSettlementDetailResolver,
)
