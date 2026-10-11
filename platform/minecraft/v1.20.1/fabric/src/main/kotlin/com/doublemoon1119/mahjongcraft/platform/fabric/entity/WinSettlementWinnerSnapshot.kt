package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationFieldId

/**
 * 贏家的和牌內容與結算詳情快照。
 *
 * @property playerId 玩家識別碼。
 * @property seatIndex 座位索引。
 * @property isAi 是否由 AI 操控。
 * @property responsiblePlayerId 責任玩家識別碼；無責任玩家時為 null。
 * @property totalScore 和牌總點數。
 * @property handAssetKeys 立牌的牌面資源識別碼。
 * @property melds 依序排列的副露。
 * @property winningTileAssetKey 獨立和牌張的牌面資源識別碼。
 * @property details 規則提供的結算詳情欄位。
 */
data class WinSettlementWinnerSnapshot(
    val playerId: String,
    val seatIndex: Int,
    val isAi: Boolean,
    val responsiblePlayerId: String?,
    val totalScore: Int,
    val handAssetKeys: List<String>,
    val melds: List<WinSettlementMeldSnapshot>,
    val winningTileAssetKey: String,
    val details: List<WinSettlementDetailSnapshot>,
) {
    /** 這位贏家實際有內容的細項欄位；版面裡「有資料才顯示」的部分以此判斷。 */
    val presentFieldIds: Set<PresentationFieldId>
        get() = details.mapNotNullTo(mutableSetOf()) { detail -> runCatching { PresentationFieldId(detail.id) }.getOrNull() }
}
