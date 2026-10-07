package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import net.minecraft.client.gui.tooltip.TooltipPositioner
import net.minecraft.util.Util
import org.joml.Vector2i
import org.joml.Vector2ic

/** 提示框背景超出內容上下緣的像素數。 */
internal const val TOOLTIP_BACKGROUND_MARGIN = 4

/**
 * 沿用 [base] 的位置，只在超出畫面上下緣時調整：放得下就推回畫面內，比畫面還高就依 [MarqueeTiming] 上下捲動。
 *
 * @property base 原本的提示框定位方式。
 * @property shownSinceMillis 提示框開始顯示的時間，以 [Util.getMeasuringTimeMs] 計。
 */
internal class ScreenFittingTooltipPositioner(
    private val base: TooltipPositioner,
    private val shownSinceMillis: Long,
) : TooltipPositioner {
    override fun getPosition(
        screenWidth: Int,
        screenHeight: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ): Vector2ic {
        val position = base.getPosition(screenWidth, screenHeight, x, y, width, height)
        val elapsedMillis = (Util.getMeasuringTimeMs() - shownSinceMillis).coerceAtLeast(0L)
        return Vector2i(
            position.x(),
            fitTooltipY(
                preferredY = position.y(),
                tooltipHeight = height,
                screenHeight = screenHeight,
                elapsedMillis = elapsedMillis,
            ),
        )
    }
}

/**
 * 計算提示框內容頂端的 y 座標，讓背景盡量留在畫面內。
 *
 * @param preferredY 原本定位方式給出的 y 座標。
 * @param tooltipHeight 提示框內容高度，不含背景邊緣。
 * @param screenHeight 畫面高度。
 * @param elapsedMillis 提示框開始顯示後經過的毫秒數；只在比畫面還高時用來捲動。
 * @return 放得下時為推回畫面內的 [preferredY]；比畫面還高時為從上緣開始、依跑馬燈節奏捲到下緣的位置。
 */
internal fun fitTooltipY(
    preferredY: Int,
    tooltipHeight: Int,
    screenHeight: Int,
    elapsedMillis: Long,
): Int {
    val top = TOOLTIP_BACKGROUND_MARGIN
    val lowest = screenHeight - TOOLTIP_BACKGROUND_MARGIN - tooltipHeight
    if (lowest >= top) return preferredY.coerceIn(top, lowest)
    return top - MarqueeTiming.offset(elapsedMillis, top - lowest)
}
