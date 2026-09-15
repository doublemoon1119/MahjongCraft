package com.doublemoon1119.mahjongcraft.extension

import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameActionCommandFactoryRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistry

/**
 * 集中保存平台無關 extension bootstrap 使用的強型別 registry。
 *
 * 一般遊戲服務仍應只依賴實際需要的單一 registry；這個集合只用於 extension 註冊與統一凍結邊界。
 *
 * @property moduleRegistry 規則模組 registry。
 * @property tileTypeRegistry 牌種類型 registry。
 * @property networkRegistries 網路 DTO registry 集合。
 * @property persistenceRegistries 權威狀態 persistence registry 集合。
 * @property gameActionAiRegistry 擴充動作 AI handler registry。
 * @property gameActionCommandFactoryRegistry 擴充動作命令 factory registry。
 * @property gameCommandRegistry 擴充遊戲命令 executor registry。
 * @property postReactionRoundOutcomeResolverRegistry 反應階段結束後的局結果 resolver registry。
 * @property postActionExhaustiveDrawResolverRegistry 動作完成後的途中流局 resolver registry。
 * @property roundPreparationResolverRegistry 開局準備 resolver registry。
 * @property winRoundContinuationResolverRegistry 胡牌後續流程 resolver registry。
 * @property winSettlementDetailResolverRegistry 胡牌結算詳情 resolver registry。
 * @property winCelebrationCueResolverRegistry 胡牌呈現提示 resolver registry。
 */
