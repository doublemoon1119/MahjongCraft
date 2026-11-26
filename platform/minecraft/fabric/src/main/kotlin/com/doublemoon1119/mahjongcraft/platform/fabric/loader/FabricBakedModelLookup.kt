package com.doublemoon1119.mahjongcraft.platform.fabric.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.BakedModelLookup
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.resources.ResourceLocation

/** 以 Fabric API 對原版模型管理器的擴充依 ID 查詢已烘焙模型。 */
object FabricBakedModelLookup : BakedModelLookup {
    override fun find(id: ResourceLocation): BakedModel? = (Minecraft.getInstance().modelManager as FabricBakedModelManager).getModel(id)
}
