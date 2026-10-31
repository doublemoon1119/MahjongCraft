package com.doublemoon1119.mahjongcraft.extension

import com.doublemoon1119.mahjongcraft.ai.api.registration.ExtensionGameActionAiRegistrar
import com.doublemoon1119.mahjongcraft.ai.api.registration.MahjongAiStrategyRegistrar
import com.doublemoon1119.mahjongcraft.ai.api.registration.OpponentModelRegistrar
import com.doublemoon1119.mahjongcraft.flow.api.registration.GameEventPublicMarkerResolverRegistrar
import com.doublemoon1119.mahjongcraft.flow.api.registration.WinCelebrationCueResolverRegistrar
import com.doublemoon1119.mahjongcraft.flow.network.dto.api.registration.NetworkDtoRegistrars
import com.doublemoon1119.mahjongcraft.flow.persistence.format.api.registration.PersistenceRegistrars
import com.doublemoon1119.mahjongcraft.flow.server.api.registration.ExtensionGameActionCommandFactoryRegistrar
import com.doublemoon1119.mahjongcraft.flow.server.api.registration.ExtensionGameCommandExecutorRegistrar
import com.doublemoon1119.mahjongcraft.flow.server.api.registration.PostActionExhaustiveDrawResolverRegistrar
import com.doublemoon1119.mahjongcraft.flow.server.api.registration.PostReactionRoundOutcomeResolverRegistrar
import com.doublemoon1119.mahjongcraft.flow.server.api.registration.RuleDecisionResolverRegistrar
import com.doublemoon1119.mahjongcraft.flow.server.api.registration.WinRoundContinuationResolverRegistrar
import com.doublemoon1119.mahjongcraft.flow.server.api.registration.WinSettlementDetailResolverRegistrar
import com.doublemoon1119.mahjongcraft.logic.api.registration.ComponentRegistrar
import com.doublemoon1119.mahjongcraft.logic.api.registration.MahjongModuleRegistrar
import com.doublemoon1119.mahjongcraft.logic.api.registration.TileTypeRegistrar
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule

/**
 * 第三方麻將規則在 MahjongCraft runtime 啟動前登記所有必要整合的共用契約。
 *
 * 此契約只涵蓋與遊戲平台無關的整合，例如：
 * - 規則配置與計算：[registerRuleModules]。
 * - 自訂牌種：[registerTileTypes]。
 * - AI 策略與對手模型：[registerAiStrategies]、[registerOpponentModels]。
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
    /** extension 的穩定識別字串，用於標示登記來源、檢查重複並診斷註冊錯誤。 */
    val id: String

    /**
     * 登記規則配置與 [MahjongRuleModule] factory。
     *
     * @param registry 用於登記規則模組與其 factory 的 registry。
     */
    fun registerRuleModules(registry: MahjongModuleRegistrar)

    /**
     * 登記第三方規則使用的擴充牌種。
     *
     * 預設不註冊任何牌，使只提供既有 Numeric／Honor 規則的 extension 不必加入空實作。
     *
     * @param registry 用於登記擴充牌種類型的 registry。
     */
    fun registerTileTypes(registry: TileTypeRegistrar) = Unit

    /**
     * 登記規則使用的型別組件，例如對局、玩家、副露、捨牌紀錄或牌張上的資料，連同生命週期與公開投影。
     *
     * 預設不登記任何組件。保存轉換另由 [registerPersistenceDtos] 登記。
     *
     * @param registry 組件定義的共用 registry。
     */
    fun registerComponents(registry: ComponentRegistrar) = Unit

    /**
     * 登記此規則模組的胡牌展示提示解析器。
     *
     * @param registry 用於登記胡牌展示提示解析器的 registry。
     */
    fun registerWinCelebrationCueResolvers(registry: WinCelebrationCueResolverRegistrar) = Unit

    /**
     * 登記所有第三方規則需要的 network DTO mapper。
     *
     * @param registries 提供各 network DTO 類型 mapper 登記入口的 registry 集合。
     */
    fun registerNetworkDtos(registries: NetworkDtoRegistrars)

    /**
     * 登記所有第三方規則需要的 persistence DTO mapper。
     *
     * @param registries 提供各 persistence DTO 類型 mapper 登記入口的 registry 集合。
     */
    fun registerPersistenceDtos(registries: PersistenceRegistrars)

    /**
     * 登記規則擴充動作供 AI 建立命令的 handler；handler 被呼叫時會拿到這一局的規則模組。
     *
     * @param registry 用於登記擴充動作 AI handler 的 registry。
     */
    fun registerGameActionAiHandlers(registry: ExtensionGameActionAiRegistrar) = Unit

    /**
     * 登記平台無關的 AI 策略；策略顯示名稱由各平台的呈現 extension 另行登記。
     *
     * @param registry 用於登記 AI 策略的 registry。
     */
    fun registerAiStrategies(registry: MahjongAiStrategyRegistrar) = Unit

    /**
     * 登記規則專屬的 AI 對手模型；沒有登記的規則，AI 改用規則中立的對手模型估計對手。
     *
     * @param registry 用於登記規則專屬對手模型的 registry。
     */
    fun registerOpponentModels(registry: OpponentModelRegistrar) = Unit

    /**
     * 登記需要額外選牌的規則擴充動作如何建立伺服器命令。
     *
     * @param registry 用於登記擴充動作命令 factory 的 registry。
     */
    fun registerGameActionCommandFactories(registry: ExtensionGameActionCommandFactoryRegistrar) = Unit

    /**
     * 登記規則擴充命令的 handler 建立方式；每個執行環境以自己的對局流程服務建立 handler。
     *
     * @param registry 用於登記擴充命令 executor factory 的 registry。
     */
    fun registerGameCommandHandlers(registry: ExtensionGameCommandExecutorRegistrar) = Unit

    /**
     * 登記最後捨牌反應結束後才判定的特殊 round outcome resolver。
     *
     * @param registry 用於登記反應階段結束後局結果 resolver 的 registry。
     */
    fun registerPostReactionRoundOutcomeResolvers(registry: PostReactionRoundOutcomeResolverRegistrar) = Unit

    /**
     * 登記由特定玩家動作主動觸發（而非最後捨牌反應結束後被動判定）的途中流局 resolver。
     *
     * @param registry 用於登記動作觸發途中流局 resolver 的 registry。
     */
    fun registerPostActionExhaustiveDrawResolvers(registry: PostActionExhaustiveDrawResolverRegistrar) = Unit

    /**
     * 登記發牌後、正常摸打前的規則選擇流程。
     *
     * @param registry 用於登記規則選擇 resolver 的 registry。
     */
    fun registerRuleDecisionResolvers(registry: RuleDecisionResolverRegistrar) = Unit

    /**
     * 登記胡牌即時結算完成後、判斷本局是否結束的 resolver。
     *
     * @param registry 用於登記胡牌後局況延續 resolver 的 registry。
     */
    fun registerWinRoundContinuationResolvers(registry: WinRoundContinuationResolverRegistrar) = Unit

    /**
     * 登記規則專屬胡牌詳情欄位的解析器；欄位只含語意資料，顯示方式由平台決定。
     *
     * @param registry 用於登記胡牌結算詳情解析器的 registry。
     */
    fun registerWinSettlementDetailResolvers(registry: WinSettlementDetailResolverRegistrar) = Unit

    /**
     * 登記規則在公開遊戲事件中提供的中立標記解析器。
     *
     * @param registry 用於登記公開事件標記解析器的 registry。
     */
    fun registerGameEventPublicMarkerResolvers(registry: GameEventPublicMarkerResolverRegistrar) = Unit
}
