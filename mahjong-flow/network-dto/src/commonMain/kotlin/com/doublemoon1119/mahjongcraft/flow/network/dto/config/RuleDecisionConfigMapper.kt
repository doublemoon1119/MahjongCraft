package com.doublemoon1119.mahjongcraft.flow.network.dto.config

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionConfig

/**
 * 將網路 DTO 還原成規則選擇設定。
 *
 * @return 還原後的規則選擇設定。
 */
fun RuleDecisionConfigDto.toDomain(): RuleDecisionConfig = RuleDecisionConfig(baseSeconds)

/**
 * 將規則選擇設定轉換成網路 DTO。
 *
 * @return 規則選擇設定的網路 DTO。
 */
fun RuleDecisionConfig.toDto(): RuleDecisionConfigDto = RuleDecisionConfigDto(baseSeconds)
