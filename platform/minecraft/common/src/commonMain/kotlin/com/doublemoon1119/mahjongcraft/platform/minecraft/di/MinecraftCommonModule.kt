package com.doublemoon1119.mahjongcraft.platform.minecraft.di

import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.registerBuiltInTileAssets
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/** Minecraft loader 與版本無關、client／server 共用的 Koin 定義。 */
@Module
class MinecraftCommonModule {
    /**
     * 建立已完成內建牌種 asset key 註冊並凍結的 [MinecraftTileAssetRegistry]。
     *
     * 目前尚未提供第三方 Minecraft asset 註冊入口，因此建立時立即凍結；日後加入該入口時需改為
     * 先交給第三方註冊完成後再凍結，與 [com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistry]
     * 的凍結時機保持一致的責任邊界。
     */
    @Single
    fun provideMinecraftTileAssetRegistry(): MinecraftTileAssetRegistry = MinecraftTileAssetRegistryImpl().apply {
        registerBuiltInTileAssets()
        freeze()
    }
}
