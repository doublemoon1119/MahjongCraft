package com.doublemoon1119.mahjongcraft.testing.logic.base

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import java.util.*

object FakeIdentifiedTileFactory {
    /**
     * 建立一個 [IdentifiedTile]。
     * 預設會自動產生 [UUID]，讓測試代碼只需關注牌值本身。
     */
    fun create(
        tile: Tile,
        id: UUID = UUID.randomUUID()
    ): IdentifiedTile {
        return IdentifiedTile(id, tile)
    }
}