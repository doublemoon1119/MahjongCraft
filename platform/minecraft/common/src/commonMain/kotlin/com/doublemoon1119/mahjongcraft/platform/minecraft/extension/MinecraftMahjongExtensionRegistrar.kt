package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.registerBuiltInAiStrategyDisplayNames
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.registerBuiltInRuleModuleDisplayNames
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.registerBuiltInWinCelebrationShowcases
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
 * [RuleModuleDisplayNameRegistry]／[TileEmojiRegistry]／[TileLabelRegistry]，完成後凍結六者。
 *
 * 版本與 loader 無關；loader adapter 只負責發現 extension 並呼叫此物件，不自行實作註冊順序或凍結
 * 時機。
 */
object MinecraftMahjongExtensionRegistrar {
    /**
     * 先註冊內建映射，再依 [extensions] 順序登記第三方映射，全部成功後凍結全部五個 registry。
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
    ): MinecraftMahjongExtensionRegistrationResult {
        tileAssetRegistry.registerBuiltInTileAssets()
        aiStrategyDisplayNameRegistry.registerBuiltInAiStrategyDisplayNames()
        tileDisplayNameRegistry.registerBuiltInTileDisplayNames()
        ruleModuleDisplayNameRegistry.registerBuiltInRuleModuleDisplayNames()
        tileEmojiRegistry.registerBuiltInTileEmojis()
        tileLabelRegistry.registerBuiltInTileLabels()
        showcaseRegistry.registerBuiltInWinCelebrationShowcases()

        val thirdPartyAssetKeys = mutableListOf<String>()
        val thirdPartyAiStrategyKeys = mutableListOf<String>()
        val thirdPartyTileDisplayNameKeys = mutableListOf<String>()
        val thirdPartyRuleModuleDisplayNameKeys = mutableListOf<String>()
        val thirdPartyTileEmojiKeys = mutableListOf<String>()
        val thirdPartyTileLabelKeys = mutableListOf<String>()
        val thirdPartyShowcaseKeys = mutableListOf<String>()
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
        return MinecraftMahjongExtensionRegistrationResult(
            thirdPartyAssetKeys,
            thirdPartyAiStrategyKeys,
            thirdPartyTileDisplayNameKeys,
            thirdPartyRuleModuleDisplayNameKeys,
            thirdPartyTileEmojiKeys,
            thirdPartyTileLabelKeys,
            thirdPartyShowcaseKeys,
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
 */
data class MinecraftMahjongExtensionRegistrationResult(
    val thirdPartyTileAssetKeys: List<String>,
    val thirdPartyAiStrategyKeys: List<String>,
    val thirdPartyTileDisplayNameKeys: List<String>,
    val thirdPartyRuleModuleDisplayNameKeys: List<String>,
    val thirdPartyTileEmojiKeys: List<String>,
    val thirdPartyTileLabelKeys: List<String>,
    val thirdPartyShowcaseKeys: List<String> = emptyList(),
)

/** 表示指定第三方 Minecraft extension 無法完成 registry 註冊。 */
class MinecraftMahjongExtensionRegistrationException(
    extensionId: String,
    cause: Throwable,
) : IllegalStateException("Failed to register Minecraft Mahjong extension: $extensionId", cause)

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

    override fun register(definition: com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseDefinition) {
        delegate.register(definition)
        recorded += definition.cueKey
    }

    override fun freeze() = delegate.freeze()

    override fun find(cueKey: String) = delegate.find(cueKey)
}
