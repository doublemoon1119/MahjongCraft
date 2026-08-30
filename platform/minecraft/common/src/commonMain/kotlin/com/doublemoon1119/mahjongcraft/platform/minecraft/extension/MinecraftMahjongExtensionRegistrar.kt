package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.registerBuiltInAiStrategyDisplayNames
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.preparation.RoundPreparationDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.preparation.RoundPreparationDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.registerBuiltInRuleModuleDisplayNames
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplateRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.registerBuiltInMatchSettlementTemplate
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.registerBuiltInRiichiReasons
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.registerBuiltInWinSettlementTemplates
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.registerBuiltInWinCelebrationShowcases
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabel
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
        tileAssetRegistry: MinecraftTileAssetRegistry,
        aiStrategyDisplayNameRegistry: AiStrategyDisplayNameRegistry,
        tileDisplayNameRegistry: TileDisplayNameRegistry,
        ruleModuleDisplayNameRegistry: RuleModuleDisplayNameRegistry,
        tileEmojiRegistry: TileEmojiRegistry,
        tileLabelRegistry: TileLabelRegistry,
        showcaseRegistry: WinCelebrationShowcaseRegistry = WinCelebrationShowcaseRegistryImpl(),
        gameActionDisplayNameRegistry: GameActionDisplayNameRegistry = GameActionDisplayNameRegistryImpl(),
        exhaustiveDrawReasonDisplayNameRegistry: ExhaustiveDrawReasonDisplayNameRegistry =
            ExhaustiveDrawReasonDisplayNameRegistryImpl(),
        roundPreparationDisplayNameRegistry: RoundPreparationDisplayNameRegistry =
            RoundPreparationDisplayNameRegistryImpl(),
        winSettlementTemplateRegistry: WinSettlementPresentationTemplateRegistry =
            WinSettlementPresentationTemplateRegistryImpl(),
        matchSettlementTemplateRegistry: MatchSettlementPresentationTemplateRegistry =
            MatchSettlementPresentationTemplateRegistryImpl(),
        playerPortraitSourceRegistry: PlayerPortraitSourceRegistry = PlayerPortraitSourceRegistryImpl(),
        publicPlayerIndicatorDisplayRegistry: PublicPlayerIndicatorDisplayRegistry =
            PublicPlayerIndicatorDisplayRegistryImpl(),
    ): MinecraftMahjongExtensionRegistrationResult {
        tileAssetRegistry.registerBuiltInTileAssets()
        aiStrategyDisplayNameRegistry.registerBuiltInAiStrategyDisplayNames()
        tileDisplayNameRegistry.registerBuiltInTileDisplayNames()
        ruleModuleDisplayNameRegistry.registerBuiltInRuleModuleDisplayNames()
        tileEmojiRegistry.registerBuiltInTileEmojis()
        tileLabelRegistry.registerBuiltInTileLabels()
        showcaseRegistry.registerBuiltInWinCelebrationShowcases()
        exhaustiveDrawReasonDisplayNameRegistry.registerBuiltInRiichiReasons()
        winSettlementTemplateRegistry.registerBuiltInWinSettlementTemplates()
        matchSettlementTemplateRegistry.registerBuiltInMatchSettlementTemplate()
        publicPlayerIndicatorDisplayRegistry.register(
            RiichiRuleModule.RIICHI_INDICATOR_ID,
            PublicPlayerIndicatorDisplay(MinecraftMessageKeys.PLAYER_INDICATOR_RIICHI),
        )

        val thirdPartyAssetKeys = mutableListOf<String>()
        val thirdPartyAiStrategyKeys = mutableListOf<String>()
        val thirdPartyTileDisplayNameKeys = mutableListOf<String>()
        val thirdPartyRuleModuleDisplayNameKeys = mutableListOf<String>()
        val thirdPartyTileEmojiKeys = mutableListOf<String>()
        val thirdPartyTileLabelKeys = mutableListOf<String>()
        val thirdPartyShowcaseKeys = mutableListOf<String>()
        val thirdPartyGameActionIds = mutableListOf<String>()
        val thirdPartyPortraitProviderIds = mutableListOf<String>()
        val recordingTileAssetRegistry = RecordingMinecraftTileAssetRegistry(tileAssetRegistry, thirdPartyAssetKeys)
        val recordingAiStrategyDisplayNameRegistry =
            RecordingAiStrategyDisplayNameRegistry(aiStrategyDisplayNameRegistry, thirdPartyAiStrategyKeys)
        val recordingTileDisplayNameRegistry =
            RecordingTileDisplayNameRegistry(tileDisplayNameRegistry, thirdPartyTileDisplayNameKeys)
        val recordingRuleModuleDisplayNameRegistry =
            RecordingRuleModuleDisplayNameRegistry(ruleModuleDisplayNameRegistry, thirdPartyRuleModuleDisplayNameKeys)
        val recordingTileEmojiRegistry = RecordingTileEmojiRegistry(tileEmojiRegistry, thirdPartyTileEmojiKeys)
        val recordingTileLabelRegistry = RecordingTileLabelRegistry(tileLabelRegistry, thirdPartyTileLabelKeys)
        val recordingShowcaseRegistry = RecordingWinCelebrationShowcaseRegistry(showcaseRegistry, thirdPartyShowcaseKeys)
        val recordingGameActionDisplayNameRegistry =
            RecordingGameActionDisplayNameRegistry(gameActionDisplayNameRegistry, thirdPartyGameActionIds)
        val recordingPortraitRegistry =
            RecordingPlayerPortraitSourceRegistry(playerPortraitSourceRegistry, thirdPartyPortraitProviderIds)

        val registeredExtensionIds = mutableSetOf<String>()
        extensions.forEach { extension ->
            if (!registeredExtensionIds.add(extension.id)) {
                throw MinecraftMahjongExtensionRegistrationException(
                    extension.id,
                    IllegalArgumentException("Duplicate Minecraft Mahjong extension id: ${extension.id}"),
                )
            }
            try {
                extension.registerTileAssets(recordingTileAssetRegistry)
                extension.registerAiStrategyDisplayNames(recordingAiStrategyDisplayNameRegistry)
                extension.registerTileDisplayNames(recordingTileDisplayNameRegistry)
                extension.registerRuleModuleDisplayNames(recordingRuleModuleDisplayNameRegistry)
                extension.registerTileEmojis(recordingTileEmojiRegistry)
                extension.registerTileLabels(recordingTileLabelRegistry)
                extension.registerWinCelebrationShowcases(recordingShowcaseRegistry)
                extension.registerGameActionDisplayNames(recordingGameActionDisplayNameRegistry)
                extension.registerExhaustiveDrawReasonDisplayNames(exhaustiveDrawReasonDisplayNameRegistry)
                extension.registerRoundPreparationDisplayNames(roundPreparationDisplayNameRegistry)
                extension.registerWinSettlementPresentationTemplates(winSettlementTemplateRegistry)
                extension.registerMatchSettlementPresentationTemplates(matchSettlementTemplateRegistry)
                extension.registerPlayerPortraitSources(recordingPortraitRegistry)
                extension.registerPublicPlayerIndicatorDisplays(publicPlayerIndicatorDisplayRegistry)
            } catch (cause: Exception) {
                throw MinecraftMahjongExtensionRegistrationException(extension.id, cause)
            }
        }

        tileAssetRegistry.freeze()
        aiStrategyDisplayNameRegistry.freeze()
        tileDisplayNameRegistry.freeze()
        ruleModuleDisplayNameRegistry.freeze()
        tileEmojiRegistry.freeze()
        tileLabelRegistry.freeze()
        showcaseRegistry.freeze()
        gameActionDisplayNameRegistry.freeze()
        exhaustiveDrawReasonDisplayNameRegistry.freeze()
        roundPreparationDisplayNameRegistry.freeze()
        winSettlementTemplateRegistry.freeze()
        matchSettlementTemplateRegistry.freeze()
        playerPortraitSourceRegistry.freeze()
        publicPlayerIndicatorDisplayRegistry.freeze()
        return MinecraftMahjongExtensionRegistrationResult(
            thirdPartyAssetKeys,
            thirdPartyAiStrategyKeys,
            thirdPartyTileDisplayNameKeys,
            thirdPartyRuleModuleDisplayNameKeys,
            thirdPartyTileEmojiKeys,
            thirdPartyTileLabelKeys,
            thirdPartyShowcaseKeys,
            thirdPartyGameActionIds,
            thirdPartyPortraitProviderIds,
        )
    }
}

