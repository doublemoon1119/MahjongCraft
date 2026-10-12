package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import org.koin.core.annotation.Single

/**
 * [TileLabelRegistry] 的預設記憶體實作。
 *
 * 建構時不預先加入任何映射；內建與第三方標籤使用相同的 [register] 流程，並由外層組裝完成後呼叫
 * [freeze]。runtime single 由 Koin component scan 管理。
 */
@Single(binds = [TileLabelRegistry::class])
class TileLabelRegistryImpl : TileLabelRegistry {
    /** 依 asset key 保存牌面角落標籤。 */
    private val labelsByAssetKey = mutableMapOf<String, TileLabel>()

    override val registrationKeys: Set<String> get() = labelsByAssetKey.keys.toSet()

    override var isFrozen: Boolean = false
        private set

    override fun register(assetKey: String, label: TileLabel) {
        check(!isFrozen) { "Tile label registry is frozen" }
        require(assetKey !in labelsByAssetKey) {
            "Label already registered for tile asset key: $assetKey"
        }
        labelsByAssetKey[assetKey] = label
    }

    override fun freeze() {
        isFrozen = true
    }

    override fun find(assetKey: String): TileLabel? = labelsByAssetKey[assetKey]
}
