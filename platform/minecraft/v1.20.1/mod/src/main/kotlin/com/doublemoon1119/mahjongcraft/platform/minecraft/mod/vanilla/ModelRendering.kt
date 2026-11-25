package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.model.Model
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.util.FastColor

// 以一個 ARGB 顏色繪製模型，名稱與參數與新版 Minecraft 相同。

/**
 * 以指定顏色繪製這個模型部位與它的子部位。
 *
 * @param pose 目前的姿勢。
 * @param buffer 接收頂點的 consumer。
 * @param light 光照。
 * @param overlay 疊加色座標。
 * @param color 乘上去的 ARGB 顏色，包含不透明度。
 */
fun ModelPart.render(pose: PoseStack, buffer: VertexConsumer, light: Int, overlay: Int, color: Int) {
    render(pose, buffer, light, overlay, red(color), green(color), blue(color), alpha(color))
}

/**
 * 以指定顏色繪製整個模型。
 *
 * @param pose 目前的姿勢。
 * @param buffer 接收頂點的 consumer。
 * @param light 光照。
 * @param overlay 疊加色座標。
 * @param color 乘上去的 ARGB 顏色，包含不透明度。
 */
fun Model.renderToBuffer(pose: PoseStack, buffer: VertexConsumer, light: Int, overlay: Int, color: Int) {
    renderToBuffer(pose, buffer, light, overlay, red(color), green(color), blue(color), alpha(color))
}

/** ARGB 顏色中 0 到 1 的紅色。 */
private fun red(color: Int): Float = FastColor.ARGB32.red(color) / CHANNEL_MAX

/** ARGB 顏色中 0 到 1 的綠色。 */
private fun green(color: Int): Float = FastColor.ARGB32.green(color) / CHANNEL_MAX

/** ARGB 顏色中 0 到 1 的藍色。 */
private fun blue(color: Int): Float = FastColor.ARGB32.blue(color) / CHANNEL_MAX

/** ARGB 顏色中 0 到 1 的不透明度。 */
private fun alpha(color: Int): Float = FastColor.ARGB32.alpha(color) / CHANNEL_MAX

/** 顏色分量的最大值。 */
private const val CHANNEL_MAX = 255f
