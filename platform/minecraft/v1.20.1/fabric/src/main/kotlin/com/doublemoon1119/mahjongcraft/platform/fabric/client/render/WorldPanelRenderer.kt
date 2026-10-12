package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import kotlin.math.roundToInt

/** 常駐世界面板共用的單一深色背景、動態測量與文字繪製 helper。 */
object WorldPanelRenderer {
    /**
     * 在目前矩陣下繪製指定顏色的矩形背景。
     *
     * @param left 矩形左界。
     * @param top 矩形頂端。
     * @param right 矩形右界。
     * @param bottom 矩形底端。
     * @param color ARGB 背景顏色。
     * @param z 矩形深度。
     * @param matrices 目前的座標轉換矩陣。
     * @param consumers 接收矩形頂點的繪製緩衝來源。
     */
    fun drawBackground(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        color: Int,
        z: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) {
        val matrix = matrices.peek().positionMatrix
        val buffer = consumers.getBuffer(ExhaustiveDrawSettlementPanelRenderLayer.layer)
        val alpha = color ushr 24 and 0xFF
        val red = color ushr 16 and 0xFF
        val green = color ushr 8 and 0xFF
        val blue = color and 0xFF
        buffer.vertex(matrix, left, top, z).color(red, green, blue, alpha).next()
        buffer.vertex(matrix, right, top, z).color(red, green, blue, alpha).next()
        buffer.vertex(matrix, right, bottom, z).color(red, green, blue, alpha).next()
        buffer.vertex(matrix, left, bottom, z).color(red, green, blue, alpha).next()
    }

    /**
     * 在指定深度繪製不帶陰影的文字，完成後還原矩陣堆疊。
     *
     * @param renderer 文字繪製器。
     * @param text 要繪製的文字。
     * @param x 文字左界。
     * @param y 文字頂端。
     * @param color 文字顏色。
     * @param z 相對目前矩陣的深度偏移。
     * @param light Minecraft 的打包光照值。
     * @param matrices 目前的座標轉換矩陣。
     * @param consumers 接收文字頂點的繪製緩衝來源。
     */
    fun drawText(
        renderer: TextRenderer,
        text: Text,
        x: Float,
        y: Float,
        color: Int,
        z: Float,
        light: Int,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) {
        matrices.push()
        matrices.translate(0.0, 0.0, z.toDouble())
        renderer.draw(text, x, y, color, false, matrices.peek().positionMatrix, consumers, TextRenderer.TextLayerType.NORMAL, 0, light)
        matrices.pop()
    }

    /**
     * 依實際字型像素寬度截斷並補 `...`；寬度連省略號都容納不了時仍保留省略號。
     *
     * @param renderer 測量文字寬度的繪製器。
     * @param value 原始文字。
     * @param maxWidth 可用的像素寬度。
     * @return 原文或截斷後附省略號的文字。
     */
    fun fitText(renderer: TextRenderer, value: String, maxWidth: Int): String {
        if (renderer.getWidth(value) <= maxWidth) return value
        val suffix = "..."
        return renderer.trimToWidth(value, (maxWidth - renderer.getWidth(suffix)).coerceAtLeast(0)) + suffix
    }

    /**
     * 將 RGB 顏色與指定透明度組成 ARGB 顏色，取代輸入原有的透明度。
     *
     * @param rgb 使用低 24 位元的 RGB 顏色。
     * @param alpha 透明度；限制在 0 到 1 後換算成 8 位元。
     * @return 組合後的 ARGB 顏色。
     */
    fun withAlpha(rgb: Int, alpha: Float): Int = ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (rgb and 0xFFFFFF)

    /**
     * 計算淡入、持續顯示與淡出三階段的透明度；區間外完全透明。
     *
     * @param value 目前進度。
     * @param inStart 淡入起點。
     * @param inEnd 淡入終點；呼叫端須確保各端點依序排列。
     * @param outStart 淡出起點。
     * @param outEnd 淡出終點，抵達時完全透明。
     * @return 各階段線性插值的透明度。
     */
    fun phaseAlpha(value: Double, inStart: Double, inEnd: Double, outStart: Double, outEnd: Double): Float = when {
        value < inStart || value >= outEnd -> 0f
        value < inEnd -> ((value - inStart) / (inEnd - inStart)).toFloat()
        value > outStart -> ((outEnd - value) / (outEnd - outStart)).toFloat()
        else -> 1f
    }
}
