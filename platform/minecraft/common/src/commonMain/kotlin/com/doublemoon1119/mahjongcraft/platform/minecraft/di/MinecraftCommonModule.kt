package com.doublemoon1119.mahjongcraft.platform.minecraft.di

import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistryImpl
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/** Minecraft loader 與版本無關、client／server 共用的 Koin 定義。 */
@Module
class MinecraftCommonModule {
    /**
     * 建立由 Koin 管理的 runtime [MinecraftTileAssetRegistry]。
     *
     * 平台必須將此 single 交給
     * [com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtensionRegistrar]，
     * 讓內建與第三方 asset key 在渲染流程解析前完成註冊及凍結；與
     * [com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistry] 的凍結時機保持一致的責任邊界。
     */
    @Single
    fun provideMinecraftTileAssetRegistry(): MinecraftTileAssetRegistry = MinecraftTileAssetRegistryImpl()

    /**
     * 建立由 Koin 管理的 runtime [TileEmojiRegistry]。
     *
     * 凍結時機與 [provideMinecraftTileAssetRegistry] 一致，同樣交給
     * [com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtensionRegistrar]
     * 負責內建與第三方登記。
     */
    @Single
    fun provideTileEmojiRegistry(): TileEmojiRegistry = TileEmojiRegistryImpl()

    /**
     * 建立由 Koin 管理的 runtime [TileLabelRegistry]。
     *
     * 凍結時機與 [provideMinecraftTileAssetRegistry] 一致，同樣交給
     * [com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtensionRegistrar]
     * 負責內建與第三方登記。
     */
    @Single
    fun provideTileLabelRegistry(): TileLabelRegistry = TileLabelRegistryImpl()
}
