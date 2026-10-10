package com.doublemoon1119.mahjongcraft.flow.network.dto.config

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ActionTimeControl
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDto

/**
 * 將 [GameConfig] 轉換成網路 DTO。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun GameConfig.toDto(registries: NetworkDtoRegistries): GameConfigDto = GameConfigDto(
    ruleConfig = ruleConfig.toDto(registries),
    flowConfig = flowConfig.toDto(),
)

/**
 * 將 [GameConfigDto] 還原成 [GameConfig]。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun GameConfigDto.toDomain(registries: NetworkDtoRegistries): GameConfig = GameConfig(
    ruleConfig = ruleConfig.toDomain(registries),
    flowConfig = flowConfig.toDomain(),
)

/**
 * 將 [GameFlowConfig] 轉換成網路 DTO。
 *
 * @return 轉換後的資料。
 */
private fun GameFlowConfig.toDto(): GameFlowConfigDto = GameFlowConfigDto(
    timeControl = ActionTimeControlDto(timeControl.baseSeconds, timeControl.reserveSeconds),
    preparationBaseSeconds = preparationBaseSeconds,
    decisionTimeoutPolicy = decisionTimeoutPolicy,
    spectatingPolicy = spectatingPolicy,
    spectatorHandVisibility = spectatorHandVisibility,
)

/**
 * 將 [GameFlowConfigDto] 還原成 [GameFlowConfig]。
 *
 * @return 轉換後的資料。
 */
private fun GameFlowConfigDto.toDomain(): GameFlowConfig = GameFlowConfig(
    timeControl = ActionTimeControl.from(timeControl.baseSeconds, timeControl.reserveSeconds),
    preparationBaseSeconds = preparationBaseSeconds,
    decisionTimeoutPolicy = decisionTimeoutPolicy,
    spectatingPolicy = spectatingPolicy,
    spectatorHandVisibility = spectatorHandVisibility,
)
