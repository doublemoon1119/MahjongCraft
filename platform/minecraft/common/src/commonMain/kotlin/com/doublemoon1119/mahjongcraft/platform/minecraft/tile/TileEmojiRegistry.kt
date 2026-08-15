package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 管理 asset key（見 [Tile.toAssetKey]）對應牌面 emoji 字元的 runtime registry。
 *
 * 比照 [MinecraftTileAssetRegistry] 的開放註冊表設計：內建與第三方牌種共用同一套 [register] 流程，
 * 不具特權。這裡的 emoji 只是純字元，實際會不會顯示成貼圖取決於呼叫端（或第三方 mod／資源包）有沒有
 * 提供對應的 `assets/minecraft/font/default.json` bitmap provider——這個 registry 本身不管字型檔案，
 * 只保存字元對照。
 */
interface TileEmojiRegistry {
    /** registry 是否已禁止後續註冊。 */
    val isFrozen: Boolean

    /**
     * 註冊一個 asset key 對應的牌面 emoji 字元。
     *
     * @throws IllegalStateException 當 registry 已凍結時拋出。
     * @throws IllegalArgumentException 當 [assetKey] 已存在對應映射時拋出。
     */
    fun register(assetKey: String, emoji: String)

    /** 凍結 registry；後續呼叫 [register] 將失敗。 */
    fun freeze()

    /** 依 [assetKey] 尋找對應的牌面 emoji 字元；尚未註冊時回傳 null。 */
    fun find(assetKey: String): String?
}
