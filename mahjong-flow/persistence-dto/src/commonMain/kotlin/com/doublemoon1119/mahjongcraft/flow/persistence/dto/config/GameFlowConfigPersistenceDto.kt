package com.doublemoon1119.mahjongcraft.flow.persistence.dto.config

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ActionTimeControl
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatorHandVisibility
import kotlinx.serialization.Serializable

/** [GameFlowConfig] 的完整 persistence DTO。 */
@Serializable
data class GameFlowConfigPersistenceDto(
    val baseSeconds: Int = ActionTimeControl.Normal.baseSeconds,
    val reserveSeconds: Int = ActionTimeControl.Normal.reserveSeconds,
    val spectatingPolicy: SpectatingPolicy = SpectatingPolicy.ENABLED,
    val spectatorHandVisibility: SpectatorHandVisibility = SpectatorHandVisibility.REVEALED,
)

/** 將 [GameFlowConfig] 轉換成 persistence DTO。 */
fun GameFlowConfig.toPersistenceDto(): GameFlowConfigPersistenceDto = GameFlowConfigPersistenceDto(
    baseSeconds = timeControl.baseSeconds,
    reserveSeconds = timeControl.reserveSeconds,
    spectatingPolicy = spectatingPolicy,
    spectatorHandVisibility = spectatorHandVisibility,
)

/** 將 [GameFlowConfigPersistenceDto] 還原成 [GameFlowConfig]。 */
fun GameFlowConfigPersistenceDto.toDomain(): GameFlowConfig = GameFlowConfig(
    timeControl = ActionTimeControl.from(baseSeconds, reserveSeconds),
    spectatingPolicy = spectatingPolicy,
    spectatorHandVisibility = spectatorHandVisibility,
)
