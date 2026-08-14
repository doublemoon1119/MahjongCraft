package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.registerBuiltInTileAssets

/**
 * 將平台發現的第三方 [MinecraftMahjongExtension] 登記至 runtime 實際使用的
 * [MinecraftTileAssetRegistry]，完成後凍結。
 *
 * 版本與 loader 無關；loader adapter 只負責發現 extension 並呼叫此物件，不自行實作註冊順序或凍結
 * 時機。
 */
object MinecraftMahjongExtensionRegistrar {
    /**
     * 先註冊內建映射，再依 [extensions] 順序登記第三方映射，全部成功後凍結 [registry]。
     *
     * @return 依 [extensions] 順序登記的第三方 asset key，不含內建映射，供呼叫端記錄診斷資訊。
     * @throws MinecraftMahjongExtensionRegistrationException 若任一 extension 註冊失敗。
     */
    fun registerAndFreeze(
        extensions: Iterable<MinecraftMahjongExtension>,
        registry: MinecraftTileAssetRegistry,
    ): List<String> {
        registry.registerBuiltInTileAssets()

        val thirdPartyAssetKeys = mutableListOf<String>()
        val recordingRegistry = RecordingMinecraftTileAssetRegistry(registry, thirdPartyAssetKeys)

        val registeredExtensionIds = mutableSetOf<String>()
        extensions.forEach { extension ->
            if (!registeredExtensionIds.add(extension.id)) {
                throw MinecraftMahjongExtensionRegistrationException(
                    extension.id,
                    IllegalArgumentException("Duplicate Minecraft Mahjong extension id: ${extension.id}"),
                )
            }
            try {
                extension.registerTileAssets(recordingRegistry)
            } catch (cause: Exception) {
                throw MinecraftMahjongExtensionRegistrationException(extension.id, cause)
            }
        }

        registry.freeze()
        return thirdPartyAssetKeys
    }
}

/** 表示指定第三方 Minecraft extension 無法完成 asset key registry 註冊。 */
class MinecraftMahjongExtensionRegistrationException(
    extensionId: String,
    cause: Throwable,
) : IllegalStateException("Failed to register Minecraft Mahjong extension: $extensionId", cause)

/** 轉發至 [delegate]，並額外把第三方註冊的 asset key 記錄進 [recorded]，供診斷用途。 */
private class RecordingMinecraftTileAssetRegistry(
    private val delegate: MinecraftTileAssetRegistry,
    private val recorded: MutableList<String>,
) : MinecraftTileAssetRegistry {
    override val isFrozen: Boolean get() = delegate.isFrozen

    override fun register(typeId: TileTypeId, assetKey: String) {
        delegate.register(typeId, assetKey)
        recorded += assetKey
    }

    override fun freeze() = delegate.freeze()

    override fun find(typeId: TileTypeId): String? = delegate.find(typeId)
}
