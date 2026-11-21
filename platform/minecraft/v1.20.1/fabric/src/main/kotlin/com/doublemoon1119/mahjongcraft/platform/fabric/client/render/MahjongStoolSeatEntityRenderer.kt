package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongStoolSeatEntity
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation

/**
 * 麻將凳隱形座位的空 renderer。
 *
 * @param context Minecraft renderer factory 提供的 client context。
 */
class MahjongStoolSeatEntityRenderer(
    context: EntityRendererProvider.Context,
) : EntityRenderer<MahjongStoolSeatEntity>(context) {
    /** 座位不會繪製材質；回傳固定空材質 ID 以滿足 renderer 契約。 */
    override fun getTextureLocation(entity: MahjongStoolSeatEntity): ResourceLocation = EMPTY_TEXTURE

    /** 固定參數。 */
    companion object {
        /** 不會實際取樣的空材質 ID。 */
        private val EMPTY_TEXTURE = ResourceLocation("mahjongcraft", "textures/empty.png")
    }
}