/**
 * [MinecraftMahjongExtensionRegistrar.registerAndFreeze] 登記的第三方映射，供呼叫端記錄診斷資訊。
 *
 * @property thirdPartyTileAssetKeys 依 extension 順序登記的第三方 tile asset key，不含內建映射。
 * @property thirdPartyAiStrategyKeys 依 extension 順序登記的第三方 AI 策略顯示名稱 key，不含內建映射。
 * @property thirdPartyTileDisplayNameKeys 依 extension 順序登記的第三方牌種顯示名稱 key，不含內建映射。
 * @property thirdPartyRuleModuleDisplayNameKeys 依 extension 順序登記的第三方規則模組顯示名稱 key，
 *   不含內建映射。
 * @property thirdPartyTileEmojiKeys 依 extension 順序登記的第三方牌面 emoji asset key，不含內建映射。
 * @property thirdPartyTileLabelKeys 依 extension 順序登記的第三方牌面角落標籤 asset key，不含內建映射。
 * @property thirdPartyGameActionIds 依 extension 順序登記的第三方規則擴充動作 ID，不含內建映射。
 * @property thirdPartyPortraitProviderIds 依 extension 順序登記的第三方 portrait provider ID，不含內建 fallback。
 */
data class MinecraftMahjongExtensionRegistrationResult(
    val thirdPartyTileAssetKeys: List<String>,
    val thirdPartyAiStrategyKeys: List<String>,
    val thirdPartyTileDisplayNameKeys: List<String>,
    val thirdPartyRuleModuleDisplayNameKeys: List<String>,
    val thirdPartyTileEmojiKeys: List<String>,
    val thirdPartyTileLabelKeys: List<String>,
    val thirdPartyShowcaseKeys: List<String> = emptyList(),
    val thirdPartyGameActionIds: List<String> = emptyList(),
    val thirdPartyPortraitProviderIds: List<String> = emptyList(),
)

