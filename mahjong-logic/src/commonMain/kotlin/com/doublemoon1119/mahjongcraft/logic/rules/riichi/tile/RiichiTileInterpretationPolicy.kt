package com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.base.TileValue
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.tile.TileInterpretationPolicy

/**
 * 日麻的牌面解讀：基本牌面直接比較，赤五的基本牌面就是普通五。
 *
 * 計算用牌值只保留赤寶牌特性；外觀、授權與姿態不影響計算與張數分組。
 */
object RiichiTileInterpretationPolicy : TileInterpretationPolicy {
    override fun valueOf(face: TileFaceView): TileValue = TileValue(
        tile = face.tile,
        components = if (face.views[RiichiTileComponentViews.RED_DORA] != null) {
            ComponentSet.EMPTY.with(RiichiTileComponents.RED_DORA, Unit)
        } else {
            ComponentSet.EMPTY
        },
    )

    override fun valueOf(value: TileValue): TileValue = TileValue(
        tile = value.tile,
        components = if (value.components[RiichiTileComponents.RED_DORA] != null) {
            ComponentSet.EMPTY.with(RiichiTileComponents.RED_DORA, Unit)
        } else {
            ComponentSet.EMPTY
        },
    )

    override fun isUnavailableForDraw(face: TileFaceView): Boolean = face.views[RiichiTileComponentViews.DORA_INDICATOR] != null

    override fun canonicalize(tile: Tile): Tile = tile
}
