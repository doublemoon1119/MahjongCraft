package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import com.mojang.blaze3d.vertex.PoseStack
import org.joml.Matrix4f

/**
 * 把目前的座標轉換乘上指定矩陣。
 *
 * @param matrix 要乘上的矩陣。
 */
fun PoseStack.multiplyPose(matrix: Matrix4f) {
    mulPoseMatrix(matrix)
}