/** 轉發 portrait provider 註冊並記錄第三方 provider ID。 */
private class RecordingPlayerPortraitSourceRegistry(
    private val delegate: PlayerPortraitSourceRegistry,
    private val recorded: MutableList<String>,
) : PlayerPortraitSourceRegistry {
    override val isFrozen: Boolean get() = delegate.isFrozen
    override val providerIds: Set<String> get() = delegate.providerIds

    override fun register(providerId: String, priority: Int, provider: PlayerPortraitSourceProvider) {
        delegate.register(providerId, priority, provider)
        recorded += providerId
    }

    override fun resolve(context: PlayerPortraitSourceContext) = delegate.resolve(context)

    override fun freeze() = delegate.freeze()
}

/** 表示指定第三方 Minecraft extension 無法完成 registry 註冊。 */
class MinecraftMahjongExtensionRegistrationException(
    extensionId: String,
    cause: Throwable,
) : IllegalStateException("Failed to register Minecraft Mahjong extension: $extensionId", cause)

/** 轉發擴充動作顯示名稱註冊並記錄第三方動作 ID。 */
private class RecordingGameActionDisplayNameRegistry(
    private val delegate: GameActionDisplayNameRegistry,
    private val recorded: MutableList<String>,
) : GameActionDisplayNameRegistry {
    override val isFrozen: Boolean get() = delegate.isFrozen

    override fun register(actionId: String, translationKey: String) {
        delegate.register(actionId, translationKey)
        recorded += actionId
    }

    override fun find(action: ExtensionGameAction): String? = delegate.find(action)

    override fun freeze() = delegate.freeze()
}

/** 轉發至 [delegate]，並額外把第三方註冊的 asset key 記錄進 [recorded]，供診斷用途。 */
private class RecordingMinecraftTileAssetRegistry(
    private val delegate: MinecraftTileAssetRegistry,
    private val recorded: MutableList<String>,
) : MinecraftTileAssetRegistry {
    override val isFrozen: Boolean get() = delegate.isFrozen

    override fun register(typeId: TileTypeId, assetKey: String) {
        delegate.register(typeId, assetKey)
        recorded += assetKey
    }

    override fun freeze() = delegate.freeze()

    override fun find(typeId: TileTypeId): String? = delegate.find(typeId)
}

