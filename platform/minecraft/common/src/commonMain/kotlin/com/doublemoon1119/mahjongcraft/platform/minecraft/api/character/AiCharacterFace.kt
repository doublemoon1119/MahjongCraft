package com.doublemoon1119.mahjongcraft.platform.minecraft.api.character

/** 可保存的二維頭像來源；不含玩家皮膚查詢或繪製 callback。 */
sealed interface AiCharacterFace {
    /**
     * 使用已登記的牌面圖片作為頭像。
     *
     * @property assetKey 牌面素材識別鍵。
     */
    class Tile(val assetKey: String) : AiCharacterFace {
        init {
            require(assetKey.isNotBlank() && assetKey.length <= AiCharacterLimits.MAX_ID_LENGTH) { "Invalid character face asset key" }
        }

        /** 素材識別鍵相同時相等。 */
        override fun equals(other: Any?): Boolean = other is Tile && assetKey == other.assetKey

        /** 與相等規則一致的雜湊值。 */
        override fun hashCode(): Int = assetKey.hashCode()
    }

    /**
     * 使用貼圖中的一塊正規化區域。
     *
     * @property resourceId 貼圖完整資源 ID。
     * @property u0 左側邊界。
     * @property v0 上側邊界。
     * @property u1 右側邊界。
     * @property v1 下側邊界。
     */
    class TextureRegion(
        val resourceId: String,
        val u0: Float,
        val v0: Float,
        val u1: Float,
        val v1: Float,
    ) : AiCharacterFace {
        init {
            AiCharacterLimits.requireId(resourceId)
            require(listOf(u0, v0, u1, v1).all { it.isFinite() && it in 0f..1f } && u0 < u1 && v0 < v1) { "Invalid character face texture region" }
        }

        /** 資源與取圖區域完全相同時相等。 */
        override fun equals(other: Any?): Boolean = other is TextureRegion && resourceId == other.resourceId && u0 == other.u0 && v0 == other.v0 && u1 == other.u1 && v1 == other.v1

        /** 與相等規則一致的雜湊值；正負零視為同一邊界。 */
        override fun hashCode(): Int = listOf(resourceId, u0 + 0f, v0 + 0f, u1 + 0f, v1 + 0f).hashCode()
    }
}
