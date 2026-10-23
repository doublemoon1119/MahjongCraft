package com.doublemoon1119.mahjongcraft.flow.persistence.format.config

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionConfig

/**
 * 將保存資料還原成規則選擇設定。
 *
 * @return 還原後的規則選擇設定。
 */
fun RuleDecisionConfigPersistenceDto.toDomain(): RuleDecisionConfig = RuleDecisionConfig(baseSeconds)

/**
 * 將規則選擇設定轉換成保存資料。
 *
 * @return 可保存的規則選擇設定。
 */
fun RuleDecisionConfig.toPersistenceDto(): RuleDecisionConfigPersistenceDto = RuleDecisionConfigPersistenceDto(baseSeconds)
