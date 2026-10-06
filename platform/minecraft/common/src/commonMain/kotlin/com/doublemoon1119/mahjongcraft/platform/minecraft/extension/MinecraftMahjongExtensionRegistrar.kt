package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry

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
     * 先登記 [BuiltInMinecraftMahjongExtension] 的規則中立映射，再依 [extensions] 順序登記各 extension 的映射，全部成功後凍結所有 registry。
     *
     * 內建規則的映射由 [BundledMinecraftMahjongExtensions] 提供，呼叫端應把它們排在第三方 extension 之前。
     *
     * @return 依來源分組的所有映射：內建映射在前，接著依 [extensions] 順序排列每個 extension，供呼叫端記錄診斷資訊。
     * @throws MinecraftMahjongExtensionRegistrationException 若任一 extension 註冊失敗。
     */
    fun registerAndFreeze(
        extensions: Iterable<MinecraftMahjongExtension>,
        registries: MinecraftPresentationRegistries,
    ): MinecraftMahjongExtensionRegistrationResult {
        registerCallbacks(BuiltInMinecraftMahjongExtension, registries)
        var snapshot = registries.registrationSnapshot()
        val sources = mutableListOf(MinecraftPresentationRegistrationSource(extensionId = null, categories = snapshot.categories.nonEmpty()))

        val registeredExtensionIds = mutableSetOf(BuiltInMinecraftMahjongExtension.id)
        extensions.forEach { extension ->
            if (!registeredExtensionIds.add(extension.id)) {
                throw MinecraftMahjongExtensionRegistrationException(
                    extension.id,
                    IllegalArgumentException("Duplicate Minecraft Mahjong extension id: ${extension.id}"),
                )
            }
            registerCallbacks(extension, registries)
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

    /** 依固定順序呼叫 [extension] 的所有登記回呼；任一回呼失敗時包裝成 [MinecraftMahjongExtensionRegistrationException]。 */
    private fun registerCallbacks(
        extension: MinecraftMahjongExtension,
        registries: MinecraftPresentationRegistries,
    ) {
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
            extension.registerRoundOutcomeDisplayNames(registries.roundOutcomeDisplayNameRegistry)
            extension.registerHistoryDiscardMarkerDisplays(registries.historyDiscardMarkerDisplayRegistry)
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
    }

    /** 只保留有登記項目的類別。 */
    private fun List<MinecraftPresentationRegistrationSnapshotCategory>.nonEmpty() = filter { it.registrationKeys.isNotEmpty() }
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
