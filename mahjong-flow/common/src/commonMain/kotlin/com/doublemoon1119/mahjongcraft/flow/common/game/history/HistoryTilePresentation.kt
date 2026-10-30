package com.doublemoon1119.mahjongcraft.flow.common.game.history

import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewSet
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose

/**
 * 一張牌在某筆交易提交後解算出的呈現結果；讀牌譜時直接使用，不重新執行規則。
 *
 * @property appearanceId 外觀樣式，例如赤五；沒有時為 null。
 * @property views 牌面可公開的特徵。
 * @property pose 共同姿態。
 * @property audience 當時知道牌面的讀取者。
 */
data class HistoryTilePresentation(
    val appearanceId: String?,
    val views: ComponentViewSet,
    val pose: TilePose,
    val audience: HistoryTileAudience,
)
