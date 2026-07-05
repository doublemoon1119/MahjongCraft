package com.doublemoon1119.mahjongcraft.testing.logic.base

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.Tile

/**
 * 用於單元測試的手牌實體工廠。
 *
 * 負責將簡單的 [Tile] 列表封裝為具備唯一標識的 [Hand] 實體，隱藏 UUID 生成細節。
 */
object FakeHandFactory {

    /**
     * 建立一個測試用的手牌。
     * @param tiles 手中的立牌列表，自動轉換為 [com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile]。
     * @param melds 副露列表，預設為空。
     * @param lastDrawn 最後摸入的牌，預設為空。
     */
    fun create(
        tiles: List<Tile> = emptyList(),
        melds: List<Meld> = emptyList(),
        lastDrawn: Tile? = null
    ): Hand {
        return Hand(
            tiles = tiles.map { FakeIdentifiedTileFactory.create(it) }.toMutableList(),
            melds = melds.toMutableList(),
            lastDrawn = lastDrawn?.let { FakeIdentifiedTileFactory.create(it) }
        )
    }
}