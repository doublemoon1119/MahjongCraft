package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInPaymentReasonIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.registerBuiltInGameActionVocabulary
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.registerBuiltInAiStrategyDisplayNames
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.registerBuiltInAutomaticControlDisplays
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.registerBuiltInDecisionStatusDisplayNames
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.registerBuiltInGameConfigPresentations
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.registerBuiltInRuleModuleDisplayNames
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.achievement.registerBuiltInRiichiAchievements
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.catalogue.registerBuiltInRuleCatalogues
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.registerBuiltInRiichiActionSounds
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.registerBuiltInRiichiReasons
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.registerBuiltInRiichiRoundInfoLineDisplays
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.registerBuiltInRiichiTableProps
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.registerRiichiDecisionStatusDisplayNames
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.registerRiichiGameActionVocabulary
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.registerBuiltInMatchSettlementTemplate
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.registerBuiltInWinSettlementTemplates
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.registerBuiltInWinCelebrationShowcases
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.registerBuiltInTileAssets
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.registerBuiltInTileDisplayNames
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.registerBuiltInTileEmojis
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.registerBuiltInTileLabels

/**
 * 將平台發現的第三方 [MinecraftMahjongExtension] 登記至 runtime 實際使用的
 * [MinecraftTileAssetRegistry]／[AiStrategyDisplayNameRegistry]／[TileDisplayNameRegistry]／
 * [RuleModuleDisplayNameRegistry]／[TileEmojiRegistry]／[TileLabelRegistry] 及其他 Minecraft 呈現 registry，
 * 完成後統一凍結。
 *
 * 版本與 loader 無關；loader adapter 只負責發現 extension 並呼叫此物件，不自行實作註冊順序或凍結
 * 時機。
 */
