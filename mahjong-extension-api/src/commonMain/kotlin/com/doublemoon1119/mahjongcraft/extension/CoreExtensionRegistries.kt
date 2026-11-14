package com.doublemoon1119.mahjongcraft.extension

import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventPublicMarkerResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameActionCommandFactoryRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistry

/**
 * 集中保存平台無關 extension bootstrap 使用的強型別 registry。
 *
 * 一般遊戲服務仍應只依賴實際需要的單一 registry；這個集合只用於 extension 註冊與統一凍結邊界。
 *
 * @property moduleRegistry 規則模組 registry。
 * @property tileTypeRegistry 牌種類型 registry。
 * @property componentRegistry 組件、適用對象與衍生效果的登記。
 * @property networkRegistries 網路 DTO registry 集合。
 * @property persistenceRegistries 權威狀態 persistence registry 集合。
 * @property gameActionAiRegistry 擴充動作 AI handler registry。
 * @property aiStrategyRegistry AI 策略 registry。
 * @property opponentModelRegistry AI 對手模型 registry。
 * @property gameActionCommandFactoryRegistry 擴充動作命令 factory registry。
 * @property gameCommandRegistry 擴充遊戲命令 executor registry。
 * @property postReactionRoundOutcomeResolverRegistry 反應階段結束後的局結果 resolver registry。
 * @property postActionExhaustiveDrawResolverRegistry 動作完成後的途中流局 resolver registry。
 * @property ruleDecisionResolverRegistry 規則選擇 resolver registry。
 * @property winRoundContinuationResolverRegistry 胡牌後續流程 resolver registry。
 * @property winSettlementDetailResolverRegistry 胡牌結算詳情 resolver registry。
 * @property gameEventPublicMarkerResolverRegistry 公開遊戲事件標記 resolver registry。
 * @property winCelebrationCueResolverRegistry 胡牌呈現提示 resolver registry。
 */
