package com.doublemoon1119.mahjongcraft.model

/**
 * 代表一場遊戲中的牌山。
 *
 * @property tiles 牌山中剩餘的牌，採用可變列表以支援摸牌動作。
 */
class TileWall(private val tiles: MutableList<Tile>) {

    /**
     * 剩餘牌數。
     */
    val remainingCount: Int get() = tiles.size

    /**
     * 進行洗牌。
     */
    fun shuffle() {
        tiles.shuffle()
    }

    /**
     * 從牌山中摸取一張牌。
     * * @return 摸到的 [Tile]，若牌山已空則返回 null。
     */
    fun draw(): Tile? {
        if (tiles.isEmpty()) return null
        return tiles.removeAt(0)
    }

    /**
     * 獲取目前牌山的唯讀列表（用於調試或顯示）。
     */
    fun getAllTiles(): List<Tile> = tiles.toList()
}