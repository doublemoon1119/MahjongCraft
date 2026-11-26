package com.doublemoon1119.mahjongcraft.flow.common.game.event

/**
 * 完整規則模組 ID 與公開事件標記解析器的註冊定義。
 *
 * @property ruleModuleId 完整規則模組識別碼。
 * @property resolver 對應規則模組的標記解析器。
 */
data class GameEventPublicMarkerResolverDefinition(
    val ruleModuleId: String,
    val resolver: GameEventPublicMarkerResolver,
)
