package com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry

import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.PersistenceDtoRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule.buildDiscardPilePersistenceRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule.buildDynamicRuleStatePersistenceRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule.buildExhaustiveDrawReasonPersistenceRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule.buildPlayerRuleStatePersistenceRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule.buildRuleConfigPersistenceRegistry
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.PlayerRuleState

/**
 * 權威狀態 persistence codec 使用的所有可擴充 DTO registry。
 *
 * @property ruleConfigs 規則配置 registry。
 * @property discardPiles 牌河 registry。
 * @property playerRuleStates 玩家規則狀態 registry。
 * @property dynamicRuleStates 動態牌桌狀態 registry。
 * @property exhaustiveDrawReasons 流局原因 registry。
 */
data class PersistenceRegistries(
    val ruleConfigs: PersistenceDtoRegistry<MahjongRuleConfig>,
    val discardPiles: PersistenceDtoRegistry<DiscardPile<*>>,
    val playerRuleStates: PersistenceDtoRegistry<PlayerRuleState>,
    val dynamicRuleStates: PersistenceDtoRegistry<DynamicRuleState>,
    val exhaustiveDrawReasons: PersistenceDtoRegistry<ExhaustiveDrawReason>,
) {
    /** 凍結所有 registry；凍結後不得新增 persistence mapper。 */
    fun freeze() {
        ruleConfigs.freeze()
        discardPiles.freeze()
        playerRuleStates.freeze()
        dynamicRuleStates.freeze()
        exhaustiveDrawReasons.freeze()
    }
}

/** 建立已登記所有內建日麻與台麻 mapper 的 persistence registries。 */
fun buildBuiltInPersistenceRegistries(): PersistenceRegistries = PersistenceRegistries(
    ruleConfigs = buildRuleConfigPersistenceRegistry(),
    discardPiles = buildDiscardPilePersistenceRegistry(),
    playerRuleStates = buildPlayerRuleStatePersistenceRegistry(),
    dynamicRuleStates = buildDynamicRuleStatePersistenceRegistry(),
    exhaustiveDrawReasons = buildExhaustiveDrawReasonPersistenceRegistry(),
)
