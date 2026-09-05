package com.doublemoon1119.mahjongcraft.platform.minecraft.di

import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtensionRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.preparation.RoundPreparationDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.preparation.RoundPreparationDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplateRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundPresentationRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistryImpl
import org.koin.core.annotation.Module
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

/** Minecraft loader 與版本無關、client／server 共用的 Koin 定義。 */
@Module
class MinecraftCommonModule {
    /** 建立供內建與第三方規則登記動作語音的 registry。 */
    @Single
    fun provideGameActionSoundPresentationRegistry(): GameActionSoundPresentationRegistry = GameActionSoundPresentationRegistryImpl()

    /** 建立 GUI 與聊天 hover 共用的設定解析器。 */
    @Single
    fun provideGameConfigPresentationResolver(
        registry: GameConfigPresentationRegistry,
        @Provided moduleRegistry: MahjongModuleRegistry,
    ): GameConfigPresentationResolver = GameConfigPresentationResolver(registry, moduleRegistry)

    /** 建立房間規則設定的宣告式呈現 registry。 */
    @Single
    fun provideGameConfigPresentationRegistry(): GameConfigPresentationRegistry = GameConfigPresentationRegistryImpl()

    /** 建立房間成員外觀來源 registry。 */
    @Single
    fun provideRoomMemberAppearanceSourceRegistry(): RoomMemberAppearanceSourceRegistry = RoomMemberAppearanceSourceRegistryImpl()

    /** 建立公開玩家 indicator 的本地化顯示 registry。 */
    @Single
    fun providePublicPlayerIndicatorDisplayRegistry(): PublicPlayerIndicatorDisplayRegistry = PublicPlayerIndicatorDisplayRegistryImpl()

    /** 建立供第三方宣告玩家頭像來源的凍結式 registry。 */
    @Single
    fun providePlayerPortraitSourceRegistry(): PlayerPortraitSourceRegistry = PlayerPortraitSourceRegistryImpl()

    /** 建立供第三方 preparation step／option 登記本地化名稱的 registry。 */
    @Single
    fun provideRoundPreparationDisplayNameRegistry(): RoundPreparationDisplayNameRegistry = RoundPreparationDisplayNameRegistryImpl()

    /** 建立供內建與第三方 extension 登記的終局結算模板 registry。 */
    @Single
    fun provideMatchSettlementPresentationTemplateRegistry(): MatchSettlementPresentationTemplateRegistry = MatchSettlementPresentationTemplateRegistryImpl()

    /** 建立供內建與第三方 extension 登記的胡牌結算模板 registry。 */
    @Single
    fun provideWinSettlementPresentationTemplateRegistry(): WinSettlementPresentationTemplateRegistry = WinSettlementPresentationTemplateRegistryImpl()

    /** 建立供內建與第三方 extension 登記的流局原因名稱 registry。 */
    @Single
    fun provideExhaustiveDrawReasonDisplayNameRegistry(): ExhaustiveDrawReasonDisplayNameRegistry = ExhaustiveDrawReasonDisplayNameRegistryImpl()

    /** 建立供內建與第三方 extension 登記的動作顯示名稱 registry。 */
    @Single
    fun provideGameActionDisplayNameRegistry(): GameActionDisplayNameRegistry = GameActionDisplayNameRegistryImpl()

    /** 建立供內建與第三方 extension 登記的胡牌 showcase registry。 */
    @Single
    fun provideWinCelebrationShowcaseRegistry(): WinCelebrationShowcaseRegistry = WinCelebrationShowcaseRegistryImpl()

    /**
     * 建立由 Koin 管理的 runtime [MinecraftTileAssetRegistry]。
     *
     * 平台必須將此 single 交給 [MinecraftMahjongExtensionRegistrar]，
     * 讓內建與第三方 asset key 在渲染流程解析前完成註冊及凍結；
     * 與 [TileTypeRegistry] 的凍結時機保持一致的責任邊界。
     */
    @Single
    fun provideMinecraftTileAssetRegistry(): MinecraftTileAssetRegistry = MinecraftTileAssetRegistryImpl()

    /**
     * 建立由 Koin 管理的 runtime [TileEmojiRegistry]。
     *
     * 凍結時機與 [provideMinecraftTileAssetRegistry] 一致，同樣交給 [MinecraftMahjongExtensionRegistrar]
     * 負責內建與第三方登記。
     */
    @Single
    fun provideTileEmojiRegistry(): TileEmojiRegistry = TileEmojiRegistryImpl()

    /**
     * 建立由 Koin 管理的 runtime [TileLabelRegistry]。
     *
     * 凍結時機與 [provideMinecraftTileAssetRegistry] 一致，同樣交給 [MinecraftMahjongExtensionRegistrar]
     * 負責內建與第三方登記。
     */
    @Single
    fun provideTileLabelRegistry(): TileLabelRegistry = TileLabelRegistryImpl()
}
