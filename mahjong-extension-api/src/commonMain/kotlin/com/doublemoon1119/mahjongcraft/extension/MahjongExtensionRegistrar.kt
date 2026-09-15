package com.doublemoon1119.mahjongcraft.extension

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
        registries: CoreExtensionRegistries,
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
                extension.registerRuleModules(registries.moduleRegistry)
                extension.registerTileTypes(registries.tileTypeRegistry)
                extension.registerNetworkDtos(registries.networkRegistries)
                extension.registerPersistenceDtos(registries.persistenceRegistries)
                extension.registerWinCelebrationCueResolvers(registries.winCelebrationCueResolverRegistry)
                extension.registerGameActionAiHandlers(registries.gameActionAiRegistry)
                extension.registerGameActionCommandFactories(registries.gameActionCommandFactoryRegistry)
                extension.registerGameCommandHandlers(registries.gameCommandRegistry)
                extension.registerPostReactionRoundOutcomeResolvers(registries.postReactionRoundOutcomeResolverRegistry)
                extension.registerPostActionExhaustiveDrawResolvers(registries.postActionExhaustiveDrawResolverRegistry)
                extension.registerRoundPreparationResolvers(registries.roundPreparationResolverRegistry)
                extension.registerWinRoundContinuationResolvers(registries.winRoundContinuationResolverRegistry)
                extension.registerWinSettlementDetailResolvers(registries.winSettlementDetailResolverRegistry)
            } catch (cause: Exception) {
                throw MahjongExtensionRegistrationException(extension.id, cause)
            }
        }

        registries.freezeAll()
    }
}

/** 表示指定第三方 extension 無法完成 runtime registry 註冊。 */
class MahjongExtensionRegistrationException(
    extensionId: String,
    cause: Throwable,
) : IllegalStateException("Failed to register Mahjong extension: $extensionId", cause)
