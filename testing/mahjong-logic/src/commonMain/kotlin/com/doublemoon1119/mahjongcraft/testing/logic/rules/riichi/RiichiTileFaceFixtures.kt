package com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewSet
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileComponentViews
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileValues

/**
 * 建立帶有生效寶牌指示牌特徵的已知牌面。
 *
 * @param tile 指示牌的基本牌面。
 * @return 讀取者看到的指示牌牌面。
 */
fun riichiDoraIndicatorFace(tile: Tile): TileFaceView = TileFaceView(
    tile = tile,
    views = ComponentViewSet.EMPTY.with(RiichiTileComponentViews.DORA_INDICATOR, Unit),
)

/**
 * 建立讀取者已知的赤五牌面，含赤寶牌特徵與赤五外觀。
 *
 * @param suit 赤五的花色。
 * @return 赤五的已知牌面。
 */
fun riichiRedFiveFace(suit: Tile.Suit): TileFaceView = TileFaceView(
    tile = Tile.Numeric(suit, 5),
    views = ComponentViewSet.EMPTY.with(RiichiTileComponentViews.RED_DORA, Unit),
    appearanceId = RiichiTileValues.RED_FIVE_APPEARANCE_ID,
)
