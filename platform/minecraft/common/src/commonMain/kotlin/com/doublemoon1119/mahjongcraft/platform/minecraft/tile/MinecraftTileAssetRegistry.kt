package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId

/**
 * 管理 [TileTypeId] 對應 Minecraft 素材識別字串（asset key）的 runtime registry。
 *
 * 只保存穩定 ID 與 asset key 的映射，不保存 Minecraft `Identifier` 或實際材質檔案；核心
 * `mahjong-logic` 不依賴此介面，由 Minecraft adapter 自行決定如何將 asset key 解析成模型與貼圖。
 */
interface MinecraftTileAssetRegistry {
    /** registry 是否已禁止後續註冊。 */
    val isFrozen: Boolean

    /**
     * 註冊一個擴充牌種對應的 asset key。
     *
     * @throws IllegalStateException 當 registry 已凍結時拋出。
     * @throws IllegalArgumentException 當 [typeId] 已存在對應映射時拋出。
     */
    fun register(typeId: TileTypeId, assetKey: String)

    /** 凍結 registry；後續呼叫 [register] 將失敗。 */
    fun freeze()

    /** 依 [typeId] 尋找對應的 asset key；尚未註冊時回傳 null。 */
    fun find(typeId: TileTypeId): String?
}
