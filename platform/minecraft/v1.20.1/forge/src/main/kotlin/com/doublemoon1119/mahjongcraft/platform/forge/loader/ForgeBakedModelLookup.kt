package com.doublemoon1119.mahjongcraft.platform.forge.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.BakedModelLookup
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.resources.ResourceLocation

/** 以 Forge 對原版模型管理器的擴充依 ID 查詢已烘焙模型。 */
object ForgeBakedModelLookup : BakedModelLookup {
    override fun find(id: ResourceLocation): BakedModel? = Minecraft.getInstance().modelManager.getModel(id)
}
