package com.doublemoon1119.mahjongcraft.logic.table.layout

/**
 * 同一段整體動作中的牌張移動集合。
 *
 * 同一階段允許平台為避免實體碰撞而細微錯開個別牌張的起步時間，但不得改變階段順序或最終位置。
 */
data class PhysicalWallLayoutTransitionPhase(val moves: List<PhysicalWallTileMove>) {
    init {
        require(moves.isNotEmpty()) { "Physical wall transition phase must contain at least one move" }
        require(moves.map { it.tileId }.distinct().size == moves.size) {
            "Physical wall transition phase must not move the same tile more than once"
        }
    }
}