class CoreExtensionRegistries(
    // 規則與牌種
    val moduleRegistry: MahjongModuleRegistry,
    val tileTypeRegistry: TileTypeRegistry,
    val componentRegistry: ComponentRegistry,
    // 資料交換與持久化
    val networkRegistries: NetworkDtoRegistries,
    val persistenceRegistries: PersistenceRegistries,
    // 胡牌呈現語意
    val winCelebrationCueResolverRegistry: WinCelebrationCueResolverRegistry,
    // 動作執行
    val gameActionAiRegistry: ExtensionGameActionAiRegistry,
    val aiStrategyRegistry: MahjongAiStrategyRegistry,
    val opponentModelRegistry: OpponentModelRegistry,
    val gameActionCommandFactoryRegistry: ExtensionGameActionCommandFactoryRegistry,
    val gameCommandRegistry: ExtensionGameCommandExecutorRegistry,
    // 對局流程判定
    val postReactionRoundOutcomeResolverRegistry: PostReactionRoundOutcomeResolverRegistry,
    val postActionExhaustiveDrawResolverRegistry: PostActionExhaustiveDrawResolverRegistry,
    val ruleDecisionResolverRegistry: RuleDecisionResolverRegistry,
    val winRoundContinuationResolverRegistry: WinRoundContinuationResolverRegistry,
    val winSettlementDetailResolverRegistry: WinSettlementDetailResolverRegistry,
    val gameEventPublicMarkerResolverRegistry: GameEventPublicMarkerResolverRegistry,
) {
    /** 取得目前所有 core extension registry 的不可變診斷快照。 */
    fun registrationSnapshot(): ExtensionRegistrationSnapshot = ExtensionRegistrationSnapshot(
        categories = listOf(
            snapshotCategory(CoreExtensionRegistrationCategoryIds.RULE_MODULE, "Rule Module", moduleRegistry.getAllModuleIds()),
            snapshotCategory(CoreExtensionRegistrationCategoryIds.TILE_TYPE, "Tile Type", tileTypeRegistry.getAll().map { it.id.toString() }),
            snapshotCategory(CoreExtensionRegistrationCategoryIds.COMPONENT, "Component", componentRegistry.getAllOwners().map { "owner:${it.id}" } + componentRegistry.getAll().map { "value:${it.id}" } + componentRegistry.effectIds().map { "effect:$it" } + componentRegistry.getAllViews().map { "view:${it.id}" } + componentRegistry.projectionIds().map { "projection:$it" } + componentRegistry.lifecycleIds().map { "lifecycle:$it" }),
            snapshotCategory(CoreExtensionRegistrationCategoryIds.NETWORK_DTO, "Network DTO", networkRegistrationKeys()),
            snapshotCategory(CoreExtensionRegistrationCategoryIds.PERSISTENCE_DTO, "Persistence DTO", persistenceRegistrationKeys()),
            snapshotCategory(
                id = CoreExtensionRegistrationCategoryIds.WIN_CELEBRATION_CUE_RESOLVER,
                displayName = "Win Celebration Cue Resolver",
                registrationKeys = winCelebrationCueResolverRegistry.registrationKeys,
            ),
            snapshotCategory(CoreExtensionRegistrationCategoryIds.GAME_ACTION_AI, "Game Action AI", gameActionAiRegistry.registrationKeys),
            snapshotCategory(CoreExtensionRegistrationCategoryIds.AI_STRATEGY, "AI Strategy", aiStrategyRegistry.getAllStrategyKeys()),
            snapshotCategory(CoreExtensionRegistrationCategoryIds.OPPONENT_MODEL, "Opponent Model", opponentModelRegistry.registrationKeys),
            snapshotCategory(
                id = CoreExtensionRegistrationCategoryIds.GAME_ACTION_COMMAND_FACTORY,
                displayName = "Game Action Command Factory",
                registrationKeys = gameActionCommandFactoryRegistry.registrationKeys,
            ),
            snapshotCategory(CoreExtensionRegistrationCategoryIds.GAME_COMMAND, "Game Command", gameCommandRegistry.registrationKeys),
            snapshotCategory(
                id = CoreExtensionRegistrationCategoryIds.POST_REACTION_ROUND_OUTCOME_RESOLVER,
                displayName = "Post-reaction Round Outcome Resolver",
                registrationKeys = postReactionRoundOutcomeResolverRegistry.registrationKeys,
            ),
            snapshotCategory(
                id = CoreExtensionRegistrationCategoryIds.POST_ACTION_EXHAUSTIVE_DRAW_RESOLVER,
                displayName = "Post-action Exhaustive Draw Resolver",
                registrationKeys = postActionExhaustiveDrawResolverRegistry.registrationKeys,
            ),
            snapshotCategory(
                id = CoreExtensionRegistrationCategoryIds.RULE_DECISION_RESOLVER,
                displayName = "Rule Decision Resolver",
                registrationKeys = ruleDecisionResolverRegistry.registrationKeys,
            ),
            snapshotCategory(
                id = CoreExtensionRegistrationCategoryIds.WIN_ROUND_CONTINUATION_RESOLVER,
                displayName = "Win Round Continuation Resolver",
                registrationKeys = winRoundContinuationResolverRegistry.registrationKeys,
            ),
            snapshotCategory(
                id = CoreExtensionRegistrationCategoryIds.WIN_SETTLEMENT_DETAIL_RESOLVER,
                displayName = "Win Settlement Detail Resolver",
                registrationKeys = winSettlementDetailResolverRegistry.registrationKeys,
            ),
            snapshotCategory(
                id = CoreExtensionRegistrationCategoryIds.GAME_EVENT_PUBLIC_MARKER_RESOLVER,
                displayName = "Game Event Public Marker Resolver",
                registrationKeys = gameEventPublicMarkerResolverRegistry.registrationKeys,
            ),
        ),
    )

    /** 依固定分類順序凍結集合內所有 registry。 */
    fun freezeAll() {
        networkRegistries.componentViews.viewTypes.forEach { type ->
            require(componentRegistry.getAllViews().any { it === type }) { "Component view definition is not registered: ${type.id}" }
        }
        persistenceRegistries.componentViews.viewTypes.forEach { type ->
            require(componentRegistry.getAllViews().any { it === type }) { "Saved component view definition is not registered: ${type.id}" }
        }
        persistenceRegistries.components.componentTypes.forEach { type ->
            require(componentRegistry.find(type.id) === type) { "Component persistence definition is not registered: ${type.id}" }
        }
        componentRegistry.freeze()
        moduleRegistry.freeze()
        tileTypeRegistry.freeze()
        networkRegistries.freeze()
        persistenceRegistries.freeze()
        winCelebrationCueResolverRegistry.freeze()
        gameActionAiRegistry.freeze()
        aiStrategyRegistry.freeze()
        opponentModelRegistry.freeze()
        gameActionCommandFactoryRegistry.freeze()
        gameCommandRegistry.freeze()
        postReactionRoundOutcomeResolverRegistry.freeze()
        postActionExhaustiveDrawResolverRegistry.freeze()
        ruleDecisionResolverRegistry.freeze()
        winRoundContinuationResolverRegistry.freeze()
        winSettlementDetailResolverRegistry.freeze()
        gameEventPublicMarkerResolverRegistry.freeze()
    }

    /** 彙整所有 network DTO 子 registry 的穩定 key。 */
    private fun networkRegistrationKeys(): Set<String> = buildSet {
        addPrefixed("component_view", networkRegistries.componentViews.registrationKeys)
        addPrefixed("rule_config", networkRegistries.ruleConfig.registrationKeys)
        addPrefixed("score_config", networkRegistries.scoreConfig.registrationKeys)
        addPrefixed("game_length", networkRegistries.gameLength.registrationKeys)
        addPrefixed("exhaustive_draw_reason", networkRegistries.exhaustiveDrawReason.registrationKeys)
        addPrefixed("extension_game_action", networkRegistries.extensionGameAction.registrationKeys)
        addPrefixed("extension_game_command", networkRegistries.extensionGameCommand.registrationKeys)
    }

    /** 彙整所有 persistence DTO 子 registry 的穩定 key。 */
    private fun persistenceRegistrationKeys(): Set<String> = buildSet {
        addPrefixed("rule_config", persistenceRegistries.ruleConfigs.registrationKeys)
        addPrefixed("discard_pile", persistenceRegistries.discardPiles.registrationKeys)
        addPrefixed("exhaustive_draw_reason", persistenceRegistries.exhaustiveDrawReasons.registrationKeys)
        addPrefixed("extension_game_action", persistenceRegistries.extensionGameActions.registrationKeys)
        addPrefixed("component", persistenceRegistries.components.registrationKeys)
        addPrefixed("component_view", persistenceRegistries.componentViews.registrationKeys)
    }
}

/**
 * 建立固定 ID 與顯示名稱的診斷快照類別。
 *
 * @param id 診斷分類的穩定識別字串。
 * @param displayName 診斷分類的顯示名稱。
 * @param registrationKeys 此分類目前登記的 key。
 */
private fun snapshotCategory(
    id: String,
    displayName: String,
    registrationKeys: Iterable<String>,
): ExtensionRegistrationSnapshotCategory = ExtensionRegistrationSnapshotCategory(id, displayName, registrationKeys.toSet())

/**
 * 將子 registry 名稱加入 key，避免不同 DTO registry 使用相同 serial name 時互相抵銷。
 *
 * @param prefix 子 registry 名稱前綴。
 * @param keys 要加入接收集合的登記 key。
 */
private fun MutableSet<String>.addPrefixed(prefix: String, keys: Iterable<String>) {
    keys.forEach { key -> add("$prefix:$key") }
}
