package com.doublemoon1119.mahjongcraft.platform.minecraft.di

import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolverRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolverRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtensionRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftPresentationRegistries
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplayRegistryImpl
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
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplateRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundOutcomeDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundOutcomeDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundPresentationRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropDescriberRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropDescriberRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
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
    /**
     * 將 Koin 管理的 Minecraft presentation registry single 組成 bootstrap 專用集合。
     *
     * @param gameActionVocabularyRegistry 各規則動作用語來源。
     * @param automaticControlDisplayRegistry 自動操作顯示資料來源。
     * @param decisionStatusDisplayNameRegistry 捨牌分析狀態名稱來源。
     * @param aiStrategyDisplayNameRegistry AI 策略名稱來源。
     * @param playerPortraitSourceRegistry 玩家頭像來源。
     * @param publicPlayerIndicatorDisplayRegistry 公開玩家指示器顯示來源。
     * @param roundPreparationDisplayNameRegistry 開局準備名稱來源。
     * @param gameConfigPresentationRegistry 房間設定呈現來源。
     * @param roomMemberAppearanceSourceRegistry 房間成員外觀來源。
     * @param ruleModuleDisplayNameRegistry 規則模組名稱來源。
     * @param exhaustiveDrawReasonDisplayNameRegistry 流局原因名稱來源。
     * @param roundOutcomeDisplayNameRegistry 本局結果名稱來源。
     * @param historyDiscardMarkerDisplayRegistry 歷史牌河標記呈現來源。
     * @param matchSettlementTemplateRegistry 終局結算模板來源。
     * @param winSettlementTemplateRegistry 胡牌結算模板來源。
     * @param winCelebrationShowcaseRegistry 胡牌 showcase 來源。
     * @param gameActionSoundPresentationRegistry 遊戲動作音效來源。
     * @param roundInfoLineDisplayRegistry 局況顯示行來源。
     * @param tablePropDescriberRegistry 桌上物件描述來源。
     * @param tileAssetRegistry 牌面資產來源。
     * @param tileDisplayNameRegistry 牌面名稱來源。
     * @param tileEmojiRegistry 牌面 emoji 來源。
     * @param tileLabelRegistry 牌面輔助標籤來源。
     * @param ruleCatalogueRegistry 規則說明目錄來源。
     * @param gameAchievementResolverRegistry 規則專屬成果判定來源。
     * @return 供 extension bootstrap 註冊及凍結使用的呈現 registry 集合。
     */
    @Single
    fun provideMinecraftPresentationRegistries(
        gameActionVocabularyRegistry: GameActionVocabularyRegistry,
        automaticControlDisplayRegistry: AutomaticControlDisplayRegistry,
        decisionStatusDisplayNameRegistry: DecisionStatusDisplayNameRegistry,
        @Provided aiStrategyDisplayNameRegistry: AiStrategyDisplayNameRegistry,
        playerPortraitSourceRegistry: PlayerPortraitSourceRegistry,
        publicPlayerIndicatorDisplayRegistry: PublicPlayerIndicatorDisplayRegistry,
        roundPreparationDisplayNameRegistry: RoundPreparationDisplayNameRegistry,
        gameConfigPresentationRegistry: GameConfigPresentationRegistry,
        roomMemberAppearanceSourceRegistry: RoomMemberAppearanceSourceRegistry,
        @Provided ruleModuleDisplayNameRegistry: RuleModuleDisplayNameRegistry,
        exhaustiveDrawReasonDisplayNameRegistry: ExhaustiveDrawReasonDisplayNameRegistry,
        roundOutcomeDisplayNameRegistry: RoundOutcomeDisplayNameRegistry,
        historyDiscardMarkerDisplayRegistry: HistoryDiscardMarkerDisplayRegistry,
        matchSettlementTemplateRegistry: MatchSettlementPresentationTemplateRegistry,
        winSettlementTemplateRegistry: WinSettlementPresentationTemplateRegistry,
        winCelebrationShowcaseRegistry: WinCelebrationShowcaseRegistry,
        gameActionSoundPresentationRegistry: GameActionSoundPresentationRegistry,
        roundInfoLineDisplayRegistry: RoundInfoLineDisplayRegistry,
        tablePropDescriberRegistry: TablePropDescriberRegistry,
        tileAssetRegistry: MinecraftTileAssetRegistry,
        @Provided tileDisplayNameRegistry: TileDisplayNameRegistry,
        tileEmojiRegistry: TileEmojiRegistry,
        tileLabelRegistry: TileLabelRegistry,
        ruleCatalogueRegistry: RuleCatalogueRegistry,
        gameAchievementResolverRegistry: GameAchievementResolverRegistry,
    ): MinecraftPresentationRegistries = MinecraftPresentationRegistries(
        tileAssetRegistry = tileAssetRegistry,
        tileDisplayNameRegistry = tileDisplayNameRegistry,
        tileEmojiRegistry = tileEmojiRegistry,
        tileLabelRegistry = tileLabelRegistry,
        ruleCatalogueRegistry = ruleCatalogueRegistry,
        gameActionVocabularyRegistry = gameActionVocabularyRegistry,
        automaticControlDisplayRegistry = automaticControlDisplayRegistry,
        decisionStatusDisplayNameRegistry = decisionStatusDisplayNameRegistry,
        gameActionSoundPresentationRegistry = gameActionSoundPresentationRegistry,
        exhaustiveDrawReasonDisplayNameRegistry = exhaustiveDrawReasonDisplayNameRegistry,
        roundOutcomeDisplayNameRegistry = roundOutcomeDisplayNameRegistry,
        historyDiscardMarkerDisplayRegistry = historyDiscardMarkerDisplayRegistry,
        roundPreparationDisplayNameRegistry = roundPreparationDisplayNameRegistry,
        roundInfoLineDisplayRegistry = roundInfoLineDisplayRegistry,
        tablePropDescriberRegistry = tablePropDescriberRegistry,
        winCelebrationShowcaseRegistry = winCelebrationShowcaseRegistry,
        winSettlementTemplateRegistry = winSettlementTemplateRegistry,
        matchSettlementTemplateRegistry = matchSettlementTemplateRegistry,
        aiStrategyDisplayNameRegistry = aiStrategyDisplayNameRegistry,
        ruleModuleDisplayNameRegistry = ruleModuleDisplayNameRegistry,
        playerPortraitSourceRegistry = playerPortraitSourceRegistry,
        publicPlayerIndicatorDisplayRegistry = publicPlayerIndicatorDisplayRegistry,
        roomMemberAppearanceSourceRegistry = roomMemberAppearanceSourceRegistry,
        gameConfigPresentationRegistry = gameConfigPresentationRegistry,
        gameAchievementResolverRegistry = gameAchievementResolverRegistry,
    )

    /** 建立供內建與第三方 extension 登記規則專屬成果判定的 registry。 */
    @Single
    fun provideGameAchievementResolverRegistry(): GameAchievementResolverRegistry = GameAchievementResolverRegistryImpl()

    /** 建立供內建與第三方 extension 登記的自動操作顯示 registry。 */
    @Single
    fun provideAutomaticControlDisplayRegistry(): AutomaticControlDisplayRegistry = AutomaticControlDisplayRegistryImpl()

    /** 建立供內建與第三方規則登記動作語音的 registry。 */
    @Single
    fun provideGameActionSoundPresentationRegistry(): GameActionSoundPresentationRegistry = GameActionSoundPresentationRegistryImpl()

    /**
     * 建立 GUI 與聊天 hover 共用的設定解析器。
     *
     * @param registry 宣告式設定呈現資料來源。
     * @param moduleRegistry 提供設定對應規則模組的 registry。
     * @return 可解析 GUI 與聊天 hover 設定呈現的元件。
     */
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

    /** 建立供內建與第三方 extension 登記的規則特殊本局結果名稱 registry。 */
    @Single
    fun provideRoundOutcomeDisplayNameRegistry(): RoundOutcomeDisplayNameRegistry = RoundOutcomeDisplayNameRegistryImpl()

    /** 建立供內建與第三方 extension 登記的歷史牌河公開標記呈現 registry。 */
    @Single
    fun provideHistoryDiscardMarkerDisplayRegistry(): HistoryDiscardMarkerDisplayRegistry = HistoryDiscardMarkerDisplayRegistryImpl()

    /** 建立供內建與第三方 extension 登記的動作顯示名稱 registry。 */
    @Single
    fun provideGameActionVocabularyRegistry(): GameActionVocabularyRegistry = GameActionVocabularyRegistryImpl()

    /** 建立捨牌分析狀態顯示名稱 registry。 */
    @Single
    fun provideDecisionStatusDisplayNameRegistry(): DecisionStatusDisplayNameRegistry = DecisionStatusDisplayNameRegistryImpl()

    /** 建立供內建與第三方 extension 登記的局況顯示行 registry。 */
    @Single
    fun provideRoundInfoLineDisplayRegistry(): RoundInfoLineDisplayRegistry = RoundInfoLineDisplayRegistryImpl()

    /** 建立供內建與第三方 extension 登記的規則桌面物件描述 registry。 */
    @Single
    fun provideTablePropDescriberRegistry(): TablePropDescriberRegistry = TablePropDescriberRegistryImpl()

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

    /** 建立供內建與第三方 extension 登記規則說明目錄來源的 registry。 */
    @Single
    fun provideRuleCatalogueRegistry(): RuleCatalogueRegistry = RuleCatalogueRegistryImpl()
}
