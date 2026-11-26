package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.TOOLTIP_BACKGROUND_MARGIN
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner
import org.joml.Vector2i
import org.joml.Vector2ic

/**
 * 場景提示框的位置：放在滑鼠指著的東西上方，上方放不下時依序改放下方、游標上方、游標下方，提示框不會蓋住指著的東西與游標。
 * 水平以游標為中心，碰到畫面邊緣才往內推。
 *
 * @property anchorTop 指著的東西在畫面上的上界。
 * @property anchorBottom 指著的東西在畫面上的下界。
 */
internal class GuideTooltipPlacement(
    private val anchorTop: Int,
    private val anchorBottom: Int,
) : ClientTooltipPositioner {
    /**
     * 計算提示框文字區左上角的位置。提示框背景比文字區四周多出 [TOOLTIP_BACKGROUND_MARGIN]。
     *
     * @param screenWidth 畫面寬度。
     * @param screenHeight 畫面高度。
     * @param x 游標水平位置。
     * @param y 游標垂直位置。
     * @param width 文字區寬度。
     * @param height 文字區高度。
     * @return 文字區左上角。
     */
    override fun positionTooltip(
        screenWidth: Int,
        screenHeight: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ): Vector2ic {
        val margin = TOOLTIP_BACKGROUND_MARGIN
        val left = (x - width / 2).coerceAtMost(screenWidth - width - margin).coerceAtLeast(margin)
        val candidates = listOf(
            anchorTop - GAP - margin - height,
            anchorBottom + GAP + margin,
            y - CURSOR_GAP - margin - height,
            y + CURSOR_GAP + margin,
        )
        val top = candidates.firstOrNull { it >= margin && it + height + margin <= screenHeight }
            ?: candidates.last().coerceAtMost(screenHeight - height - margin).coerceAtLeast(margin)
        return Vector2i(left, top)
    }

    /** 提示框與指著的東西、游標之間的距離。 */
    internal companion object {
        /** 提示框背景與指著的東西之間的距離。 */
        const val GAP: Int = 2

        /** 提示框背景與游標之間的距離，留出游標圖示的大小。 */
        const val CURSOR_GAP: Int = 12

        /** 提示框文字最寬佔畫面寬度的比例。 */
        const val MAXIMUM_WIDTH_RATIO: Float = 0.5f
    }
}
