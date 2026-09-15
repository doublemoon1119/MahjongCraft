package com.doublemoon1119.mahjongcraft.platform.fabric.di

import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.extension.CoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.di.FlowServerModule
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameActionCommandFactoryRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.di.MinecraftCommonModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.di.MinecraftServerModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

/**
 * Minecraft server 流程使用的 Fabric adapter 定義。
 *
 * Dedicated server 與 client 程序內的 integrated server 都需要此 module。它包含 [FlowServerModule]、
 * [MinecraftServerModule]、[FabricCommonModule] 與所有 server-side Fabric adapter，但不掃描
 * `platform.fabric.client`，因此不會把 HUD、renderer 或其他 Minecraft client-only 類別加入 server graph。
 *
 * [MinecraftCommonModule] 已經透過 [FabricCommonModule] 間接 include，這裡重複列出純粹是因為 Koin
 * compiler plugin 的 strictSafety 靜態依賴檢查目前不會展開超過一層的巢狀 `includes`（[MinecraftCommonModule]
 * 定義的 single 因此在巢狀兩層時被誤判成缺漏依賴）；Koin runtime 對同一個 module 被多路徑重複
 * include 本來就會去重，不會造成 [MinecraftTileAssetRegistry] 之類的 single 被註冊兩次。
 */
@Module(
    includes = [
        FlowServerModule::class,
        MinecraftServerModule::class,
        MinecraftCommonModule::class,
        FabricCommonModule::class,
    ],
)
@ComponentScan("com.doublemoon1119.mahjongcraft.platform.fabric.server")
class FabricServerModule {
    /** 將 Koin 管理的 core extension registry single 組成 bootstrap 專用集合。 */
    @Single
    fun provideCoreExtensionRegistries(
        @Provided moduleRegistry: MahjongModuleRegistry,
        @Provided tileTypeRegistry: TileTypeRegistry,
        @Provided networkRegistries: NetworkDtoRegistries,
        @Provided persistenceRegistries: PersistenceRegistries,
        @Provided gameActionAiRegistry: ExtensionGameActionAiRegistry,
        @Provided gameActionCommandFactoryRegistry: ExtensionGameActionCommandFactoryRegistry,
        @Provided gameCommandRegistry: ExtensionGameCommandExecutorRegistry,
        @Provided postReactionRoundOutcomeResolverRegistry: PostReactionRoundOutcomeResolverRegistry,
        @Provided postActionExhaustiveDrawResolverRegistry: PostActionExhaustiveDrawResolverRegistry,
        @Provided roundPreparationResolverRegistry: RoundPreparationResolverRegistry,
        @Provided winRoundContinuationResolverRegistry: WinRoundContinuationResolverRegistry,
        @Provided winSettlementDetailResolverRegistry: WinSettlementDetailResolverRegistry,
        @Provided winCelebrationCueResolverRegistry: WinCelebrationCueResolverRegistry,
    ): CoreExtensionRegistries = CoreExtensionRegistries(
        moduleRegistry = moduleRegistry,
        tileTypeRegistry = tileTypeRegistry,
        networkRegistries = networkRegistries,
        persistenceRegistries = persistenceRegistries,
        gameActionAiRegistry = gameActionAiRegistry,
        gameActionCommandFactoryRegistry = gameActionCommandFactoryRegistry,
        gameCommandRegistry = gameCommandRegistry,
        postReactionRoundOutcomeResolverRegistry = postReactionRoundOutcomeResolverRegistry,
        postActionExhaustiveDrawResolverRegistry = postActionExhaustiveDrawResolverRegistry,
        roundPreparationResolverRegistry = roundPreparationResolverRegistry,
        winRoundContinuationResolverRegistry = winRoundContinuationResolverRegistry,
        winSettlementDetailResolverRegistry = winSettlementDetailResolverRegistry,
        winCelebrationCueResolverRegistry = winCelebrationCueResolverRegistry,
    )
}
