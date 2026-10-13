package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import org.koin.core.annotation.Single

/**
 * [TileEmojiRegistry] 的預設記憶體實作。
 *
 * 建構時不預先加入任何映射；內建與第三方 emoji 使用相同的 [register] 流程，並由外層組裝完成後呼叫
 * [freeze]。runtime single 由 Koin component scan 管理。
 */
@Single(binds = [TileEmojiRegistry::class])
class TileEmojiRegistryImpl : TileEmojiRegistry {
    /** 依 asset key 保存牌面 emoji 字元。 */
    private val emojisByAssetKey = mutableMapOf<String, String>()

    override val registrationKeys: Set<String> get() = emojisByAssetKey.keys.toSet()

    override var isFrozen: Boolean = false
        private set

    override fun register(definition: TileEmojiDefinition) {
        val assetKey = definition.assetKey
        val emoji = definition.emoji
        check(!isFrozen) { "Tile emoji registry is frozen" }
        require(assetKey !in emojisByAssetKey) {
            "Emoji already registered for tile asset key: $assetKey"
        }
        emojisByAssetKey[assetKey] = emoji
    }

    override fun freeze() {
        isFrozen = true
    }

    override fun find(assetKey: String): String? = emojisByAssetKey[assetKey]
}
