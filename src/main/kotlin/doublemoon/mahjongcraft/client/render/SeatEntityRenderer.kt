package doublemoon.mahjongcraft.client.render

import doublemoon.mahjongcraft.entity.SeatEntity
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.util.Identifier

@Environment(EnvType.CLIENT)
class SeatEntityRenderer(
    context: EntityRendererFactory.Context
) : EntityRenderer<SeatEntity>(context) {

    override fun getTexture(entity: SeatEntity): Identifier? = null

}