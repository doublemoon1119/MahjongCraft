package com.doublemoon1119.mahjongcraft.logic.table.layout

/** 可依序取得保留區各墩位置的內部軌道。 */
internal interface ReservedWallTrack {
    /**
     * 取得軌道上第 [index] 墩指定 [layer] 的位置。
     *
     * @param index 軌道墩序列中的零基底索引。
     * @param layer 墩內層數，只接受 0 或 1。
     * @return 對應的牌牆位置。
     */
    fun position(index: Int, layer: Int): TileWallPosition
}
