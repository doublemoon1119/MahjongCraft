package com.doublemoon1119.mahjongcraft.extension

import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.HistoryReplayProjectionRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameActionCommandFactoryRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistry

/**
 * 第三方麻將規則在 MahjongCraft runtime 啟動前登記所有必要整合的共用契約。
 *
 * 此契約只涵蓋與遊戲平台無關的整合，例如：
 * - 規則配置與計算：[registerRuleModules]。
 * - 自訂牌種：[registerTileTypes]。
 * - AI 策略：[registerAiStrategies]。
 * - 網路與存檔資料轉換：[registerNetworkDtos]、[registerPersistenceDtos]。
 *
 * 平台專屬整合（如 Minecraft 的貼圖 asset key、顯示名稱與演出效果）由該平台的 extension 介面負責。
 * Minecraft 使用 `com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtension`。
 * 這讓 `mahjong-extension-api` 不必依賴特定平台，同一份規則邏輯也能在不同平台重複使用。
 *
 * loader adapter 負責發現實作並交給 [MahjongExtensionRegistrar]；extension 不應自行取得 Koin 或依賴
 * MahjongCraft 的初始化順序。
 */
interface MahjongExtension {
    /** 第三方 extension 的穩定識別字串，用於診斷註冊錯誤。 */
    val id: String

    /** 登記規則配置與 [MahjongRuleModule] factory。 */
    fun registerRuleModules(registry: MahjongModuleRegistry)

    /**
     * 登記第三方規則使用的擴充牌種。
     *
     * 預設不註冊任何牌，使只提供既有 Numeric／Honor 規則的 extension 不必加入空實作。
     */
    fun registerTileTypes(registry: TileTypeRegistry) = Unit

    /** 登記此規則模組的胡牌展示提示解析器。 */
    fun registerWinCelebrationCueResolvers(registry: WinCelebrationCueResolverRegistry) = Unit

    /** 登記所有第三方規則需要的 network DTO mapper。 */
    fun registerNetworkDtos(registries: NetworkDtoRegistries)

    /** 登記所有第三方規則需要的 persistence DTO mapper。 */
    fun registerPersistenceDtos(registries: PersistenceRegistries)

    /** 登記對局歷史公開投影的必要轉換器與可選規則資訊。 */
    fun registerHistoryReplayProjections(registry: HistoryReplayProjectionRegistry) = Unit

    /** 登記規則擴充動作供 AI 建立命令的 handler。 */
    fun registerGameActionAiHandlers(registry: ExtensionGameActionAiRegistry) = Unit

    /** 登記平台無關的 AI 策略；策略顯示名稱由各平台的呈現 extension 另行登記。 */
    fun registerAiStrategies(registry: MahjongAiStrategyRegistry) = Unit

    /** 登記需要額外選牌的規則擴充動作如何建立伺服器命令。 */
    fun registerGameActionCommandFactories(registry: ExtensionGameActionCommandFactoryRegistry) = Unit

    /** 登記規則擴充命令的伺服器執行 handler。 */
    fun registerGameCommandHandlers(registry: ExtensionGameCommandExecutorRegistry) = Unit

    /** 登記最後捨牌反應結束後才判定的特殊 round outcome resolver。 */
    fun registerPostReactionRoundOutcomeResolvers(registry: PostReactionRoundOutcomeResolverRegistry) = Unit

    /** 登記由特定玩家動作主動觸發（而非最後捨牌反應結束後被動判定）的途中流局 resolver。 */
    fun registerPostActionExhaustiveDrawResolvers(registry: PostActionExhaustiveDrawResolverRegistry) = Unit

    /** 登記發牌後、正常摸打前的規則準備流程。 */
    fun registerRoundPreparationResolvers(registry: RoundPreparationResolverRegistry) = Unit

    /** 登記胡牌即時結算完成後、判斷本局是否結束的 resolver。 */
    fun registerWinRoundContinuationResolvers(registry: WinRoundContinuationResolverRegistry) = Unit

    /** 登記規則專屬胡牌詳情欄位的解析器；欄位只含語意資料，顯示方式由平台決定。 */
    fun registerWinSettlementDetailResolvers(registry: WinSettlementDetailResolverRegistry) = Unit
}
