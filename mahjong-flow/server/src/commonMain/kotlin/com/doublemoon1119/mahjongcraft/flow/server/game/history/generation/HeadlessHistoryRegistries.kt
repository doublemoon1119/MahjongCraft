package com.doublemoon1119.mahjongcraft.flow.server.game.history.generation

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry

/**
 * [HeadlessFlowHistoryRuntime] 使用的規則整合，一律是執行環境已完成登記並凍結的 registry。
 *
 * @property moduleRegistry 規則模組。
 * @property aiStrategyRegistry AI 策略。
 * @property winCelebrationCueResolverRegistry 胡牌演出提示。
 * @property postActionExhaustiveDrawResolverRegistry 動作後的途中流局判定。
 * @property postReactionRoundOutcomeResolverRegistry 回應結束後的本局結果判定。
 * @property winRoundContinuationResolverRegistry 胡牌後本局是否繼續。
 * @property winSettlementDetailResolverRegistry 胡牌詳情。
 * @property gameCommandRegistry 擴充命令 handler。
 */
data class HeadlessHistoryRegistries(
    val moduleRegistry: MahjongModuleRegistry,
    val aiStrategyRegistry: MahjongAiStrategyRegistry,
    val winCelebrationCueResolverRegistry: WinCelebrationCueResolverRegistry,
    val postActionExhaustiveDrawResolverRegistry: PostActionExhaustiveDrawResolverRegistry,
    val postReactionRoundOutcomeResolverRegistry: PostReactionRoundOutcomeResolverRegistry,
    val winRoundContinuationResolverRegistry: WinRoundContinuationResolverRegistry,
    val winSettlementDetailResolverRegistry: WinSettlementDetailResolverRegistry,
    val gameCommandRegistry: ExtensionGameCommandExecutorRegistry,
)
