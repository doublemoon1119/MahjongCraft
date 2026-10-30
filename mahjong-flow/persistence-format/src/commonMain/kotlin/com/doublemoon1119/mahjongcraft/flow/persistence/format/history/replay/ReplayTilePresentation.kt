package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewSet
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose

/**
 * 重播時一張牌目前的呈現結果，取自保存的呈現變化。
 *
 * @property appearanceId 外觀樣式；沒有時為 null。
 * @property views 可公開的牌面特徵。
 * @property pose 共同姿態。
 */
internal data class ReplayTilePresentation(
    val appearanceId: String?,
    val views: ComponentViewSet,
    val pose: TilePose,
)
