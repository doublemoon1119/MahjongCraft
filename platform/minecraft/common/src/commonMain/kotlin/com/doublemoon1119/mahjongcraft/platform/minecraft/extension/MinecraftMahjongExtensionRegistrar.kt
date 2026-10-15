package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry

/**
 * 將內建平台映射、內建規則及第三方 [MinecraftMahjongExtension] 的呈現映射登記至 runtime 實際使用的
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
     * @param extensions 依序登記的內建規則與第三方 Minecraft extension；內建規則 extension 應排在第三方之前。
     * @param registries runtime 實際使用並於成功後凍結的呈現 registries。
     * @param failurePolicy 判斷登記回呼丟出的失敗能否包成帶擴充識別碼的註冊例外；無法隔離的失敗原樣往外傳。
     * @return 依來源分組的所有映射：內建映射在前，接著依 [extensions] 順序排列每個 extension，供呼叫端記錄診斷資訊。
     * @throws MinecraftMahjongExtensionRegistrationException 若任一 extension 註冊失敗。
     */
    fun registerAndFreeze(
        extensions: Iterable<MinecraftMahjongExtension>,
        registries: MinecraftPresentationRegistries,
        failurePolicy: ExtensionFailurePolicy,
    ): MinecraftMahjongExtensionRegistrationResult {
        val registeredExtensionIds = mutableSetOf<String>()
        register(BuiltInMinecraftMahjongExtension, registries, registeredExtensionIds, failurePolicy)
        var snapshot = registries.registrationSnapshot()
        val sources = mutableListOf(
            MinecraftPresentationRegistrationSource(extensionId = null, categories = snapshot.toCategories()),
        )
        extensions.forEach { extension ->
            register(extension, registries, registeredExtensionIds, failurePolicy)
            val after = registries.registrationSnapshot()
            sources += MinecraftPresentationRegistrationSource(
                extensionId = extension.id,
                categories = snapshot.additionsSince(after),
            )
            snapshot = after
        }

        registries.freezeAll()
        return MinecraftMahjongExtensionRegistrationResult(sources = sources)
    }

    /**
     * 驗證識別碼及重複來源，並將回呼失敗包裝為具來源內容的註冊例外。
     *
     * @param extension 本次要登記的內建或第三方擴充。
     * @param registries 執行回呼時使用的共用 registry 集合。
     * @param registeredExtensionIds 已通過檢查的來源識別碼。
     * @param failurePolicy 判斷登記回呼丟出的失敗能否包成帶擴充識別碼的註冊例外；無法隔離的失敗原樣往外傳。
     */
    private fun register(
        extension: MinecraftMahjongExtension,
        registries: MinecraftPresentationRegistries,
        registeredExtensionIds: MutableSet<String>,
        failurePolicy: ExtensionFailurePolicy,
    ) {
        try {
            NamespacedId.requireValid(extension.id) { "Invalid Minecraft Mahjong extension id: ${extension.id}" }
            require(registeredExtensionIds.add(extension.id)) {
                "Duplicate Minecraft Mahjong extension id: ${extension.id}"
            }
            registerCallbacks(extension, registries)
        } catch (cause: Throwable) {
            if (!failurePolicy.isIsolatable(cause)) throw cause
            throw MinecraftMahjongExtensionRegistrationException(extension.id, cause)
        }
    }

    /**
     * 依固定順序呼叫 [extension] 的所有登記回呼；例外由外層來源驗證流程統一包裝。
     *
     * @param extension 正在登記的 extension。
     * @param registries extension 要使用的呈現 registries。
     */
    private fun registerCallbacks(
        extension: MinecraftMahjongExtension,
        registries: MinecraftPresentationRegistries,
    ) {
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
    }
}
