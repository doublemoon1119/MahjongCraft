package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.block.model.BakedQuad
import org.joml.Matrix4f

// 寫頂點的方法依新版 Minecraft 的名稱提供；新版本直接使用原版的同名方法。每個頂點寫完後呼叫 [finishVertex]。

/**
 * 開始一個新頂點，位置先經過矩陣轉換。
 *
 * @param matrix 位置矩陣。
 * @param x X 座標。
 * @param y Y 座標。
 * @param z Z 座標。
 * @return 同一個 consumer，繼續寫這個頂點的其他資料。
 */
fun VertexConsumer.addVertex(matrix: Matrix4f, x: Float, y: Float, z: Float): VertexConsumer = vertex(matrix, x, y, z)

/**
 * 設定這個頂點的顏色。
 *
 * @param red 紅色，0 到 255。
 * @param green 綠色，0 到 255。
 * @param blue 藍色，0 到 255。
 * @param alpha 不透明度，0 到 255。
 * @return 同一個 consumer。
 */
fun VertexConsumer.setColor(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer = color(red, green, blue, alpha)

/**
 * 設定這個頂點的顏色。
 *
 * @param red 紅色，0 到 1。
 * @param green 綠色，0 到 1。
 * @param blue 藍色，0 到 1。
 * @param alpha 不透明度，0 到 1。
 * @return 同一個 consumer。
 */
fun VertexConsumer.setColor(red: Float, green: Float, blue: Float, alpha: Float): VertexConsumer = color(red, green, blue, alpha)

/**
 * 設定這個頂點的貼圖座標。
 *
 * @param u 貼圖 U 座標。
 * @param v 貼圖 V 座標。
 * @return 同一個 consumer。
 */
fun VertexConsumer.setUv(u: Float, v: Float): VertexConsumer = uv(u, v)

/**
 * 設定這個頂點的疊加色座標，例如受傷時的紅色。
 *
 * @param overlay 合併成一個整數的疊加色座標。
 * @return 同一個 consumer。
 */
fun VertexConsumer.setOverlay(overlay: Int): VertexConsumer = overlayCoords(overlay)

/**
 * 設定這個頂點的光照。
 *
 * @param light 合併成一個整數的方塊與天空光照。
 * @return 同一個 consumer。
 */
fun VertexConsumer.setLight(light: Int): VertexConsumer = uv2(light)

/**
 * 設定這個頂點的法線，方向先經過姿勢的法線矩陣轉換。
 *
 * @param pose 目前的姿勢。
 * @param x 法線 X 分量。
 * @param y 法線 Y 分量。
 * @param z 法線 Z 分量。
 * @return 同一個 consumer。
 */
fun VertexConsumer.setNormal(pose: PoseStack.Pose, x: Float, y: Float, z: Float): VertexConsumer = normal(pose.normal(), x, y, z)

/**
 * 設定這個頂點的法線，方向不再轉換。
 *
 * @param x 法線 X 分量。
 * @param y 法線 Y 分量。
 * @param z 法線 Z 分量。
 * @return 同一個 consumer。
 */
fun VertexConsumer.setNormal(x: Float, y: Float, z: Float): VertexConsumer = normal(x, y, z)

/** 結束目前的頂點；寫完一個頂點的全部資料後呼叫。 */
fun VertexConsumer.finishVertex() {
    endVertex()
}

/**
 * 把一個已烘焙的模型面整批寫入，名稱與參數與新版 Minecraft 相同。這個版本的原版方法沒有不透明度，[alpha] 不會套用。
 *
 * @param pose 目前的姿勢。
 * @param quad 要寫入的模型面。
 * @param red 乘上去的紅色，0 到 1。
 * @param green 乘上去的綠色，0 到 1。
 * @param blue 乘上去的藍色，0 到 1。
 * @param alpha 乘上去的不透明度，0 到 1。
 * @param light 光照。
 * @param overlay 疊加色座標。
 */
fun VertexConsumer.putBulkData(
    pose: PoseStack.Pose,
    quad: BakedQuad,
    red: Float,
    green: Float,
    blue: Float,
    @Suppress("UNUSED_PARAMETER") alpha: Float,
    light: Int,
    overlay: Int,
) {
    putBulkData(pose, quad, red, green, blue, light, overlay)
}
