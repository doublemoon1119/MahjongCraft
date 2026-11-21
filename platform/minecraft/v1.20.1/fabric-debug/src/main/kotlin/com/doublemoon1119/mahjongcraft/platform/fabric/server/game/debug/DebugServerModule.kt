package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug

import com.doublemoon1119.mahjongcraft.platform.fabric.di.fabricServerModule
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.animation.FabricDebugAnimationCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.character.FabricDebugSeatedParticipantCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.decision.FabricDebugDecisionCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.history.FabricDebugHistoryCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.history.FabricHistoryGenerationRuntimeFactory
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.history.HistoryGenerationController
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.history.HistoryGenerationRuntimeFactory
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinShowcaseOverride
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.FabricDebugPresentationCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.progression.FabricDebugProgressionCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.progression.FabricDebugRoundCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario.DebugGameScenarioLoader
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario.DebugGameScenarioPresentationPublisher
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario.DebugGameScenarioPresentationSynchronizer
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario.DebugGameScenarioRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario.DebugGameScenarioValidator
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario.FabricDebugScenarioCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress.FabricDebugStressCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress.StressTestController
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress.StressTestEnvironmentFactory
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugPlayerTableScope
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugPreviewEntityLifecycle
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugTilePreviewSupport
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugVirtualTableLayoutFactory
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.text.FabricDebugTextCommand
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

/** debug 工具的伺服器服務；開發環境載入本體的 Koin，依賴本體 [fabricServerModule] 提供的服務。 */
val debugServerModule: Module = module {
    single<FabricDebugSeatedParticipantCommand>()
    single<FabricDebugCommand>()
    single<FabricDebugAnimationCommand>()
    single<FabricDebugDecisionCommand>()
    single<FabricDebugHistoryCommand>()
    single<FabricHistoryGenerationRuntimeFactory>() bind HistoryGenerationRuntimeFactory::class
    single<HistoryGenerationController>()
    single<DebugWinRoundContinuationState>()
    single<DebugWinShowcaseOverride>()
    single<FabricDebugPresentationCommand>()
    single<FabricDebugProgressionCommand>()
    single<FabricDebugRoundCommand>()
    single<DebugGameScenarioLoader>()
    single<DebugGameScenarioPresentationPublisher>()
    single<DebugGameScenarioPresentationSynchronizer>()
    single<DebugGameScenarioRegistry>()
    single<DebugGameScenarioValidator>()
    single<FabricDebugScenarioCommand>()
    single<FabricDebugStressCommand>()
    single<StressTestController>()
    single<StressTestEnvironmentFactory>()
    single<DebugPlayerTableScope>()
    single<DebugPreviewEntityLifecycle>()
    single<DebugTilePreviewSupport>()
    single<DebugVirtualTableLayoutFactory>()
    single<FabricDebugTextCommand>()
}
