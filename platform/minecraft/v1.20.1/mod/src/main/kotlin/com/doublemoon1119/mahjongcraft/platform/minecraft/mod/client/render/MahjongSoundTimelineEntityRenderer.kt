package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongSoundTimelineEntity
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation

/**
 * 無形聲音時間線的空 renderer。
 *
 * @param context Minecraft renderer factory 提供的 client context。
 */
class MahjongSoundTimelineEntityRenderer(
    context: EntityRendererProvider.Context,
) : EntityRenderer<MahjongSoundTimelineEntity>(context) {
    /** 此 entity 不會繪製材質；回傳固定空材質 ID 以滿足 renderer 契約。 */
    override fun getTextureLocation(entity: MahjongSoundTimelineEntity): ResourceLocation = EMPTY_TEXTURE

    /** 固定參數。 */
    companion object {
        /** 不會實際取樣的空材質 ID。 */
        private val EMPTY_TEXTURE = ResourceLocation("mahjongcraft", "textures/empty.png")
    }
}
