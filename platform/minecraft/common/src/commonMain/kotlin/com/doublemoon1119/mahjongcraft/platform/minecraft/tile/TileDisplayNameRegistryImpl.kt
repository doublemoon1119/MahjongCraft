package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId

/**
 * [TileDisplayNameRegistry] 的預設記憶體實作。
 *
 * 建構時不預先加入任何映射；內建與第三方顯示名稱使用相同的 [register] 流程，並由外層組裝完成後
 * 呼叫 [freeze]。此類別不依賴 Koin，runtime single 由外層 DI module 管理。
 */
class TileDisplayNameRegistryImpl : TileDisplayNameRegistry {
    /** 依牌種 ID 保存顯示名稱 translation key。 */
    private val translationKeysByTypeId = mutableMapOf<TileTypeId, String>()

    override var isFrozen: Boolean = false
        private set

    override fun register(typeId: TileTypeId, translationKey: String) {
        check(!isFrozen) { "Tile display name registry is frozen" }
        require(typeId !in translationKeysByTypeId) {
            "Display name already registered for tile type: $typeId"
        }
        translationKeysByTypeId[typeId] = translationKey
    }

    override fun freeze() {
        isFrozen = true
    }

    override fun find(typeId: TileTypeId): String? = translationKeysByTypeId[typeId]
}
