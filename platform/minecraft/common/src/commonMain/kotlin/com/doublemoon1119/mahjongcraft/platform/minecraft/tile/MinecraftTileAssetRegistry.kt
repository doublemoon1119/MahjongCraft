package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId

/**
 * 管理 [TileTypeId] 對應 Minecraft 素材識別字串與資源描述的 runtime registry。
 *
 * 保存牌種 asset key、牌種資源與牌組共用外觀的完整資源 ID，不保存 Minecraft `Identifier` 或實際材質檔案；核心
 * `mahjong-logic` 不依賴此介面，由 Minecraft adapter 自行決定如何將資源 ID 解析成模型與貼圖。
 */
interface MinecraftTileAssetRegistry {
    /** 目前已登記 asset key 的快照。 */
    val registrationKeys: Set<String>

    /** 目前已登記牌組外觀識別碼的快照。 */
    val appearanceRegistrationKeys: Set<String>

    /** 目前已登記規則外觀綁定規則 ID 的快照。 */
    val ruleAppearanceBindingKeys: Set<String>

    /** 目前已登記牌組外觀的分離快照，供資源預載入使用。 */
    val registeredAppearances: List<MinecraftTileSetAppearanceDefinition>

    /** registry 是否已禁止後續註冊。 */
    val isFrozen: Boolean

    /**
     * 登記完整描述；凍結後、身分重複或素材 key 與基本牌／未知牌重名時拒絕。
     *
     * @param definition 集中保存登記身分與內容的描述。
     */
    fun register(definition: MinecraftTileAssetDefinition)

    /**
     * 登記一套規則中立的牌組共用外觀。
     *
     * @param definition 要登記的牌組外觀描述。
     */
    fun register(definition: MinecraftTileSetAppearanceDefinition): Unit = error("This tile asset registry does not support tile set appearances")

    /**
     * 登記規則使用的預設牌組外觀。
     *
     * @param definition 要登記的規則外觀綁定。
     */
    fun register(definition: MinecraftRuleTileAppearanceDefinition): Unit = error("This tile asset registry does not support rule tile appearances")

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
     * 依 asset key 尋找完整的貼圖與模型資源 ID。
     *
     * @param assetKey 要查詢的素材 key。
     * @return 已登記的資源描述；未登記時為 `null`。
     */
    fun findResources(assetKey: String): MinecraftTileResources? = null

    /**
     * 依外觀 ID 尋找牌組共用外觀；未登記時回傳 `null`。
     *
     * @param appearanceId 要查詢的牌組外觀識別碼。
     * @return 已登記的牌組外觀；找不到時為 `null`。
     */
    fun findAppearance(appearanceId: String): MinecraftTileSetAppearanceDefinition? = null

    /**
     * 依規則 ID 尋找牌組外觀；沒有綁定時回傳內建預設外觀。
     *
     * @param ruleModuleId 要查詢的規則模組識別碼。
     * @return 規則指定的牌組外觀，或已登記的內建預設外觀。
     */
    fun findAppearanceForRule(ruleModuleId: String): MinecraftTileSetAppearanceDefinition? = null

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
