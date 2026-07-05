package com.doublemoon1119.mahjongcraft.logic.base

/**
 * [Hand] 的對稱快照，用於 Client 端渲染
 */
data class HandSnapshot(
    val standingTiles: List<IdentifiedTileSnapshot>,
    var lastDrawn: IdentifiedTileSnapshot? = null
)

/**
 * 產生一個 [Hand] 的快照
 */
fun Hand.toSnapshot(isVisible: Boolean): HandSnapshot {
    return HandSnapshot(
        standingTiles = this.standingTiles.map { it.toSnapshot(isVisible) },
        lastDrawn = this.lastDrawn?.toSnapshot(isVisible)
    )
}