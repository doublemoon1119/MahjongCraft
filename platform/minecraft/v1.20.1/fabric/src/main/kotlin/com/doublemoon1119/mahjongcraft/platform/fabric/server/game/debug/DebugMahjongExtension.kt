package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.decision.DebugRoundPreparationResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.registerDebugWinRoundContinuationResolvers
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario.registerDebugScriptedAiStrategies

/**
 * 只在開發環境登記的 debug 整合，讓 debug 指令與情境需要的流程能在遊戲內驗證。
 *
 * 內建規則沒有自己的開局準備流程時，補上 debug 用的開局準備；另外登記可切換的胡牌後本局繼續流程，以及
 * debug 情境對手使用的腳本 AI。正式環境不登記這個 extension。
 *
 * @property winRoundContinuationState debug 指令切換胡牌後本局繼續流程時共用的狀態。
 */
internal class DebugMahjongExtension(
    private val winRoundContinuationState: DebugWinRoundContinuationState,
) : MahjongExtension {
    override val id: String = MahjongCraftMetadata.id("debug")

    override fun registerRuleModules(registry: MahjongModuleRegistry) = Unit

    override fun registerNetworkDtos(registries: NetworkDtoRegistries) = Unit

    override fun registerPersistenceDtos(registries: PersistenceRegistries) = Unit

    override fun registerAiStrategies(registry: MahjongAiStrategyRegistry) {
        registry.registerDebugScriptedAiStrategies()
    }

    override fun registerRoundPreparationResolvers(registry: RoundPreparationResolverRegistry) {
        listOf(BuiltInRuleModuleIds.RIICHI, BuiltInRuleModuleIds.TAIWAN).forEach { ruleModuleId ->
            if (registry.find(ruleModuleId) == null) registry.register(DebugRoundPreparationResolver(ruleModuleId))
        }
    }

    override fun registerWinRoundContinuationResolvers(registry: WinRoundContinuationResolverRegistry) {
        registry.registerDebugWinRoundContinuationResolvers(state = winRoundContinuationState)
    }
}
