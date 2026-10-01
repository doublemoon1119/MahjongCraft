package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongStoolSeatEntity
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.util.Identifier

/** 麻將凳隱形座位的空 renderer。 */
class MahjongStoolSeatEntityRenderer(
    context: EntityRendererFactory.Context,
) : EntityRenderer<MahjongStoolSeatEntity>(context) {
    /** 座位不會繪製材質；回傳固定空材質 ID 以滿足 renderer 契約。 */
    override fun getTexture(entity: MahjongStoolSeatEntity): Identifier = EMPTY_TEXTURE

    /** 固定參數。 */
    companion object {
        /** 不會實際取樣的空材質 ID。 */
        private val EMPTY_TEXTURE = Identifier("mahjongcraft", "textures/empty.png")
    }
}
