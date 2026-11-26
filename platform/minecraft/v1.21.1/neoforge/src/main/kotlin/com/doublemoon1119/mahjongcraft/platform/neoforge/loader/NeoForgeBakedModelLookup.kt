package com.doublemoon1119.mahjongcraft.platform.neoforge.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.BakedModelLookup
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.resources.ResourceLocation

/** 依 ID 查詢 NeoForge 以獨立模型登記並烘焙的模型。 */
object NeoForgeBakedModelLookup : BakedModelLookup {
    override fun find(id: ResourceLocation): BakedModel? = Minecraft.getInstance().modelManager.getModel(ModelResourceLocation.standalone(id))
}
