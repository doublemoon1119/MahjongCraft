package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/**
 * 版面節點量測出來的尺寸。
 *
 * @property width 寬度。
 * @property height 高度。
 */
data class PresentationNodeSize(
    val width: Float,
    val height: Float,
) {
    companion object {
        /** 不佔空間的尺寸，供解析不到內容的節點使用。 */
        val ZERO = PresentationNodeSize(0f, 0f)
    }
}
