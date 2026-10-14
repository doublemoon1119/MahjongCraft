package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata

/**
 * 描述一套牌共用的 Minecraft 外觀資源。
 *
 * @property id 外觀的完整 `namespace:path` 識別碼。
 * @property coverTextureId 固定布局的牌體材質圖集完整資源 ID；牌背預覽亦取用其中的背面區域。
 * @property unknownFaceTextureId 未知牌正面材質的完整資源 ID；亦供缺失正面資源回退使用，省略時使用內建未知牌材質。
 */
data class MinecraftTileSetAppearanceDefinition(
    val id: String,
    val coverTextureId: String,
    val unknownFaceTextureId: String =
        "${MinecraftModMetadata.MOD_ID}:${tileTextureAssetPath("unknown")}",
) {
    init {
        NamespacedId.requireValid(id) { "Invalid tile set appearance ID: $id" }
        NamespacedId.requireValid(coverTextureId) { "Invalid tile set cover texture ID: $coverTextureId" }
        NamespacedId.requireValid(unknownFaceTextureId) {
            "Invalid unknown face texture ID: $unknownFaceTextureId"
        }
    }
}
