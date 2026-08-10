package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.extension.MahjongExtensionRegistrar
import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.registerBuiltInRuleConfigDtos
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
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
        networkRegistries: NetworkDtoRegistries,
        persistenceRegistries: PersistenceRegistries,
    ) {
        try {
            val extensions = FabricLoader.getInstance()
                .getEntrypoints(MAHJONG_EXTENSION_ENTRYPOINT, MahjongExtension::class.java)
            initialize(moduleRegistry, networkRegistries, persistenceRegistries, extensions)
            logger.info("Registered {} Mahjong extension(s)", extensions.size)
        } catch (cause: Exception) {
            logger.error("Failed to initialize Mahjong extensions", cause)
            throw cause
        }
    }

    /** 使用明確提供的 [extensions] 初始化，供平台測試驗證組裝順序。 */
    internal fun initialize(
        moduleRegistry: MahjongModuleRegistry,
        networkRegistries: NetworkDtoRegistries,
        persistenceRegistries: PersistenceRegistries,
        extensions: Iterable<MahjongExtension>,
    ) {
        moduleRegistry.registerBuiltInRuleModules()
        networkRegistries.registerBuiltInRuleConfigDtos()

        MahjongExtensionRegistrar.registerAndFreeze(
            extensions = extensions,
            moduleRegistry = moduleRegistry,
            networkRegistries = networkRegistries,
            persistenceRegistries = persistenceRegistries,
        )
    }
}
