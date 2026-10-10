package com.doublemoon1119.mahjongcraft.flow.network.dto.rule

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionGameCommand
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.DtoRegistry
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.config.GameLength
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.config.ScoreConfig
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.PlayerRuleState

/**
 * 領域層開放介面 ↔ DTO 的註冊表集合。建構時全部是空的，比照 [MahjongModuleRegistryImpl] 的既有精神——
 * 各規則（例如日麻）在自己的 extension 呼叫對應 registry 的 `register(...)` 把實作註冊進來，第三方規則
 * 模組要支援序列化也一樣，不需要修改這個檔案。
 */
interface NetworkDtoRegistries {
    /** 規則配置 DTO registry。 */
    val ruleConfig: DtoRegistry<MahjongRuleConfig, MahjongRuleConfigDto>

    /** 計分配置 DTO registry。 */
    val scoreConfig: DtoRegistry<ScoreConfig, ScoreConfigDto>

    /** 遊戲長度 DTO registry。 */
    val gameLength: DtoRegistry<GameLength, GameLengthDto>

    /** 動態牌桌狀態 DTO registry。 */
    val dynamicRuleState: DtoRegistry<DynamicRuleState, DynamicRuleStateDto>

    /** 玩家規則狀態 DTO registry。 */
    val playerRuleState: DtoRegistry<PlayerRuleState, PlayerRuleStateDto>

    /** 牌河 DTO registry。 */
    val discardPile: DtoRegistry<DiscardPile<*>, DiscardPileDto>

    /** 流局原因 DTO registry。 */
    val exhaustiveDrawReason: DtoRegistry<ExhaustiveDrawReason, ExhaustiveDrawReasonDto>

    /** 擴充遊戲動作 DTO registry。 */
    val extensionGameAction: DtoRegistry<ExtensionGameAction, ExtensionGameActionDto>

    /** 擴充遊戲命令 DTO registry。 */
    val extensionGameCommand: DtoRegistry<ExtensionGameCommand, ExtensionGameCommandDto>

    /** 凍結所有 registry；凍結後不得新增或覆寫 mapper。 */
    fun freeze()
}
