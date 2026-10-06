package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolverRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.registerRiichiGameConfigPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.registerTaiwanGameConfigPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.registerRiichiRuleModuleDisplayName
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.registerTaiwanRuleModuleDisplayName
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.achievement.registerBuiltInRiichiAchievements
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.catalogue.registerRiichiRuleCatalogue
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.registerBuiltInRiichiActionSounds
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.registerBuiltInRiichiReasons
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.registerBuiltInRiichiRoundInfoLineDisplays
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.registerBuiltInRiichiTableProps
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.registerRiichiDecisionStatusDisplayNames
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.registerRiichiGameActionVocabulary
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.registerRiichiWinSettlementTemplates
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.registerRiichiWinCelebrationShowcases
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropDescriberRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.registerRiichiTileAssets
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.registerRiichiTileDisplayNames
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.registerRiichiTileEmojis
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.registerRiichiTileLabels
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.registerTaiwanTileAssets
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.registerTaiwanTileEmojis
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.registerTaiwanTileLabels

/** 隨 MahjongCraft 發布、一律啟用的 Minecraft 呈現 extension。 */
object BundledMinecraftMahjongExtensions {
    /** 依登記順序排列的內建規則呈現 extension；平台應排在第三方 extension 之前登記。 */
    val all: List<MinecraftMahjongExtension> = listOf(BundledRiichiMinecraftExtension, BundledTaiwanMinecraftExtension)
}

/**
 * 日麻的 Minecraft 呈現整合，與平台無關的日麻 extension 使用同一個 extension ID。
 *
 * 包含赤五的貼圖、名稱、表情與標籤、規則名稱與設定畫面、規則說明、成就、役滿演出、流局原因名稱、
 * 胡牌結算版面、動作用語、決策狀態、音效、資訊列、桌面物件說明與立直標示。
 */
object BundledRiichiMinecraftExtension : MinecraftMahjongExtension {
    override val id: String = MahjongCraftMetadata.id("riichi")

    override fun registerTileAssets(registry: MinecraftTileAssetRegistry) = registry.registerRiichiTileAssets()

    override fun registerTileDisplayNames(registry: TileDisplayNameRegistry) = registry.registerRiichiTileDisplayNames()

    override fun registerRuleModuleDisplayNames(registry: RuleModuleDisplayNameRegistry) = registry.registerRiichiRuleModuleDisplayName()

    override fun registerTileEmojis(registry: TileEmojiRegistry) = registry.registerRiichiTileEmojis()

    override fun registerTileLabels(registry: TileLabelRegistry) = registry.registerRiichiTileLabels()

    override fun registerRuleCatalogues(registry: RuleCatalogueRegistry) = registry.registerRiichiRuleCatalogue()

    override fun registerGameAchievementResolvers(registry: GameAchievementResolverRegistry) = registry.registerBuiltInRiichiAchievements()

    override fun registerWinCelebrationShowcases(registry: WinCelebrationShowcaseRegistry) = registry.registerRiichiWinCelebrationShowcases()

    override fun registerExhaustiveDrawReasonDisplayNames(registry: ExhaustiveDrawReasonDisplayNameRegistry) = registry.registerBuiltInRiichiReasons()

    override fun registerWinSettlementPresentationTemplates(registry: WinSettlementPresentationTemplateRegistry) = registry.registerRiichiWinSettlementTemplates()

    override fun registerPublicPlayerIndicatorDisplays(registry: PublicPlayerIndicatorDisplayRegistry) {
        registry.register(RiichiRuleModule.RIICHI_INDICATOR_ID, PublicPlayerIndicatorDisplay(MinecraftMessageKeys.PLAYER_INDICATOR_RIICHI))
    }

    override fun registerGameConfigPresentations(registry: GameConfigPresentationRegistry) = registry.registerRiichiGameConfigPresentation()

    override fun registerGameActionSounds(registry: GameActionSoundPresentationRegistry) = registry.registerBuiltInRiichiActionSounds()

    override fun registerRoundInfoPresentations(registry: RoundInfoLineDisplayRegistry) = registry.registerBuiltInRiichiRoundInfoLineDisplays()

    override fun registerGameActionVocabulary(registry: GameActionVocabularyRegistry) = registry.registerRiichiGameActionVocabulary()

    override fun registerDecisionStatusDisplayNames(registry: DecisionStatusDisplayNameRegistry) = registry.registerRiichiDecisionStatusDisplayNames()

    override fun registerTablePropDescribers(registry: TablePropDescriberRegistry) = registry.registerBuiltInRiichiTableProps()
}

/**
 * 台麻的 Minecraft 呈現整合，與平台無關的台麻 extension 使用同一個 extension ID。
 *
 * 包含花牌的貼圖、表情與標籤，以及規則名稱與設定畫面。
 */
object BundledTaiwanMinecraftExtension : MinecraftMahjongExtension {
    override val id: String = MahjongCraftMetadata.id("taiwan")

    override fun registerTileAssets(registry: MinecraftTileAssetRegistry) = registry.registerTaiwanTileAssets()

    override fun registerRuleModuleDisplayNames(registry: RuleModuleDisplayNameRegistry) = registry.registerTaiwanRuleModuleDisplayName()

    override fun registerTileEmojis(registry: TileEmojiRegistry) = registry.registerTaiwanTileEmojis()

    override fun registerTileLabels(registry: TileLabelRegistry) = registry.registerTaiwanTileLabels()

    override fun registerGameConfigPresentations(registry: GameConfigPresentationRegistry) = registry.registerTaiwanGameConfigPresentation()
}