/** 轉發至 [delegate]，並額外把第三方註冊的策略 key 記錄進 [recorded]，供診斷用途。 */
private class RecordingAiStrategyDisplayNameRegistry(
    private val delegate: AiStrategyDisplayNameRegistry,
    private val recorded: MutableList<String>,
) : AiStrategyDisplayNameRegistry {
    override val isFrozen: Boolean get() = delegate.isFrozen

    override fun register(strategyKey: String, translationKey: String) {
        delegate.register(strategyKey, translationKey)
        recorded += strategyKey
    }

    override fun freeze() = delegate.freeze()

    override fun find(strategyKey: String): String? = delegate.find(strategyKey)
}

/** 轉發至 [delegate]，並額外把第三方註冊的牌種顯示名稱 key 記錄進 [recorded]，供診斷用途。 */
private class RecordingTileDisplayNameRegistry(
    private val delegate: TileDisplayNameRegistry,
    private val recorded: MutableList<String>,
) : TileDisplayNameRegistry {
    override val isFrozen: Boolean get() = delegate.isFrozen

    override fun register(typeId: TileTypeId, translationKey: String) {
        delegate.register(typeId, translationKey)
        recorded += typeId.toString()
    }

    override fun freeze() = delegate.freeze()

    override fun find(typeId: TileTypeId): String? = delegate.find(typeId)
}

/** 轉發至 [delegate]，並額外把第三方註冊的規則模組顯示名稱 key 記錄進 [recorded]，供診斷用途。 */
private class RecordingRuleModuleDisplayNameRegistry(
    private val delegate: RuleModuleDisplayNameRegistry,
    private val recorded: MutableList<String>,
) : RuleModuleDisplayNameRegistry {
    override val isFrozen: Boolean get() = delegate.isFrozen

    override fun register(ruleModuleId: String, translationKey: String) {
        delegate.register(ruleModuleId, translationKey)
        recorded += ruleModuleId
    }

    override fun freeze() = delegate.freeze()

    override fun find(ruleModuleId: String): String? = delegate.find(ruleModuleId)
}

/** 轉發至 [delegate]，並額外把第三方註冊的牌面 emoji asset key 記錄進 [recorded]，供診斷用途。 */
private class RecordingTileEmojiRegistry(
    private val delegate: TileEmojiRegistry,
    private val recorded: MutableList<String>,
) : TileEmojiRegistry {
    override val isFrozen: Boolean get() = delegate.isFrozen

    override fun register(assetKey: String, emoji: String) {
        delegate.register(assetKey, emoji)
        recorded += assetKey
    }

    override fun freeze() = delegate.freeze()

    override fun find(assetKey: String): String? = delegate.find(assetKey)
}

/** 轉發至 [delegate]，並額外把第三方註冊的牌面角落標籤 asset key 記錄進 [recorded]，供診斷用途。 */
private class RecordingTileLabelRegistry(
    private val delegate: TileLabelRegistry,
    private val recorded: MutableList<String>,
) : TileLabelRegistry {
    override val isFrozen: Boolean get() = delegate.isFrozen

    override fun register(assetKey: String, label: TileLabel) {
        delegate.register(assetKey, label)
        recorded += assetKey
    }

    override fun freeze() = delegate.freeze()

    override fun find(assetKey: String): TileLabel? = delegate.find(assetKey)
}

/** 轉發 showcase 註冊並記錄第三方 cue key。 */
private class RecordingWinCelebrationShowcaseRegistry(
    private val delegate: WinCelebrationShowcaseRegistry,
    private val recorded: MutableList<String>,
) : WinCelebrationShowcaseRegistry {
    override val isFrozen: Boolean get() = delegate.isFrozen
    override val cueKeys: Set<String> get() = delegate.cueKeys

    override fun register(definition: WinCelebrationShowcaseDefinition) {
        delegate.register(definition)
        recorded += definition.cueKey
    }

    override fun freeze() = delegate.freeze()

    override fun find(cueKey: String) = delegate.find(cueKey)
}
