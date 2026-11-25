package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import com.mojang.blaze3d.vertex.PoseStack
import org.joml.Matrix4f

/**
 * 把目前的座標轉換乘上指定矩陣。名稱與新版 Minecraft 相同；這個版本照原版只改變位置，法向量轉換不變，與這個版本的原版光照
 * 慣例一致。新版的同名方法也會轉換法向量，需要固定法向量的呼叫端自行保存並還原。
 *
 * @param matrix 要乘上的矩陣。
 */
fun PoseStack.mulPose(matrix: Matrix4f) {
    mulPoseMatrix(matrix)
}
