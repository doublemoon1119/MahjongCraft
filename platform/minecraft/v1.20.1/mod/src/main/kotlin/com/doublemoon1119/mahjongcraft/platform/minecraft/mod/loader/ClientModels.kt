package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

import net.minecraft.client.resources.model.BakedModel
import net.minecraft.resources.ResourceLocation

/**
 * 繪製時依 ID 查詢已烘焙模型的入口。
 *
 * 載入器的用戶端進入點在繪製任何東西之前安裝查詢方式；尚未安裝就查詢會丟出例外。
 */
object ClientModels {
    /** 載入器提供的查詢方式。 */
    @Volatile
    private var installedLookup: BakedModelLookup? = null

    /**
     * 安裝查詢方式。
     *
     * @param lookup 載入器的查詢方式。
     */
    fun install(lookup: BakedModelLookup) {
        installedLookup = lookup
    }

    /**
     * 取得已烘焙的模型。
     *
     * @param id 模型 ID。
     * @return 已烘焙的模型；沒有這個模型時為 null。
     */
    fun find(id: ResourceLocation): BakedModel? = checkNotNull(installedLookup) { "Baked model lookup is not installed" }.find(id)
}
