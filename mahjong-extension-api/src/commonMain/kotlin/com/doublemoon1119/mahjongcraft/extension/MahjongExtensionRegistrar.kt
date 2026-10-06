package com.doublemoon1119.mahjongcraft.extension

/**
 * 將平台發現的第三方 extension 登記至 runtime 實際使用的 registry，完成後凍結所有 registry。
 */
object MahjongExtensionRegistrar {
    /**
     * 先依序登記 [builtInExtensions]，再依 [extensions] 順序登記第三方 extension，全部成功後凍結 registry。
     *
     * [builtInExtensions] 是隨 MahjongCraft 一起發布的 extension，與第三方走同一組回呼，但它們的登記不列入回傳的
     * 第三方登記結果。兩者共用 extension ID 的唯一性檢查。
     *
     * @return 第三方 extension 新增的登記類別。
     * @throws MahjongExtensionRegistrationException 若任一 extension 註冊失敗。
     */
    fun registerAndFreeze(
        extensions: Iterable<MahjongExtension>,
        registries: CoreExtensionRegistries,
        builtInExtensions: Iterable<MahjongExtension> = emptyList(),
    ): List<ExtensionRegistrationCategory> {
        val registeredExtensionIds = mutableSetOf<String>()
        builtInExtensions.forEach { extension -> register(extension, registries, registeredExtensionIds) }
        val baseline = registries.registrationSnapshot()
        extensions.forEach { extension -> register(extension, registries, registeredExtensionIds) }

        val registrations = baseline.additionsSince(registries.registrationSnapshot())
        registries.freezeAll()
        return registrations
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
