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
}
