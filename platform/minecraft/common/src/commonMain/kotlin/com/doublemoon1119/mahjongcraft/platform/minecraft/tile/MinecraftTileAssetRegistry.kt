package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId

/**
 * 管理 [TileTypeId] 對應 Minecraft 素材識別字串（asset key）的 runtime registry。
 *
 * 只保存穩定 ID 與 asset key 的映射，不保存 Minecraft `Identifier` 或實際材質檔案；核心
 * `mahjong-logic` 不依賴此介面，由 Minecraft adapter 自行決定如何將 asset key 解析成模型與貼圖。
 */
interface MinecraftTileAssetRegistry {
    /** 目前已登記 asset key 的快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已禁止後續註冊。 */
    val isFrozen: Boolean

    /**
     * 登記完整描述；凍結後或身分重複時拒絕，不提前呼叫來源。
     *
     * @param definition 集中保存登記身分與內容的描述。
     */
    fun register(definition: MinecraftTileAssetDefinition)

    /** 凍結 registry；後續呼叫 [register] 將失敗。 */
    fun freeze()

    /**
     * 依 [typeId] 尋找對應的 asset key；尚未註冊時回傳 null。
     *
     * @param typeId 牌種的穩定識別碼。
     * @return 已登記的 asset key；未登記時為 `null`。
     */
    fun find(typeId: TileTypeId): String?

    /**
     * 是否有任何已註冊的擴充牌種對應到這個 asset key；供正規化外部輸入時判斷第三方 key 是否合法。
     *
     * @param assetKey 要查詢的素材 key。
     * @return 有對應牌種時為 `true`。
     */
    fun isRegisteredAssetKey(assetKey: String): Boolean

    /** 目前所有已註冊的 asset key；供指令 Tab 補全等需要列舉第三方牌種的場景使用。 */
    val registeredAssetKeys: Set<String>
}
