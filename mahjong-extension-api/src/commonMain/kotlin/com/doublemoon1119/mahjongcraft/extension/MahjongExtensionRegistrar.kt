package com.doublemoon1119.mahjongcraft.extension

import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.service.createBuiltInWinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistry

/**
 * 將平台發現的第三方 extension 登記至 runtime 實際使用的 registry，完成後凍結所有 registry。
 */
object MahjongExtensionRegistrar {
    /**
     * 依 [extensions] 順序執行註冊，並在全部成功後凍結 registry。
     *
     * @throws MahjongExtensionRegistrationException 若任一 extension 註冊失敗。
     */
    fun registerAndFreeze(
        extensions: Iterable<MahjongExtension>,
        moduleRegistry: MahjongModuleRegistry,
        tileTypeRegistry: TileTypeRegistry,
        networkRegistries: NetworkDtoRegistries,
        persistenceRegistries: PersistenceRegistries,
        winCelebrationCueResolverRegistry: WinCelebrationCueResolverRegistry =
            com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistryImpl(),
        gameActionAiRegistry: ExtensionGameActionAiRegistry = ExtensionGameActionAiRegistry(),
        gameCommandRegistry: ExtensionGameCommandExecutorRegistry = ExtensionGameCommandExecutorRegistry(),
        postReactionRoundOutcomeResolverRegistry: PostReactionRoundOutcomeResolverRegistry =
            PostReactionRoundOutcomeResolverRegistry(),
        winSettlementDetailResolverRegistry: WinSettlementDetailResolverRegistry =
            createBuiltInWinSettlementDetailResolverRegistry(),
    ) {
        val registeredExtensionIds = mutableSetOf<String>()
        extensions.forEach { extension ->
            if (!registeredExtensionIds.add(extension.id)) {
                throw MahjongExtensionRegistrationException(
                    extension.id,
                    IllegalArgumentException("Duplicate Mahjong extension id: ${extension.id}"),
                )
            }
            try {
                extension.registerRuleModules(moduleRegistry)
                extension.registerTileTypes(tileTypeRegistry)
                extension.registerNetworkDtos(networkRegistries)
                extension.registerPersistenceDtos(persistenceRegistries)
                extension.registerWinCelebrationCueResolvers(winCelebrationCueResolverRegistry)
                extension.registerGameActionAiHandlers(gameActionAiRegistry)
                extension.registerGameCommandHandlers(gameCommandRegistry)
                extension.registerPostReactionRoundOutcomeResolvers(postReactionRoundOutcomeResolverRegistry)
                extension.registerWinSettlementDetailResolvers(winSettlementDetailResolverRegistry)
            } catch (cause: Exception) {
                throw MahjongExtensionRegistrationException(extension.id, cause)
            }
        }

        moduleRegistry.freeze()
        tileTypeRegistry.freeze()
        networkRegistries.freeze()
        persistenceRegistries.freeze()
        winCelebrationCueResolverRegistry.freeze()
        gameActionAiRegistry.freeze()
        gameCommandRegistry.freeze()
        postReactionRoundOutcomeResolverRegistry.freeze()
        winSettlementDetailResolverRegistry.freeze()
    }
}

/** 表示指定第三方 extension 無法完成 runtime registry 註冊。 */
class MahjongExtensionRegistrationException(
    extensionId: String,
    cause: Throwable,
) : IllegalStateException("Failed to register Mahjong extension: $extensionId", cause)
