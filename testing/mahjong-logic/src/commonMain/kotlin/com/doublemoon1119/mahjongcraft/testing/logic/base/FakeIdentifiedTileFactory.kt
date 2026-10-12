package com.doublemoon1119.mahjongcraft.testing.logic.base

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import kotlin.uuid.Uuid

/** 建立測試用的 [IdentifiedTile]，可使用指定 UUID 或預設產生隨機 UUID。 */
object FakeIdentifiedTileFactory {
    /**
     * 建立一個 [IdentifiedTile]。
     * 預設會自動產生 [Uuid]，讓測試代碼只需關注牌值本身。
     *
     * @param tile 牌的種類與數值。
     * @param id 牌的 UUID，預設隨機產生。
     */
    fun create(
        tile: Tile,
        id: Uuid = Uuid.random(),
    ): IdentifiedTile = IdentifiedTile(id, tile)
}
