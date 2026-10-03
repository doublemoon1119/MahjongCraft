package com.doublemoon1119.mahjongcraft.platform.fabric.di

import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelRegistry
import com.doublemoon1119.mahjongcraft.extension.CoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.network.dto.di.NetworkDtoModule
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.di.PersistenceFormatModule
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.HistoryReplayProjectionRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
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
 * [MinecraftCommonModule]、[NetworkDtoModule] 與 [PersistenceFormatModule] 已經透過 [FabricCommonModule] 間接
 * include，這裡重複列出是因為 Koin compiler plugin 組裝 full-graph 時，不會展開本地 module（例如
 * [FabricCommonModule]）自己的 `includes`；只經由它引入的 single 會被判成缺漏依賴，連 `koin.get<T>()`
 * 呼叫處也一併報錯。Koin runtime 對同一個 module 被多路徑重複 include 本來就會去重，不會造成
 * [MinecraftTileAssetRegistry] 之類的 single 被註冊兩次。
 */
@Module(
    includes = [
        FlowServerModule::class,
        MinecraftServerModule::class,
        MinecraftCommonModule::class,
        NetworkDtoModule::class,
        PersistenceFormatModule::class,
        FabricCommonModule::class,
    ],
)
@ComponentScan("com.doublemoon1119.mahjongcraft.platform.fabric.server")
class FabricServerModule {
    /**
     * 將 Koin 管理的 core extension registry single 組成 bootstrap 專用集合。
     *
     * @param moduleRegistry 規則模組 registry。
     * @param tileTypeRegistry 擴充牌種 registry。
     * @param networkRegistries 網路資料轉換 registry 集合。
     * @param persistenceRegistries 持久化資料轉換 registry 集合。
     * @param historyReplayProjectionRegistry 歷史讀取投影 registry。
     * @param gameActionAiRegistry 擴充動作的 AI 決策 registry。
     * @param aiStrategyRegistry AI 策略 registry。
     * @param opponentModelRegistry 對手模型 registry。
     * @param gameActionCommandFactoryRegistry 動作至命令的轉換 registry。
     * @param gameCommandRegistry 擴充命令執行 registry。
     * @param postReactionRoundOutcomeResolverRegistry 回應完成後的局結果判定 registry。
     * @param postActionExhaustiveDrawResolverRegistry 動作完成後的流局判定 registry。
     * @param roundPreparationResolverRegistry 開局準備解析 registry。
     * @param winRoundContinuationResolverRegistry 和牌後續流程 registry。
     * @param winSettlementDetailResolverRegistry 和牌結算明細 registry。
     * @param winCelebrationCueResolverRegistry 和牌演出提示 registry。
     * @return 共用 registry 的具名集合，不建立第二份實例。
     */
    @Single
    fun provideCoreExtensionRegistries(
        @Provided moduleRegistry: MahjongModuleRegistry,
        @Provided tileTypeRegistry: TileTypeRegistry,
        @Provided networkRegistries: NetworkDtoRegistries,
        @Provided persistenceRegistries: PersistenceRegistries,
        @Provided historyReplayProjectionRegistry: HistoryReplayProjectionRegistry,
        @Provided gameActionAiRegistry: ExtensionGameActionAiRegistry,
        @Provided aiStrategyRegistry: MahjongAiStrategyRegistry,
        @Provided opponentModelRegistry: OpponentModelRegistry,
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
        historyReplayProjectionRegistry = historyReplayProjectionRegistry,
        gameActionAiRegistry = gameActionAiRegistry,
        aiStrategyRegistry = aiStrategyRegistry,
        opponentModelRegistry = opponentModelRegistry,
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
