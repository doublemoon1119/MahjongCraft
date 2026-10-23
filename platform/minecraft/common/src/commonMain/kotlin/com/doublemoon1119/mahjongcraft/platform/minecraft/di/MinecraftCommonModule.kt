package com.doublemoon1119.mahjongcraft.platform.minecraft.di

import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolverRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolverRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.RuleDecisionDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.RuleDecisionDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftPresentationRegistries
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistryImpl
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
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistryImpl
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

/**
 * Minecraft loader 與版本無關、client／server 共用的 Koin 定義。
 *
 * [MinecraftPresentationRegistries] 把同一份 presentation registry 組成 bootstrap 註冊及凍結使用的集合，不建立第二份實例。
 * server 專用狀態由 [minecraftServerModule] 提供；共用名稱來源直接由本模組綁定，原生文字轉換留在對應平台 adapter，
 * 不為純委派另外建立 loader registry。
 */
val minecraftCommonModule = module {
    single<MinecraftPresentationRegistries>()

    single<GameAchievementResolverRegistryImpl>() bind GameAchievementResolverRegistry::class

    single<GameActionVocabularyRegistryImpl>() bind GameActionVocabularyRegistry::class

    single<AiStrategyDisplayNameRegistryImpl>() bind AiStrategyDisplayNameRegistry::class

    single<AutomaticControlDisplayRegistryImpl>() bind AutomaticControlDisplayRegistry::class

    single<RuleCatalogueRegistryImpl>() bind RuleCatalogueRegistry::class

    single<DecisionStatusDisplayNameRegistryImpl>() bind DecisionStatusDisplayNameRegistry::class

    single<HistoryDiscardMarkerDisplayRegistryImpl>() bind HistoryDiscardMarkerDisplayRegistry::class

    single<PlayerPortraitSourceRegistryImpl>() bind PlayerPortraitSourceRegistry::class
    single<PublicPlayerIndicatorDisplayRegistryImpl>() bind PublicPlayerIndicatorDisplayRegistry::class

    single<RuleDecisionDisplayNameRegistryImpl>() bind RuleDecisionDisplayNameRegistry::class

    single<GameConfigPresentationRegistryImpl>() bind GameConfigPresentationRegistry::class
    single<GameConfigPresentationResolver>()
    single<RoomMemberAppearanceSourceRegistryImpl>() bind RoomMemberAppearanceSourceRegistry::class

    single<RuleModuleDisplayNameRegistryImpl>() bind RuleModuleDisplayNameRegistry::class

    single<ExhaustiveDrawReasonDisplayNameRegistryImpl>() bind ExhaustiveDrawReasonDisplayNameRegistry::class
    single<MatchSettlementPresentationTemplateRegistryImpl>() bind MatchSettlementPresentationTemplateRegistry::class
    single<RoundOutcomeDisplayNameRegistryImpl>() bind RoundOutcomeDisplayNameRegistry::class
    single<WinSettlementPresentationTemplateRegistryImpl>() bind WinSettlementPresentationTemplateRegistry::class

    single<WinCelebrationShowcaseRegistryImpl>() bind WinCelebrationShowcaseRegistry::class

    single<GameActionSoundPresentationRegistryImpl>() bind GameActionSoundPresentationRegistry::class

    single<RoundInfoLineDisplayRegistryImpl>() bind RoundInfoLineDisplayRegistry::class
    single<TablePropDescriberRegistryImpl>() bind TablePropDescriberRegistry::class

    single<MinecraftTileAssetRegistryImpl>() bind MinecraftTileAssetRegistry::class
    single<TileDisplayNameRegistryImpl>() bind TileDisplayNameRegistry::class
    single<TileEmojiRegistryImpl>() bind TileEmojiRegistry::class
    single<TileLabelRegistryImpl>() bind TileLabelRegistry::class
}
