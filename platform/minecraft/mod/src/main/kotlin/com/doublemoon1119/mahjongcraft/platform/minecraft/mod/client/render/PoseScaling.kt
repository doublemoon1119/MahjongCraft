package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render

import com.mojang.blaze3d.vertex.PoseStack
import org.joml.Matrix3f

/**
 * 沿三個座標軸縮放位置，法線的轉換維持縮放前的狀態。
 *
 * - 用於不改變各面朝向的縮放，例如把方塊拉成細長條，或翻轉某個座標軸。
 * - 法線因此維持單位長度。有些載入器寫入烘焙模型的頂點時不會再把法線恢復成單位長度，沿用縮放後的法線會讓明暗失真。
 * - 法線不隨版本處理縮放的方式改變，各版本遇到各軸倍數不同或負的倍數時算法並不相同。
 *
 * @param x X 軸倍數。
 * @param y Y 軸倍數。
 * @param z Z 軸倍數。
 */
internal fun PoseStack.scaleKeepingNormals(x: Float, y: Float, z: Float) {
    val normal = Matrix3f(last().normal())
    scale(x, y, z)
    last().normal().set(normal)
}
