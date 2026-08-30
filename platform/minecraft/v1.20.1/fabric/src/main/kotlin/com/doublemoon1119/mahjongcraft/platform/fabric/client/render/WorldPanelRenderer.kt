package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import kotlin.math.roundToInt

/** 常駐世界面板共用的單一深色背景、動態測量與文字繪製 helper。 */
object WorldPanelRenderer {
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

    /** 依實際字型像素寬度截斷並補 `...`。 */
    fun fitText(renderer: TextRenderer, value: String, maxWidth: Int): String {
        if (renderer.getWidth(value) <= maxWidth) return value
        val suffix = "..."
        return renderer.trimToWidth(value, (maxWidth - renderer.getWidth(suffix)).coerceAtLeast(0)) + suffix
    }

    fun withAlpha(rgb: Int, alpha: Float): Int = ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (rgb and 0xFFFFFF)
}
