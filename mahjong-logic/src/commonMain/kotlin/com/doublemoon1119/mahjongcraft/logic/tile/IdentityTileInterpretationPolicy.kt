package com.doublemoon1119.mahjongcraft.logic.tile

import com.doublemoon1119.mahjongcraft.logic.base.Tile

/** 不具有規則特有牌面等價關係時使用的原樣解讀 policy。 */
object IdentityTileInterpretationPolicy : TileInterpretationPolicy {
    override fun canonicalize(tile: Tile): Tile = tile
}
