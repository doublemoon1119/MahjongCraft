package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

import net.minecraft.client.resources.model.BakedModel
import net.minecraft.resources.ResourceLocation

/** 依模型 ID 取得已烘焙的模型。原版只能以物品或方塊狀態的模型位置查詢，依 ID 查詢由載入器提供。 */
fun interface BakedModelLookup {
    /**
     * 取得已烘焙的模型。
     *
     * @param id 模型 ID。
     * @return 已烘焙的模型。沒有這個模型時為 null。
     */
    fun find(id: ResourceLocation): BakedModel?
}
