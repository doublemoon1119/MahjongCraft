package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTileSnapshot

/**
 * 一次捨牌對指定讀取者可見的內容，不含權威紀錄組件。
 *
 * @property tile 已裁切牌面的牌張。
 * @property isTaken 是否已被鳴走。
 * @property publicMarkerIds 規則明確開放的公開標記，不包含私有組件 ID 或值。
 */
data class DiscardEntrySnapshot(
    val tile: IdentifiedTileSnapshot,
    val isTaken: Boolean,
    val publicMarkerIds: Set<String>,
)
