package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.toSnapshot
import kotlin.uuid.Uuid

/**
 * 不分規則的牌河快照，公開內容與權威牌河分開。
 *
 * @property entries 依捨牌順序排列、已裁切的紀錄。
 * @property sidewaysTileId 規則要求側放的牌；沒有時為 null。
 */
data class DiscardPileSnapshot(
    val entries: List<DiscardEntrySnapshot>,
    val sidewaysTileId: Uuid?,
) : SidewaysMarkedDiscardPile {
    override fun sidewaysMarkedTileId(): Uuid? = sidewaysTileId
}

/**
 * 建立預設公開的牌河快照，不複製原始組件。
 *
 * 對外入口仍須依規則的逐張授權取代牌面，不能因為位於牌河就略過可見性判定。
 *
 * @return 只包含公開紀錄標記的牌河。
 */
fun DiscardPile<*>.toSnapshot(): DiscardPileSnapshot = DiscardPileSnapshot(
    entries = entries.map { DiscardEntrySnapshot(it.tile.toSnapshot(true), it.isTaken, it.publicMarkerIds.toSet()) },
    sidewaysTileId = (this as? SidewaysMarkedDiscardPile)?.sidewaysMarkedTileId(),
)