object MinecraftMahjongExtensionRegistrar {
    /**
     * 先註冊內建映射，再依 [extensions] 順序登記各 extension 的映射，全部成功後凍結所有 registry。
     *
     * @return 依來源分組的所有映射：內建映射在前，接著依 [extensions] 順序排列每個 extension，供呼叫端記錄診斷資訊。
     * @throws MinecraftMahjongExtensionRegistrationException 若任一 extension 註冊失敗。
     */
    fun registerAndFreeze(
        extensions: Iterable<MinecraftMahjongExtension>,
        registries: MinecraftPresentationRegistries,
    ): MinecraftMahjongExtensionRegistrationResult {
        registries.tileAssetRegistry.registerBuiltInTileAssets()
        registries.aiStrategyDisplayNameRegistry.registerBuiltInAiStrategyDisplayNames()
        registries.automaticControlDisplayRegistry.registerBuiltInAutomaticControlDisplays()
        registries.tileDisplayNameRegistry.registerBuiltInTileDisplayNames()
        registries.ruleModuleDisplayNameRegistry.registerBuiltInRuleModuleDisplayNames()
        registries.tileEmojiRegistry.registerBuiltInTileEmojis()
        registries.tileLabelRegistry.registerBuiltInTileLabels()
        registries.winCelebrationShowcaseRegistry.registerBuiltInWinCelebrationShowcases()
        registries.exhaustiveDrawReasonDisplayNameRegistry.registerBuiltInRiichiReasons()
        registries.winSettlementTemplateRegistry.registerBuiltInWinSettlementTemplates()
        registries.matchSettlementTemplateRegistry.registerBuiltInMatchSettlementTemplate()
        registries.publicPlayerIndicatorDisplayRegistry.register(
            RiichiRuleModule.RIICHI_INDICATOR_ID,
            PublicPlayerIndicatorDisplay(MinecraftMessageKeys.PLAYER_INDICATOR_RIICHI),
        )
        registries.publicPlayerIndicatorDisplayRegistry.register(
            BuiltInPaymentReasonIds.PAO,
            PublicPlayerIndicatorDisplay(MinecraftMessageKeys.PLAYER_INDICATOR_PAO, colorRgb = PAO_PAYMENT_REASON_COLOR),
        )
        registries.gameConfigPresentationRegistry.registerBuiltInGameConfigPresentations()
        registries.gameActionSoundPresentationRegistry.registerBuiltInRiichiActionSounds()
        registries.roundInfoLineDisplayRegistry.registerBuiltInRiichiRoundInfoLineDisplays()
        registries.gameActionVocabularyRegistry.registerBuiltInGameActionVocabulary()
        registries.gameActionVocabularyRegistry.registerRiichiGameActionVocabulary()
        registries.decisionStatusDisplayNameRegistry.registerBuiltInDecisionStatusDisplayNames()
        registries.decisionStatusDisplayNameRegistry.registerRiichiDecisionStatusDisplayNames()
        registries.tablePropDescriberRegistry.registerBuiltInRiichiTableProps()
        registries.ruleCatalogueRegistry.registerBuiltInRuleCatalogues()
        registries.gameAchievementResolverRegistry.registerBuiltInRiichiAchievements()
        var snapshot = registries.registrationSnapshot()
        val sources = mutableListOf(MinecraftPresentationRegistrationSource(extensionId = null, categories = snapshot.categories.nonEmpty()))

        val registeredExtensionIds = mutableSetOf<String>()
        extensions.forEach { extension ->
            if (!registeredExtensionIds.add(extension.id)) {
                throw MinecraftMahjongExtensionRegistrationException(
                    extension.id,
                    IllegalArgumentException("Duplicate Minecraft Mahjong extension id: ${extension.id}"),
                )
            }
            try {
                extension.registerTileAssets(registries.tileAssetRegistry)
                extension.registerAiStrategyDisplayNames(registries.aiStrategyDisplayNameRegistry)
                extension.registerAutomaticControlDisplays(registries.automaticControlDisplayRegistry)
                extension.registerTileDisplayNames(registries.tileDisplayNameRegistry)
                extension.registerRuleModuleDisplayNames(registries.ruleModuleDisplayNameRegistry)
                extension.registerTileEmojis(registries.tileEmojiRegistry)
                extension.registerTileLabels(registries.tileLabelRegistry)
                extension.registerRuleCatalogues(registries.ruleCatalogueRegistry)
                extension.registerGameAchievementResolvers(registries.gameAchievementResolverRegistry)
                extension.registerWinCelebrationShowcases(registries.winCelebrationShowcaseRegistry)
                extension.registerGameActionVocabulary(registries.gameActionVocabularyRegistry)
                extension.registerDecisionStatusDisplayNames(registries.decisionStatusDisplayNameRegistry)
                extension.registerGameActionSounds(registries.gameActionSoundPresentationRegistry)
                extension.registerExhaustiveDrawReasonDisplayNames(registries.exhaustiveDrawReasonDisplayNameRegistry)
                extension.registerRoundPreparationDisplayNames(registries.roundPreparationDisplayNameRegistry)
                extension.registerWinSettlementPresentationTemplates(registries.winSettlementTemplateRegistry)
                extension.registerMatchSettlementPresentationTemplates(registries.matchSettlementTemplateRegistry)
                extension.registerPlayerPortraitSources(registries.playerPortraitSourceRegistry)
                extension.registerPublicPlayerIndicatorDisplays(registries.publicPlayerIndicatorDisplayRegistry)
                extension.registerGameConfigPresentations(registries.gameConfigPresentationRegistry)
                extension.registerRoomMemberAppearanceSources(registries.roomMemberAppearanceSourceRegistry)
                extension.registerRoundInfoPresentations(registries.roundInfoLineDisplayRegistry)
                extension.registerTablePropDescribers(registries.tablePropDescriberRegistry)
            } catch (cause: Exception) {
                throw MinecraftMahjongExtensionRegistrationException(extension.id, cause)
            }
            val after = registries.registrationSnapshot()
            sources += MinecraftPresentationRegistrationSource(
                extensionId = extension.id,
                categories = snapshot.additionsSince(after).nonEmpty(),
            )
            snapshot = after
        }

        registries.freezeAll()
        return MinecraftMahjongExtensionRegistrationResult(sources = sources)
    }

    /** 只保留有登記項目的類別。 */
    private fun List<MinecraftPresentationRegistrationSnapshotCategory>.nonEmpty() = filter { it.registrationKeys.isNotEmpty() }

    /** 包牌付款原因的文字顏色；與分數增減的紅綠色區隔。 */
    private const val PAO_PAYMENT_REASON_COLOR: Int = 0xFFB05C
}

/**
 * [MinecraftMahjongExtensionRegistrar.registerAndFreeze] 依來源分組的登記結果。
 *
 * @property sources 內建映射在前，接著依登記順序排列每個 extension。
 */
data class MinecraftMahjongExtensionRegistrationResult(
    val sources: List<MinecraftPresentationRegistrationSource>,
)

/**
 * 一個來源登記的 Minecraft 呈現映射。
 *
 * @property extensionId 登記這些映射的 extension ID；為 null 時代表內建映射。
 * @property categories 這個來源登記的非空類別。
 */
data class MinecraftPresentationRegistrationSource(
    val extensionId: String?,
    val categories: List<MinecraftPresentationRegistrationSnapshotCategory>,
)

/** 表示指定 Minecraft extension 無法完成 registry 註冊。 */
class MinecraftMahjongExtensionRegistrationException(
    extensionId: String,
    cause: Throwable,
) : IllegalStateException("Failed to register Minecraft Mahjong extension: $extensionId", cause)
