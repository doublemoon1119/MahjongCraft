package com.doublemoon1119.mahjongcraft.logic.table.layout

/**
 * 可沿完整牌牆跨越桌角的連續保留區軌道。
 *
 * @property stackPositions 由開門缺口一側往保留區尾端排列的墩位置，層數固定為 0。
 */
internal data class CircularReservedWallTrack(
    val stackPositions: List<TileWallPosition>,
) : ReservedWallTrack {
    init {
        require(stackPositions.isNotEmpty()) { "Reserved wall track must contain at least one stack" }
        require(stackPositions.all { it.side >= 0 && it.stack >= 0 && it.layer == 0 }) {
            "Reserved wall track must contain valid lower-layer stack positions"
        }
    }

    override fun position(index: Int, layer: Int): TileWallPosition {
        require(layer in 0..1) { "Reserved wall track layer must be 0 or 1" }
        return stackPositions[index].copy(layer = layer)
    }
}
