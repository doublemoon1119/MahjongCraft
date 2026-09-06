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
     * 註冊一個擴充牌種對應的 asset key。asset key 在整個 registry 內必須唯一，避免不同牌種
     * 共用同一個 asset key 而在正規化、Tab 補全等場景造成無法區分的撞名。
     *
     * 第三方擴充建議以 [typeId] 的 [TileTypeId.namespace] 當作 asset key 前綴（例如
     * `"example_cat"`），降低與其他來源選到同一個字串的機率；貼圖檔案仍統一放在本 mod 的
     * asset namespace 下（見 [tileTextureAssetPath]），與 asset key 前綴無關。
     *
     * @throws IllegalStateException 當 registry 已凍結時拋出。
     * @throws IllegalArgumentException 當 [typeId] 已存在對應映射，或 [assetKey] 已被其他牌種
     * 註冊時拋出。
     */
    fun register(typeId: TileTypeId, assetKey: String)

    /** 凍結 registry；後續呼叫 [register] 將失敗。 */
    fun freeze()

    /** 依 [typeId] 尋找對應的 asset key；尚未註冊時回傳 null。 */
    fun find(typeId: TileTypeId): String?

    /** 是否有任何已註冊的擴充牌種對應到這個 asset key；供正規化外部輸入時判斷第三方 key 是否合法。 */
    fun isRegisteredAssetKey(assetKey: String): Boolean

    /** 目前所有已註冊的 asset key；供指令 Tab 補全等需要列舉第三方牌種的場景使用。 */
    val registeredAssetKeys: Set<String>
}
