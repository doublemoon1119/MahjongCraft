package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import com.mojang.blaze3d.systems.RenderSystem

/** 繪製狀態的模型檢視矩陣：保存、還原、設為單位矩陣，並套用到之後的繪製。 */
object ModelViewMatrix {
    /** 保存目前的矩陣，之後以 [pop] 還原。 */
    fun push() {
        RenderSystem.getModelViewStack().pushMatrix()
    }

    /** 還原最近一次 [push] 保存的矩陣。 */
    fun pop() {
        RenderSystem.getModelViewStack().popMatrix()
    }

    /** 把目前的矩陣設為單位矩陣。 */
    fun setIdentity() {
        RenderSystem.getModelViewStack().identity()
    }

    /** 把目前的矩陣套用到之後的繪製。 */
    fun apply() {
        RenderSystem.applyModelViewMatrix()
    }
}
