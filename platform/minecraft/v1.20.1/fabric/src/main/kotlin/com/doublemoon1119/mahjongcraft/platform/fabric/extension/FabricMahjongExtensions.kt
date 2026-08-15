package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.extension.MahjongExtensionRegistrar
import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInTileTypes
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.registerBuiltInRuleConfigDtos
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtensionRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtensionRegistrationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

/** Fabric Loader 用來發現 [MahjongExtension] 的 entrypoint key。 */
const val MAHJONG_EXTENSION_ENTRYPOINT: String = "${MinecraftModMetadata.MOD_ID}:extension"

/**
 * 發現並註冊 Fabric 環境中的第三方 [MahjongExtension]。
 *
 * 內建規則與 DTO 會先完成註冊，第三方 extension 隨後取得 runtime 實際使用的同一批 registry；
 * 全部成功後由 [MahjongExtensionRegistrar] 凍結 registry。
 */
object FabricMahjongExtensions {
    /** Fabric extension discovery 與註冊結果使用的 logger。 */
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    /** 透過 [FabricLoader] 發現 entrypoint，完成一次 runtime registry 初始化。 */
    fun initialize(
        moduleRegistry: MahjongModuleRegistry,
        tileTypeRegistry: TileTypeRegistry,
        networkRegistries: NetworkDtoRegistries,
        persistenceRegistries: PersistenceRegistries,
        minecraftTileAssetRegistry: MinecraftTileAssetRegistry,
        aiStrategyDisplayNameRegistry: AiStrategyDisplayNameRegistry,
        tileDisplayNameRegistry: TileDisplayNameRegistry,
        ruleModuleDisplayNameRegistry: RuleModuleDisplayNameRegistry,
    ) {
        try {
            val extensions = FabricLoader.getInstance()
                .getEntrypoints(MAHJONG_EXTENSION_ENTRYPOINT, MahjongExtension::class.java)
            val result = initialize(
                moduleRegistry,
                tileTypeRegistry,
                networkRegistries,
                persistenceRegistries,
                minecraftTileAssetRegistry,
                aiStrategyDisplayNameRegistry,
                tileDisplayNameRegistry,
                ruleModuleDisplayNameRegistry,
                extensions,
            )
            val extensionIds = extensions.map { it.id }
            logger.info(
                "Registered {} third-party Mahjong extension(s): {}",
                extensionIds.size,
                extensionIds,
            )
            logger.info(
                "Registered {} third-party Minecraft tile asset(s): {}",
                result.thirdPartyTileAssetKeys.size,
                result.thirdPartyTileAssetKeys,
            )
            logger.info(
                "Registered {} third-party AI strategy display name(s): {}",
                result.thirdPartyAiStrategyKeys.size,
                result.thirdPartyAiStrategyKeys,
            )
            logger.info(
                "Registered {} third-party tile display name(s): {}",
                result.thirdPartyTileDisplayNameKeys.size,
                result.thirdPartyTileDisplayNameKeys,
            )
            logger.info(
                "Registered {} third-party rule module display name(s): {}",
                result.thirdPartyRuleModuleDisplayNameKeys.size,
                result.thirdPartyRuleModuleDisplayNameKeys,
            )
        } catch (cause: Exception) {
            logger.error("Failed to initialize Mahjong extensions", cause)
            throw cause
        }
    }

    /**
     * 使用明確提供的 [extensions] 初始化，供平台測試驗證組裝順序。
     *
     * @return [MinecraftMahjongExtensionRegistrar.registerAndFreeze] 的登記結果，供呼叫端記錄診斷資訊。
     */
    internal fun initialize(
        moduleRegistry: MahjongModuleRegistry,
        tileTypeRegistry: TileTypeRegistry,
        networkRegistries: NetworkDtoRegistries,
        persistenceRegistries: PersistenceRegistries,
        minecraftTileAssetRegistry: MinecraftTileAssetRegistry,
        aiStrategyDisplayNameRegistry: AiStrategyDisplayNameRegistry,
        tileDisplayNameRegistry: TileDisplayNameRegistry,
        ruleModuleDisplayNameRegistry: RuleModuleDisplayNameRegistry,
        extensions: Iterable<MahjongExtension>,
    ): MinecraftMahjongExtensionRegistrationResult {
        moduleRegistry.registerBuiltInRuleModules()
        tileTypeRegistry.registerBuiltInTileTypes()
        networkRegistries.registerBuiltInRuleConfigDtos()

        MahjongExtensionRegistrar.registerAndFreeze(
            extensions = extensions,
            moduleRegistry = moduleRegistry,
            tileTypeRegistry = tileTypeRegistry,
            networkRegistries = networkRegistries,
            persistenceRegistries = persistenceRegistries,
        )

        // 同一個第三方類別可同時實作 MahjongExtension 與 MinecraftMahjongExtension，
        // 不需要在 fabric.mod.json 額外宣告第二個 entrypoint。
        return MinecraftMahjongExtensionRegistrar.registerAndFreeze(
            extensions = extensions.filterIsInstance<MinecraftMahjongExtension>(),
            tileAssetRegistry = minecraftTileAssetRegistry,
            aiStrategyDisplayNameRegistry = aiStrategyDisplayNameRegistry,
            tileDisplayNameRegistry = tileDisplayNameRegistry,
            ruleModuleDisplayNameRegistry = ruleModuleDisplayNameRegistry,
        )
    }
}
