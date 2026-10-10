package com.doublemoon1119.mahjongcraft.platform.minecraft.player

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds

/** Renderer 可安全解讀的玩家頭像來源；不向 extension 暴露 Minecraft render callback。 */
sealed interface PlayerPortraitSource {
    /** 使用玩家 skin 的正面臉部，不包含帽子 layer。 */
    data object PlayerSkinFace : PlayerPortraitSource

    /**
     * 使用已註冊牌面 asset；適合 AI 或規則角色的純牌面 fallback。
     *
     * @property assetKey 牌面 asset 的穩定識別鍵。
     */
    data class TileFace(val assetKey: String) : PlayerPortraitSource {
        init {
            require(assetKey.isNotBlank()) { "Portrait tile asset key must not be blank" }
        }
    }

    /**
     * 使用 resource 中指定的正規化 UV 區域。
     *
     * @property resourceId 貼圖 resource 的 namespaced 識別碼。
     * @property u0 UV 區域的左側邊界。
     * @property v0 UV 區域的上側邊界。
     * @property u1 UV 區域的右側邊界。
     * @property v1 UV 區域的下側邊界。
     */
    data class TextureRegion(
        val resourceId: String,
        val u0: Float,
        val v0: Float,
        val u1: Float,
        val v1: Float,
    ) : PlayerPortraitSource {
        init {
            MinecraftResourceIds.requireValid(resourceId) { "Portrait texture resource ID must be namespaced: $resourceId" }
            require(listOf(u0, v0, u1, v1).all { it.isFinite() && it in 0f..1f }) {
                "Portrait texture UV values must be finite and normalized"
            }
            require(u0 < u1 && v0 < v1) { "Portrait texture UV region must have positive size" }
        }
    }
}
