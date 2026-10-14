package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata

/**
 * 一張牌使用的完整 Minecraft 貼圖與模型資源 ID。
 *
 * 此描述只定位資源，不定義模型大小、原點、標籤錨點或隱藏牌外觀。
 * Fabric 的有效已烘焙模型使用自身的幾何、UV 與各面材質；缺少模型時才沿用內建立方體與牌體材質。
 * 世界排列、翻牌補償與模型標籤仍以標準牌尺寸定位，自訂模型不會自動改變這些設定。
 * 平面預覽使用獨立正面貼圖，不從模型 JSON 擷取正面 UV；隱藏牌仍使用未知牌外觀。
 *
 * @property textureId 平面正面預覽與缺少模型時使用的貼圖完整 `namespace:path` ID，不代表六面材質圖集。
 * @property modelId 模型資源的完整 `namespace:path` ID；模型自行指定各面材質，不強制繼承內建基本模型。
 */
data class MinecraftTileResources(
    val textureId: String,
    val modelId: String,
) {
    /** 內建牌面資源所使用的預設描述。 */
    companion object {
        /**
         * 依內建命名慣例建立牌面資源描述。
         *
         * @param assetKey 素材登記使用的穩定 key。
         * @return 使用產品預設 namespace 與路徑的資源描述。
         */
        fun builtIn(assetKey: String): MinecraftTileResources = MinecraftTileResources(
            textureId = "${MinecraftModMetadata.MOD_ID}:${tileTextureAssetPath(assetKey)}",
            modelId = "${MinecraftModMetadata.MOD_ID}:${tileModelAssetPath(assetKey)}",
        )
    }

    init {
        NamespacedId.requireValid(textureId) {
            "Tile texture resource ID must be a full namespaced ID: $textureId"
        }
        NamespacedId.requireValid(modelId) {
            "Tile model resource ID must be a full namespaced ID: $modelId"
        }
    }
}
