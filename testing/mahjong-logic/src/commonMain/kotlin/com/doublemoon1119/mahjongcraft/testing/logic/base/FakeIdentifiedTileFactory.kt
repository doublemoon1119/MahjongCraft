package com.doublemoon1119.mahjongcraft.testing.logic.base

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import kotlin.uuid.Uuid

object FakeIdentifiedTileFactory {
    /**
     * 建立一個 [IdentifiedTile]。
     * 預設會自動產生 [Uuid]，讓測試代碼只需關注牌值本身。
     */
    fun create(
        tile: Tile,
        id: Uuid = Uuid.random(),
    ): IdentifiedTile = IdentifiedTile(id, tile)
}
