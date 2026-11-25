package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.player

import net.minecraft.resources.ResourceLocation

/**
 * 一位玩家外觀用到的材質：皮膚、手臂款式與披風。
 *
 * @property texture 皮膚材質。
 * @property modelName 原版的手臂款式名稱：細手臂為 [SLIM_MODEL]，一般手臂為 [DEFAULT_MODEL]。
 * @property cape 披風材質。沒有披風時為 null。
 */
data class SkinTextures(val texture: ResourceLocation, val modelName: String, val cape: ResourceLocation?) {
    /** 手臂款式名稱。 */
    companion object {
        /** 細手臂。 */
        const val SLIM_MODEL: String = "slim"

        /** 一般手臂。 */
        const val DEFAULT_MODEL: String = "default"
    }
}
