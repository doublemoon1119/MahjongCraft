package com.doublemoon1119.mahjongcraft.platform.fabric.di

import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.AutomaticControlCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.AutomaticControlStatusHudRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.ClientAutoSortHandPreferencePacketSender
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.ClientAutoSortHandPreferenceService
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.ClientAutomaticControlCommandService
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.ClientAutomaticControlRequestPacketSender
import com.doublemoon1119.mahjongcraft.platform.fabric.client.catalogue.RuleCatalogueScreenCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.client.catalogue.RuleCatalogueScreenController
import com.doublemoon1119.mahjongcraft.platform.fabric.client.character.ClientAiCharacterPreviewRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.client.character.ClientAiCharacterPreviews
import com.doublemoon1119.mahjongcraft.platform.fabric.client.character.NativeCharacterPreviewRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.concurrency.ClientCoroutineScope
import com.doublemoon1119.mahjongcraft.platform.fabric.client.concurrency.ClientThreadCoroutineDispatcher
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.ClientConfigCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigScreenController
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongHudPreviewRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.game.PlayerDecisionHudController
import com.doublemoon1119.mahjongcraft.platform.fabric.client.guide.GuideScreenCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.client.guide.GuideScreenController
import com.doublemoon1119.mahjongcraft.platform.fabric.client.handbook.HandbookScreenCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.client.handbook.MahjongHandbookScreenController
import com.doublemoon1119.mahjongcraft.platform.fabric.client.history.ClientHistoryChatEntryStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.history.HistoryArchiveStatusPacketSender
import com.doublemoon1119.mahjongcraft.platform.fabric.client.history.HistoryParticipantPresentationResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.client.history.HistoryQueryPacketSender
import com.doublemoon1119.mahjongcraft.platform.fabric.client.history.HistoryScreenCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.client.history.HistoryScreenController
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MahjongTileFaceRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.PlayerPortraitRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.room.RoomConfigScreenCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.client.table.TableSpaceObstructionRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.tableprop.ClientTablePropPreviewRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.client.tableprop.ClientTablePropPreviews
import com.doublemoon1119.mahjongcraft.platform.fabric.client.tile.ClientDiscardSettlementTracker
import com.doublemoon1119.mahjongcraft.platform.fabric.client.tile.ClientHandTileLayoutController
import com.doublemoon1119.mahjongcraft.platform.fabric.client.tile.DiscardSourceHudRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.tile.HandSortCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.client.tile.LatestDiscardHighlightController
import com.doublemoon1119.mahjongcraft.platform.fabric.client.tile.MatchingTileHighlightController
import com.doublemoon1119.mahjongcraft.platform.fabric.client.tile.TileLabelCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.client.time.PauseAwareClientMonotonicClock
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterClientStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.di.flowClientModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.automatic.AutomaticControlDisplayResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.automatic.ClientAutoSortHandPreferenceSender
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.automatic.ClientAutomaticControlRequestSender
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.automatic.ClientAutomaticControlUpdateCoordinator
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.game.ClientDecisionPromptStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.game.DecisionTextResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.game.DecisionTimerDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.ClientHistoryArchiveStatusCoordinator
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.ClientHistoryQueryCoordinator
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.ClientHistoryQuerySettings
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryArchiveStatusSender
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryArchiveStatusTransport
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryQuerySender
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryQueryTransport
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.player.ClientPlayerDisplayNameResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.player.ClientPlayerProfileResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.ClientTileAppearanceResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.PublicPlayerIndicatorTextResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.state.ClientAutomaticControlStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.table.ClientTableSpaceObstructionStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.LoaderPlatform
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

/**
 * Minecraft client 使用的 Koin 定義，與載入器無關。
 *
 * 此 module 只由 Minecraft client graph 載入，包含 HUD、renderer 與其他 client-only adapter；dedicated server graph 不載入此 module。
 */
val modClientModule = module {
    includes(flowClientModule, modCommonModule)

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
    single<AutomaticControlCommand>()
    single<ClientAutoSortHandPreferencePacketSender>() bind ClientAutoSortHandPreferenceSender::class
    single<ClientAutomaticControlRequestPacketSender>() bind ClientAutomaticControlRequestSender::class

    single<RuleCatalogueScreenCommand>()
    single<RuleCatalogueScreenController>()

    single<ClientCoroutineScope>() bind CoroutineScope::class
    single<ClientThreadCoroutineDispatcher>() bind CoroutineDispatcher::class

    single<ClientConfigCommand>()
    single<MahjongClientConfigScreenController>()
    single<HandbookScreenCommand>()
    single<MahjongHandbookScreenController>()
    single<GuideScreenCommand>()
    single<GuideScreenController>()
    single { MahjongClientConfigStore(get<LoaderPlatform>()) }
    single<MahjongHudPreviewRenderer>()

    single<ClientDecisionPromptStore>()
    single<DecisionTextResolver>()
    single<DecisionTimerDisplay>()
    single<PlayerDecisionHudController>()

    single<ClientHistoryArchiveStatusCoordinator>() bind HistoryArchiveStatusTransport::class
    single<ClientHistoryChatEntryStore>()
    single<ClientHistoryQueryCoordinator>() bind HistoryQueryTransport::class
    single<ClientHistoryQuerySettings>()
    single<HistoryArchiveStatusPacketSender>() bind HistoryArchiveStatusSender::class
    single<HistoryQueryPacketSender>() bind HistoryQuerySender::class
    single<HistoryScreenCommand>()
    single<HistoryParticipantPresentationResolver>()
    single<HistoryScreenController>()

    single<ClientPlayerDisplayNameResolver>()
    single<ClientPlayerProfileResolver>()

    single<ClientTileAppearanceResolver>()
    single<MahjongTileFaceRenderer>()
    single<ClientAiCharacterPreviewRegistry>()
    single<NativeCharacterPreviewRenderer>()
    single<ClientAiCharacterPreviews>()
    single<ClientTablePropPreviewRegistry>()
    single<ClientTablePropPreviews>()
    single<PlayerPortraitRenderer>()
    single<PublicPlayerIndicatorTextResolver>()

    single<RoomConfigScreenCommand>()

    single<ClientAutomaticControlStateStore>()
    single<PauseAwareClientMonotonicClock>()
    single<AiCharacterClientStateStore>()
    single<ClientMahjongStateStore>()
    single<ClientTableSpaceObstructionStore>()
    single<TableSpaceObstructionRenderer>()

    single<HandSortCommand>()
    single<TileLabelCommand>()
    single<ClientHandTileLayoutController>()
    single<MatchingTileHighlightController>()
    single<ClientDiscardSettlementTracker>()
    single<LatestDiscardHighlightController>()
    single<DiscardSourceHudRenderer>()
}
