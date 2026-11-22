package com.doublemoon1119.mahjongcraft.platform.fabric.client.model

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.model.FixedMahjongTileModelVariant
import net.fabricmc.fabric.api.client.model.loading.v1.ModelResolver
import net.minecraft.client.resources.model.UnbakedModel
import net.minecraft.resources.ResourceLocation

/**
 * 在 Fabric 烘焙前以 synthetic ID 提供固定牌模型的材質變體。
 *
 * 未登記的模型 ID 會交回 `null`，讓其他 resolver 與 vanilla 載入流程繼續處理。
 *
 * @param variants 此次資源重載可提供的固定牌模型變體。
 */
class FixedMahjongTileModelVariantResolver(
    variants: Collection<FixedMahjongTileModelVariant>,
) : ModelResolver {
    /** 依 synthetic model ID 查詢的固定牌模型變體。 */
    private val variantsById: Map<ResourceLocation, FixedMahjongTileModelVariant> = variants.associateBy { it.modelId }

    override fun resolveModel(context: ModelResolver.Context): UnbakedModel? = variantsById[context.id()]?.toUnbakedModel()
}