class CoreExtensionRegistries(
    // 規則與牌種
    val moduleRegistry: MahjongModuleRegistry,
    val tileTypeRegistry: TileTypeRegistry,
    // 資料交換與持久化
    val networkRegistries: NetworkDtoRegistries,
    val persistenceRegistries: PersistenceRegistries,
    // 胡牌呈現語意
    val winCelebrationCueResolverRegistry: WinCelebrationCueResolverRegistry,
    // 動作執行
    val gameActionAiRegistry: ExtensionGameActionAiRegistry,
    val gameActionCommandFactoryRegistry: ExtensionGameActionCommandFactoryRegistry,
    val gameCommandRegistry: ExtensionGameCommandExecutorRegistry,
    // 對局流程判定
    val postReactionRoundOutcomeResolverRegistry: PostReactionRoundOutcomeResolverRegistry,
    val postActionExhaustiveDrawResolverRegistry: PostActionExhaustiveDrawResolverRegistry,
    val roundPreparationResolverRegistry: RoundPreparationResolverRegistry,
    val winRoundContinuationResolverRegistry: WinRoundContinuationResolverRegistry,
    val winSettlementDetailResolverRegistry: WinSettlementDetailResolverRegistry,
) {
    /** 取得目前所有 core extension registry 的不可變診斷快照。 */
    fun registrationSnapshot(): ExtensionRegistrationSnapshot = ExtensionRegistrationSnapshot(
        listOf(
            snapshotCategory("mahjongcraft:rule_module", "Rule Module", moduleRegistry.getAllModuleIds()),
            snapshotCategory("mahjongcraft:tile_type", "Tile Type", tileTypeRegistry.getAll().map { it.id.toString() }),
            snapshotCategory("mahjongcraft:network_dto", "Network DTO", networkRegistrationKeys()),
            snapshotCategory("mahjongcraft:persistence_dto", "Persistence DTO", persistenceRegistrationKeys()),
            snapshotCategory(
                "mahjongcraft:win_celebration_cue_resolver",
                "Win Celebration Cue Resolver",
                winCelebrationCueResolverRegistry.registrationKeys,
            ),
            snapshotCategory("mahjongcraft:game_action_ai", "Game Action AI", gameActionAiRegistry.registrationKeys),
            snapshotCategory(
                "mahjongcraft:game_action_command_factory",
                "Game Action Command Factory",
                gameActionCommandFactoryRegistry.registrationKeys,
            ),
            snapshotCategory("mahjongcraft:game_command", "Game Command", gameCommandRegistry.registrationKeys),
            snapshotCategory(
                "mahjongcraft:post_reaction_round_outcome_resolver",
                "Post-reaction Round Outcome Resolver",
                postReactionRoundOutcomeResolverRegistry.registrationKeys,
            ),
            snapshotCategory(
                "mahjongcraft:post_action_exhaustive_draw_resolver",
                "Post-action Exhaustive Draw Resolver",
                postActionExhaustiveDrawResolverRegistry.registrationKeys,
            ),
            snapshotCategory(
                "mahjongcraft:round_preparation_resolver",
                "Round Preparation Resolver",
                roundPreparationResolverRegistry.registrationKeys,
            ),
            snapshotCategory(
                "mahjongcraft:win_round_continuation_resolver",
                "Win Round Continuation Resolver",
                winRoundContinuationResolverRegistry.registrationKeys,
            ),
            snapshotCategory(
                "mahjongcraft:win_settlement_detail_resolver",
                "Win Settlement Detail Resolver",
                winSettlementDetailResolverRegistry.registrationKeys,
            ),
        ),
    )

    /** 依固定分類順序凍結集合內所有 registry。 */
    fun freezeAll() {
        moduleRegistry.freeze()
        tileTypeRegistry.freeze()
        networkRegistries.freeze()
        persistenceRegistries.freeze()
        winCelebrationCueResolverRegistry.freeze()
        gameActionAiRegistry.freeze()
        gameActionCommandFactoryRegistry.freeze()
        gameCommandRegistry.freeze()
        postReactionRoundOutcomeResolverRegistry.freeze()
        postActionExhaustiveDrawResolverRegistry.freeze()
        roundPreparationResolverRegistry.freeze()
        winRoundContinuationResolverRegistry.freeze()
        winSettlementDetailResolverRegistry.freeze()
    }

    /** 彙整所有 network DTO 子 registry 的穩定 key。 */
    private fun networkRegistrationKeys(): Set<String> = buildSet {
        addPrefixed("rule_config", networkRegistries.ruleConfig.registrationKeys)
        addPrefixed("score_config", networkRegistries.scoreConfig.registrationKeys)
        addPrefixed("game_length", networkRegistries.gameLength.registrationKeys)
        addPrefixed("dynamic_rule_state", networkRegistries.dynamicRuleState.registrationKeys)
        addPrefixed("player_rule_state", networkRegistries.playerRuleState.registrationKeys)
        addPrefixed("discard_pile", networkRegistries.discardPile.registrationKeys)
        addPrefixed("exhaustive_draw_reason", networkRegistries.exhaustiveDrawReason.registrationKeys)
        addPrefixed("extension_game_action", networkRegistries.extensionGameAction.registrationKeys)
        addPrefixed("extension_game_command", networkRegistries.extensionGameCommand.registrationKeys)
    }

    /** 彙整所有 persistence DTO 子 registry 的穩定 key。 */
    private fun persistenceRegistrationKeys(): Set<String> = buildSet {
        addPrefixed("rule_config", persistenceRegistries.ruleConfigs.registrationKeys)
        addPrefixed("discard_pile", persistenceRegistries.discardPiles.registrationKeys)
        addPrefixed("player_rule_state", persistenceRegistries.playerRuleStates.registrationKeys)
        addPrefixed("dynamic_rule_state", persistenceRegistries.dynamicRuleStates.registrationKeys)
        addPrefixed("exhaustive_draw_reason", persistenceRegistries.exhaustiveDrawReasons.registrationKeys)
        addPrefixed("extension_game_action", persistenceRegistries.extensionGameActions.registrationKeys)
    }
}

/** 建立固定 ID 與顯示名稱的診斷快照類別。 */
private fun snapshotCategory(
    id: String,
    displayName: String,
    registrationKeys: Iterable<String>,
): ExtensionRegistrationSnapshotCategory = ExtensionRegistrationSnapshotCategory(id, displayName, registrationKeys.toSet())

/** 將子 registry 名稱加入 key，避免不同 DTO registry 使用相同 serial name 時互相抵銷。 */
private fun MutableSet<String>.addPrefixed(prefix: String, keys: Iterable<String>) {
    keys.forEach { key -> add("$prefix:$key") }
}
