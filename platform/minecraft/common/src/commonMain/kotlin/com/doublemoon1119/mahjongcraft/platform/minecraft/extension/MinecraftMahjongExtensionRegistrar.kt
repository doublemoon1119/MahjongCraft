package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

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
     * @throws MinecraftMahjongExtensionRegistrationException 若任一 extension 註冊失敗。
     */
    fun registerAndFreeze(
        extensions: Iterable<MinecraftMahjongExtension>,
        registry: MinecraftTileAssetRegistry,
    ) {
        registry.registerBuiltInTileAssets()

        val registeredExtensionIds = mutableSetOf<String>()
        extensions.forEach { extension ->
            if (!registeredExtensionIds.add(extension.id)) {
                throw MinecraftMahjongExtensionRegistrationException(
                    extension.id,
                    IllegalArgumentException("Duplicate Minecraft Mahjong extension id: ${extension.id}"),
                )
            }
            try {
                extension.registerTileAssets(registry)
            } catch (cause: Exception) {
                throw MinecraftMahjongExtensionRegistrationException(extension.id, cause)
            }
        }

        registry.freeze()
    }
}

/** 表示指定第三方 Minecraft extension 無法完成 asset key registry 註冊。 */
class MinecraftMahjongExtensionRegistrationException(
    extensionId: String,
    cause: Throwable,
) : IllegalStateException("Failed to register Minecraft Mahjong extension: $extensionId", cause)
