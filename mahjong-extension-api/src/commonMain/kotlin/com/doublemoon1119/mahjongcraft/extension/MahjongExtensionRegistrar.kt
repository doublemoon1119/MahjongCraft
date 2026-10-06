package com.doublemoon1119.mahjongcraft.extension

/**
 * 將 extension 登記至 runtime 實際使用的 registry，完成後凍結所有 registry。
 */
object MahjongExtensionRegistrar {
    /**
     * 依 [extensions] 順序登記，全部成功後凍結 registry。
     *
     * 呼叫前已存在的登記視為內建登記；之後每個 extension 新增的登記歸在該 extension 名下。
     *
     * @return 依來源分組的所有登記：內建登記在前，接著依 [extensions] 順序排列每個 extension。
     * @throws MahjongExtensionRegistrationException 若任一 extension 註冊失敗。
     */
    fun registerAndFreeze(
        extensions: Iterable<MahjongExtension>,
        registries: CoreExtensionRegistries,
    ): List<ExtensionRegistrationSource> {
        val registeredExtensionIds = mutableSetOf<String>()
        var snapshot = registries.registrationSnapshot()
        val sources = mutableListOf(ExtensionRegistrationSource(extensionId = null, categories = snapshot.toCategories()))
        extensions.forEach { extension ->
            register(extension, registries, registeredExtensionIds)
            val after = registries.registrationSnapshot()
            sources += ExtensionRegistrationSource(extensionId = extension.id, categories = snapshot.additionsSince(after))
            snapshot = after
        }

        registries.freezeAll()
        return sources
    }

    /** 檢查 [extension] 的 ID 尚未使用後，依固定順序呼叫它的所有登記回呼。 */
    private fun register(
        extension: MahjongExtension,
        registries: CoreExtensionRegistries,
        registeredExtensionIds: MutableSet<String>,
    ) {
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
            extension.registerHistoryReplayProjections(registries.historyReplayProjectionRegistry)
            extension.registerWinCelebrationCueResolvers(registries.winCelebrationCueResolverRegistry)
            extension.registerGameActionAiHandlers(registries.gameActionAiRegistry)
            extension.registerAiStrategies(registries.aiStrategyRegistry)
            extension.registerOpponentModels(registries.opponentModelRegistry)
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
}

/** 表示指定 extension 無法完成 runtime registry 註冊。 */
class MahjongExtensionRegistrationException(
    extensionId: String,
    cause: Throwable,
) : IllegalStateException("Failed to register Mahjong extension: $extensionId", cause)
