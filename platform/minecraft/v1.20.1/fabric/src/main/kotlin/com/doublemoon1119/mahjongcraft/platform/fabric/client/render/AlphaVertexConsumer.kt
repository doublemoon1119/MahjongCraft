package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import net.minecraft.client.render.VertexConsumer
import kotlin.math.roundToInt

/**
 * 把每個頂點的不透明度乘上固定比例後交給原本的緩衝，其他資料原樣傳遞；搭配半透明圖層畫出淡化的模型。
 *
 * @property delegate 原本的頂點緩衝。
 * @property alpha 0 到 1 之間的不透明度比例。
 */
internal class AlphaVertexConsumer(
    private val delegate: VertexConsumer,
    private val alpha: Float,
) : VertexConsumer {
    override fun vertex(x: Double, y: Double, z: Double): VertexConsumer = apply { delegate.vertex(x, y, z) }

    override fun color(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer = apply { delegate.color(red, green, blue, scaled(alpha)) }

    override fun texture(u: Float, v: Float): VertexConsumer = apply { delegate.texture(u, v) }

    override fun overlay(u: Int, v: Int): VertexConsumer = apply { delegate.overlay(u, v) }

    override fun light(u: Int, v: Int): VertexConsumer = apply { delegate.light(u, v) }

    override fun normal(x: Float, y: Float, z: Float): VertexConsumer = apply { delegate.normal(x, y, z) }

    override fun next() {
        delegate.next()
    }

    override fun fixedColor(red: Int, green: Int, blue: Int, alpha: Int) {
        delegate.fixedColor(red, green, blue, scaled(alpha))
    }

    override fun unfixColor() {
        delegate.unfixColor()
    }

    /**
     * 乘上不透明度比例後的顏色分量。
     *
     * @param value 原本 0 到 255 的不透明度。
     * @return 乘上比例後的不透明度。
     */
    private fun scaled(value: Int): Int = (value * alpha).roundToInt().coerceIn(0, MAX_CHANNEL)

    /** 顏色分量的上限。 */
    private companion object {
        /** 0 到 255 的顏色分量上限。 */
        const val MAX_CHANNEL = 255
    }
}
