package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 管理 asset key（見 [Tile.toAssetKey]）對應牌面角落標籤（[TileLabel]）的 runtime registry。
 *
 * 比照 [TileEmojiRegistry] 的開放註冊表設計：內建與第三方牌種共用同一套 [register] 流程，不具特權。
 * 這是給非中文圈玩家看的可切換輔助標籤（例如九筒右上角顯示 `9`），不影響牌面本身的材質；沒有註冊
 * 標籤的 asset key（例如尚未提供標籤的第三方牌種）[find] 回傳 `null`，呈現端應視為不顯示任何標籤，
 * 而不是報錯或顯示佔位符。
 */
interface TileLabelRegistry {
    /** registry 是否已禁止後續註冊。 */
    val isFrozen: Boolean

    /**
     * 註冊一個 asset key 對應的牌面角落標籤。
     *
     * @throws IllegalStateException 當 registry 已凍結時拋出。
     * @throws IllegalArgumentException 當 [assetKey] 已存在對應映射時拋出。
     */
    fun register(assetKey: String, label: TileLabel)

    /** 凍結 registry；後續呼叫 [register] 將失敗。 */
    fun freeze()

    /** 依 [assetKey] 尋找對應的牌面角落標籤；尚未註冊時回傳 `null`。 */
    fun find(assetKey: String): TileLabel?
}
