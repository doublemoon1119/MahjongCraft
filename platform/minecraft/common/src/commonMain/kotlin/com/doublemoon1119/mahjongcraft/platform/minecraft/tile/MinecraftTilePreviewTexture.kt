package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 平面牌面預覽所使用的貼圖與固定 UV 範圍。
 *
 * UV 已經正規化到 `0.0..1.0`，不允許第三方牌種改變預覽幾何或牌面尺寸。
 *
 * @property resourceId 貼圖的完整 `namespace:path` 資源 ID。
 * @property u0 UV 左側起點。
 * @property v0 UV 上側起點。
 * @property u1 UV 右側終點。
 * @property v1 UV 下側終點。
 */
data class MinecraftTilePreviewTexture(
    val resourceId: String,
    val u0: Float,
    val v0: Float,
    val u1: Float,
    val v1: Float,
) {
    init {
        NamespacedId.requireValid(resourceId) { "Invalid tile preview texture ID: $resourceId" }
        require(u0 in 0f..1f && v0 in 0f..1f && u1 in 0f..1f && v1 in 0f..1f) {
            "Tile preview UV coordinates must be normalized: ($u0, $v0, $u1, $v1)"
        }
        require(u0 < u1 && v0 < v1) { "Tile preview UV range must have positive size" }
    }
}
