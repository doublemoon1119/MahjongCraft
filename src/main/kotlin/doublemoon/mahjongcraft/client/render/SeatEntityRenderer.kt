package doublemoon.mahjongcraft.client.render

import doublemoon.mahjongcraft.entity.SeatEntity
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.rendereregistry.v1.EntityRendererRegistry
import net.minecraft.client.render.entity.EntityRenderDispatcher
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.util.Identifier

@Environment(EnvType.CLIENT)
class SeatEntityRenderer(
    dispatcher: EntityRenderDispatcher,
    context: EntityRendererRegistry.Context
) : EntityRenderer<SeatEntity>(dispatcher) {

    override fun getTexture(entity: SeatEntity): Identifier? = null

}