package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInPaymentReasonIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.registerBuiltInAiStrategyDisplayNames
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.registerBuiltInGameConfigPresentations
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.registerBuiltInRuleModuleDisplayNames
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.registerBuiltInRiichiActionSounds
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.registerBuiltInRiichiReasons
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.registerBuiltInRiichiRoundInfoLineDisplays
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.registerBuiltInRiichiTableProps
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
     * 先註冊內建映射，再依 [extensions] 順序登記第三方映射，全部成功後凍結所有 registry。
     *
     * @return 依 [extensions] 順序登記的第三方映射，不含內建映射，供呼叫端記錄診斷資訊。
     * @throws MinecraftMahjongExtensionRegistrationException 若任一 extension 註冊失敗。
     */
    fun registerAndFreeze(
        extensions: Iterable<MinecraftMahjongExtension>,
        registries: MinecraftPresentationRegistries,
    ): MinecraftMahjongExtensionRegistrationResult {
        registries.tileAssetRegistry.registerBuiltInTileAssets()
        registries.aiStrategyDisplayNameRegistry.registerBuiltInAiStrategyDisplayNames()
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
        registries.tablePropDescriberRegistry.registerBuiltInRiichiTableProps()
        val baseline = registries.registrationSnapshot()

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
                extension.registerTileDisplayNames(registries.tileDisplayNameRegistry)
                extension.registerRuleModuleDisplayNames(registries.ruleModuleDisplayNameRegistry)
                extension.registerTileEmojis(registries.tileEmojiRegistry)
                extension.registerTileLabels(registries.tileLabelRegistry)
                extension.registerWinCelebrationShowcases(registries.winCelebrationShowcaseRegistry)
                extension.registerGameActionDisplayNames(registries.gameActionDisplayNameRegistry)
                extension.registerGameActionSounds(registries.gameActionSoundPresentationRegistry)
                extension.registerExhaustiveDrawReasonDisplayNames(registries.exhaustiveDrawReasonDisplayNameRegistry)
                extension.registerRoundPreparationDisplayNames(registries.roundPreparationDisplayNameRegistry)
                extension.registerWinSettlementPresentationTemplates(registries.winSettlementTemplateRegistry)
                extension.registerMatchSettlementPresentationTemplates(registries.matchSettlementTemplateRegistry)
                extension.registerPlayerPortraitSources(registries.playerPortraitSourceRegistry)
                extension.registerPublicPlayerIndicatorDisplays(registries.publicPlayerIndicatorDisplayRegistry)
                extension.registerGameConfigPresentations(registries.gameConfigPresentationRegistry)
                extension.registerRoomMemberAppearanceSources(registries.roomMemberAppearanceSourceRegistry)
                extension.registerRoundInfoLineDisplays(registries.roundInfoLineDisplayRegistry)
                extension.registerTablePropDescribers(registries.tablePropDescriberRegistry)
            } catch (cause: Exception) {
                throw MinecraftMahjongExtensionRegistrationException(extension.id, cause)
            }
        }

        registries.freezeAll()
        return MinecraftMahjongExtensionRegistrationResult(
            categories = baseline.additionsSince(registries.registrationSnapshot()),
        )
    }

    /** 包牌付款原因的文字顏色；與分數增減的紅綠色區隔。 */
    private const val PAO_PAYMENT_REASON_COLOR: Int = 0xFFB05C
}

/** [MinecraftMahjongExtensionRegistrar.registerAndFreeze] 登記的第三方呈現分類。 */
data class MinecraftMahjongExtensionRegistrationResult(
    val categories: List<MinecraftPresentationRegistrationSnapshotCategory>,
)

/** 表示指定第三方 Minecraft extension 無法完成 registry 註冊。 */
class MinecraftMahjongExtensionRegistrationException(
    extensionId: String,
    cause: Throwable,
) : IllegalStateException("Failed to register Minecraft Mahjong extension: $extensionId", cause)
