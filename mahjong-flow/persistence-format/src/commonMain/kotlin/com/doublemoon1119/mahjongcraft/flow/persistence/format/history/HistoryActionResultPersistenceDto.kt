package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import kotlinx.serialization.Serializable

/**
 * 動作提交後的相關索引資料；桌況另由同筆交易結果保存。
 *
 * @property affectedTileIds 動作直接影響的牌 UUID 字串。
 * @property newlyRevealedTileIds 因本次動作新公開的牌 UUID 字串。
 * @property remainingWallTileCount 動作完成後活牌牆剩餘牌數。
 * @property reservedWallTileIds 動作完成後保留區中的牌 UUID 字串。
 * @property scoresByPlayerId 動作完成後各玩家分數。
 * @property nextPlayerId 下一個行動玩家的 UUID 字串。
 */
@Serializable
data class HistoryActionResultPersistenceDto(
    val affectedTileIds: List<String>,
    val newlyRevealedTileIds: List<String>,
    val remainingWallTileCount: Int,
    val reservedWallTileIds: List<String>,
    val scoresByPlayerId: Map<String, Int>,
    val nextPlayerId: String,
)
