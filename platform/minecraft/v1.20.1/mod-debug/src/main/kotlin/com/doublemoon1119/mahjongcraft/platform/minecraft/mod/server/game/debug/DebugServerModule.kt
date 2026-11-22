package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.di.modServerModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.animation.DebugAnimationCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.character.DebugSeatedParticipantCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.decision.DebugDecisionCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.history.DebugHistoryCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.history.HistoryGenerationController
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.presentation.DebugPresentationCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.progression.DebugProgressionCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.progression.DebugRoundCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.scenario.DebugGameScenarioLoader
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.scenario.DebugGameScenarioPresentationSynchronizer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.scenario.DebugGameScenarioRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.scenario.DebugGameScenarioValidator
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.scenario.DebugScenarioCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.stress.DebugStressCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.stress.StressTestController
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.stress.StressTestEnvironmentFactory
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.support.DebugPlayerTableScope
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.support.DebugPreviewEntityLifecycle
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.support.DebugTilePreviewSupport
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.text.DebugTextCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.history.HistoryGenerationRuntimeFactory
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.history.RegisteredHistoryGenerationRuntimeFactory
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.presentation.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.presentation.DebugWinShowcaseOverride
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.scenario.DebugGameScenarioPresentationPublisher
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.support.DebugVirtualTableLayoutFactory
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

/** debug 工具的伺服器服務；開發環境載入本體的 Koin，依賴本體 [modServerModule] 提供的服務。 */
val debugServerModule: Module = module {
    single<DebugSeatedParticipantCommand>()
    single<DebugCommand>()
    single<DebugAnimationCommand>()
    single<DebugDecisionCommand>()
    single<DebugHistoryCommand>()
    single<RegisteredHistoryGenerationRuntimeFactory>() bind HistoryGenerationRuntimeFactory::class
    single<HistoryGenerationController>()
    single<DebugWinRoundContinuationState>()
    single<DebugWinShowcaseOverride>()
    single<DebugPresentationCommand>()
    single<DebugProgressionCommand>()
    single<DebugRoundCommand>()
    single<DebugGameScenarioLoader>()
    single<DebugGameScenarioPresentationPublisher>()
    single<DebugGameScenarioPresentationSynchronizer>()
    single<DebugGameScenarioRegistry>()
    single<DebugGameScenarioValidator>()
    single<DebugScenarioCommand>()
    single<DebugStressCommand>()
    single<StressTestController>()
    single<StressTestEnvironmentFactory>()
    single<DebugPlayerTableScope>()
    single<DebugPreviewEntityLifecycle>()
    single<DebugTilePreviewSupport>()
    single<DebugVirtualTableLayoutFactory>()
    single<DebugTextCommand>()
}
