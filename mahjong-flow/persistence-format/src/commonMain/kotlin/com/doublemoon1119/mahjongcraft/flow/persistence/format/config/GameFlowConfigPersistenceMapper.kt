package com.doublemoon1119.mahjongcraft.flow.persistence.format.config

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ActionTimeControl
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig

/**
 * 將 [GameFlowConfig] 轉換成 persistence DTO。
 *
 * @return 轉換後的 persistence DTO。
 */
fun GameFlowConfig.toPersistenceDto(): GameFlowConfigPersistenceDto = GameFlowConfigPersistenceDto(
    baseSeconds = timeControl.baseSeconds,
    reserveSeconds = timeControl.reserveSeconds,
    roundPreparation = roundPreparation?.toPersistenceDto(),
    decisionTimeoutPolicy = decisionTimeoutPolicy,
    spectatingPolicy = spectatingPolicy,
    spectatorHandVisibility = spectatorHandVisibility,
)

/**
 * 將 [GameFlowConfigPersistenceDto] 還原成 [GameFlowConfig]。
 *
 * @return 還原後的流程設定。
 */
fun GameFlowConfigPersistenceDto.toDomain(): GameFlowConfig = GameFlowConfig(
    timeControl = ActionTimeControl.from(baseSeconds, reserveSeconds),
    roundPreparation = roundPreparation?.toDomain(),
    decisionTimeoutPolicy = decisionTimeoutPolicy,
    spectatingPolicy = spectatingPolicy,
    spectatorHandVisibility = spectatorHandVisibility,
)
