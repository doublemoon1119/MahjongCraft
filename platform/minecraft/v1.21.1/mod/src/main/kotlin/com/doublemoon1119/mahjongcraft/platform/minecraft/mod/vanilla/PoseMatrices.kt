@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import com.mojang.blaze3d.vertex.PoseStack
import org.joml.Matrix4f

/**
 * 把目前的座標轉換乘上指定矩陣。
 *
 * - 這個版本的原版已有同名方法，呼叫時一律使用原版方法。
 * - 這個宣告只讓共用程式碼的 import 能夠解析。
 *
 * @param matrix 要乘上的矩陣。
 */
fun PoseStack.mulPose(matrix: Matrix4f) {
    mulPose(matrix)
}
