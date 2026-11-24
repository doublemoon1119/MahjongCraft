@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.block.model.BakedQuad
import org.joml.Matrix4f

// 這個版本的原版已有同名方法，呼叫時一律使用原版方法；這裡的同名宣告只讓共用程式碼的 import 能夠解析。

/** 開始一個新頂點，位置先經過矩陣轉換。 */
fun VertexConsumer.addVertex(matrix: Matrix4f, x: Float, y: Float, z: Float): VertexConsumer = addVertex(matrix, x, y, z)

/** 設定這個頂點的顏色，每個分量 0 到 255。 */
fun VertexConsumer.setColor(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer = setColor(red, green, blue, alpha)

/** 設定這個頂點的顏色，每個分量 0 到 1。 */
fun VertexConsumer.setColor(red: Float, green: Float, blue: Float, alpha: Float): VertexConsumer = setColor(red, green, blue, alpha)

/** 設定這個頂點的貼圖座標。 */
fun VertexConsumer.setUv(u: Float, v: Float): VertexConsumer = setUv(u, v)

/** 設定這個頂點的疊加色座標。 */
fun VertexConsumer.setOverlay(overlay: Int): VertexConsumer = setOverlay(overlay)

/** 設定這個頂點的光照。 */
fun VertexConsumer.setLight(light: Int): VertexConsumer = setLight(light)

/** 設定這個頂點的法線，方向先經過姿勢的法線矩陣轉換。 */
fun VertexConsumer.setNormal(pose: PoseStack.Pose, x: Float, y: Float, z: Float): VertexConsumer = setNormal(pose, x, y, z)

/** 設定這個頂點的法線，方向不再轉換。 */
fun VertexConsumer.setNormal(x: Float, y: Float, z: Float): VertexConsumer = setNormal(x, y, z)

/** 結束目前的頂點。這個版本寫入下一個頂點時自動結束前一個，不需要額外動作。 */
@Suppress("UnusedReceiverParameter")
fun VertexConsumer.finishVertex() = Unit

/** 把一個已烘焙的模型面整批寫入。 */
fun VertexConsumer.putBulkData(
    pose: PoseStack.Pose,
    quad: BakedQuad,
    red: Float,
    green: Float,
    blue: Float,
    alpha: Float,
    light: Int,
    overlay: Int,
) {
    putBulkData(pose, quad, red, green, blue, alpha, light, overlay)
}
