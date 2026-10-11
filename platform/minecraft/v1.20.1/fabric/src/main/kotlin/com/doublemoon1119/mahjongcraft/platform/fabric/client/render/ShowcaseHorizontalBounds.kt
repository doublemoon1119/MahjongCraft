package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

/**
 * 一翼相對於自身中心的水平範圍。
 *
 * @property minX 左緣。
 * @property maxX 右緣。
 */
data class ShowcaseHorizontalBounds(
    val minX: Double,
    val maxX: Double,
) {
    /** 寬度。 */
    val width: Double get() = maxX - minX
}
