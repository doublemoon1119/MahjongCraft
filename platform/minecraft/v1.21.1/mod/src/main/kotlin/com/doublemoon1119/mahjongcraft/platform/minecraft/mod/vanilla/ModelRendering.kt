@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.model.Model
import net.minecraft.client.model.geom.ModelPart

// 這個版本的原版已有同名方法，呼叫時一律使用原版方法；這裡的同名宣告只讓共用程式碼的 import 能夠解析。

/** 以指定 ARGB 顏色繪製這個模型部位與它的子部位。 */
fun ModelPart.render(pose: PoseStack, buffer: VertexConsumer, light: Int, overlay: Int, color: Int) {
    render(pose, buffer, light, overlay, color)
}

/** 以指定 ARGB 顏色繪製整個模型。 */
fun Model.renderToBuffer(pose: PoseStack, buffer: VertexConsumer, light: Int, overlay: Int, color: Int) {
    renderToBuffer(pose, buffer, light, overlay, color)
}
