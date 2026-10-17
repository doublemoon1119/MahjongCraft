package com.doublemoon1119.mahjongcraft.platform.fabric.di

import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.AutomaticControlDisplayResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.AutomaticControlStatusHudRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.ClientAutoSortHandPreferenceSender
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.ClientAutoSortHandPreferenceService
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.ClientAutomaticControlCommandService
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.ClientAutomaticControlRequestSender
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.ClientAutomaticControlUpdateCoordinator
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.FabricAutomaticControlCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.FabricClientAutoSortHandPreferenceSender
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.FabricClientAutomaticControlRequestSender
import com.doublemoon1119.mahjongcraft.platform.fabric.client.catalogue.FabricRuleCatalogueScreenCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.client.catalogue.RuleCatalogueScreenController
import com.doublemoon1119.mahjongcraft.platform.fabric.client.concurrency.ClientCoroutineScope
import com.doublemoon1119.mahjongcraft.platform.fabric.client.concurrency.ClientThreadCoroutineDispatcher
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.FabricClientConfigCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigScreenController
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongHudPreviewRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.game.ClientDecisionPromptStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.game.DecisionTextResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.client.game.DecisionTimerDisplay
import com.doublemoon1119.mahjongcraft.platform.fabric.client.game.PlayerDecisionHudController
import com.doublemoon1119.mahjongcraft.platform.fabric.client.history.ClientHistoryArchiveStatusCoordinator
import com.doublemoon1119.mahjongcraft.platform.fabric.client.history.ClientHistoryChatEntryStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.history.ClientHistoryQueryCoordinator
import com.doublemoon1119.mahjongcraft.platform.fabric.client.history.ClientHistoryQuerySettings
import com.doublemoon1119.mahjongcraft.platform.fabric.client.history.FabricHistoryArchiveStatusSender
import com.doublemoon1119.mahjongcraft.platform.fabric.client.history.FabricHistoryQuerySender
import com.doublemoon1119.mahjongcraft.platform.fabric.client.history.FabricHistoryScreenCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.client.history.HistoryArchiveStatusSender
import com.doublemoon1119.mahjongcraft.platform.fabric.client.history.HistoryArchiveStatusTransport
import com.doublemoon1119.mahjongcraft.platform.fabric.client.history.HistoryParticipantPresentationResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.client.history.HistoryQuerySender
import com.doublemoon1119.mahjongcraft.platform.fabric.client.history.HistoryQueryTransport
import com.doublemoon1119.mahjongcraft.platform.fabric.client.history.HistoryScreenController
import com.doublemoon1119.mahjongcraft.platform.fabric.client.player.ClientPlayerDisplayNameResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.client.player.ClientPlayerProfileResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.ClientTileAppearanceResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MahjongTileFaceRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.PlayerPortraitRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.PublicPlayerIndicatorTextResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.client.room.FabricRoomConfigScreenCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientAutomaticControlStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.tile.ClientHandTileLayoutController
import com.doublemoon1119.mahjongcraft.platform.fabric.client.tile.FabricHandSortCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.client.tile.FabricTileLabelCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.client.tile.MatchingTileHighlightController
import com.doublemoon1119.mahjongcraft.platform.fabric.client.time.PauseAwareClientMonotonicClock
import com.doublemoon1119.mahjongcraft.platform.minecraft.di.flowClientModule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

/**
 * Fabric client 使用的 Koin 定義。
 *
 * 此 module 只由 Minecraft client graph 載入，包含 HUD、renderer 與其他 client-only adapter；dedicated server graph 不載入此 module。
 */
val fabricClientModule = module {
    includes(flowClientModule, fabricCommonModule)

    single<AutomaticControlDisplayResolver>()
    single<AutomaticControlStatusHudRenderer>()
    single<ClientAutoSortHandPreferenceService>()
    single<ClientAutomaticControlCommandService>()
    single {
        ClientAutomaticControlUpdateCoordinator(
            stateStore = get(),
            requestSender = get(),
            clock = get<PauseAwareClientMonotonicClock>(),
        )
    }
    single<FabricAutomaticControlCommand>()
    single<FabricClientAutoSortHandPreferenceSender>() bind ClientAutoSortHandPreferenceSender::class
    single<FabricClientAutomaticControlRequestSender>() bind ClientAutomaticControlRequestSender::class

    single<FabricRuleCatalogueScreenCommand>()
    single<RuleCatalogueScreenController>()

    single<ClientCoroutineScope>() bind CoroutineScope::class
    single<ClientThreadCoroutineDispatcher>() bind CoroutineDispatcher::class

    single<FabricClientConfigCommand>()
    single<MahjongClientConfigScreenController>()
    single<MahjongClientConfigStore>()
    single<MahjongHudPreviewRenderer>()

    single<ClientDecisionPromptStore>()
    single<DecisionTextResolver>()
    single<DecisionTimerDisplay>()
    single<PlayerDecisionHudController>()

    single<ClientHistoryArchiveStatusCoordinator>() bind HistoryArchiveStatusTransport::class
    single<ClientHistoryChatEntryStore>()
    single<ClientHistoryQueryCoordinator>() bind HistoryQueryTransport::class
    single<ClientHistoryQuerySettings>()
    single<FabricHistoryArchiveStatusSender>() bind HistoryArchiveStatusSender::class
    single<FabricHistoryQuerySender>() bind HistoryQuerySender::class
    single<FabricHistoryScreenCommand>()
    single<HistoryParticipantPresentationResolver>()
    single<HistoryScreenController>()

    single<ClientPlayerDisplayNameResolver>()
    single<ClientPlayerProfileResolver>()

    single<ClientTileAppearanceResolver>()
    single<MahjongTileFaceRenderer>()
    single<PlayerPortraitRenderer>()
    single<PublicPlayerIndicatorTextResolver>()

    single<FabricRoomConfigScreenCommand>()

    single<ClientAutomaticControlStateStore>()
    single<PauseAwareClientMonotonicClock>()
    single<ClientMahjongStateStore>()

    single<FabricHandSortCommand>()
    single<FabricTileLabelCommand>()
    single<ClientHandTileLayoutController>()
    single<MatchingTileHighlightController>()
}
