package com.doublemoon1119.mahjongcraft.platform.fabric.entity

/**
 * 單一玩家在流局結算排行榜中的可持久化關鍵影格。
 *
 * @property playerId 玩家識別碼。
 * @property seatIndex 座位索引。
 * @property wind 玩家座風名稱。
 * @property isAi 是否由 AI 操控。
 * @property previousScore 結算前分數。
 * @property currentScore 結算後分數。
 * @property previousRank 結算前名次。
 * @property currentRank 結算後名次。
 * @property statusId 流局狀態識別碼；沒有狀態時為 null。
 * @property revealedHandTileIds 公開手牌的牌實體識別碼。
 * @property revealedHandAssetKeys 公開手牌的牌面資源識別碼。
 * @property waitingTileAssetKeys 等待牌的牌面資源識別碼。
 */
data class ExhaustiveDrawSettlementPlayerSnapshot(
    val playerId: String,
    val seatIndex: Int,
    val wind: String,
    val isAi: Boolean,
    val previousScore: Int,
    val currentScore: Int,
    val previousRank: Int,
    val currentRank: Int,
    val statusId: String?,
    val revealedHandTileIds: List<String>,
    val revealedHandAssetKeys: List<String>,
    val waitingTileAssetKeys: List<String>,
)
