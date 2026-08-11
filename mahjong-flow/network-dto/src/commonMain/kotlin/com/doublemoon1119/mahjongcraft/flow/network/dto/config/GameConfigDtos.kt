package com.doublemoon1119.mahjongcraft.flow.network.dto.config

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ActionTimeControl
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatorHandVisibility
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.MahjongRuleConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDto
import kotlinx.serialization.Serializable

/** [ActionTimeControl] 的網路 DTO。 */
@Serializable
data class ActionTimeControlDto(
    val actionSeconds: Int,
    val reserveSeconds: Int,
)

/** [GameFlowConfig] 的網路 DTO。 */
@Serializable
data class GameFlowConfigDto(
    val timeControl: ActionTimeControlDto,
    val spectatingPolicy: SpectatingPolicy,
    val spectatorHandVisibility: SpectatorHandVisibility,
)

/** [GameConfig] 的網路 DTO。 */
@Serializable
data class GameConfigDto(
    val ruleConfig: MahjongRuleConfigDto,
    val flowConfig: GameFlowConfigDto,
)

/** 將 [GameConfig] 轉換成網路 DTO。 */
fun GameConfig.toDto(registries: NetworkDtoRegistries): GameConfigDto = GameConfigDto(
    ruleConfig = ruleConfig.toDto(registries),
    flowConfig = flowConfig.toDto(),
)

/** 將 [GameConfigDto] 還原成 [GameConfig]。 */
fun GameConfigDto.toDomain(registries: NetworkDtoRegistries): GameConfig = GameConfig(
    ruleConfig = ruleConfig.toDomain(registries),
    flowConfig = flowConfig.toDomain(),
)

/** 將 [GameFlowConfig] 轉換成網路 DTO。 */
private fun GameFlowConfig.toDto(): GameFlowConfigDto = GameFlowConfigDto(
    timeControl = ActionTimeControlDto(timeControl.actionSeconds, timeControl.reserveSeconds),
    spectatingPolicy = spectatingPolicy,
    spectatorHandVisibility = spectatorHandVisibility,
)

/** 將 [GameFlowConfigDto] 還原成 [GameFlowConfig]。 */
private fun GameFlowConfigDto.toDomain(): GameFlowConfig = GameFlowConfig(
    timeControl = ActionTimeControl.from(timeControl.actionSeconds, timeControl.reserveSeconds),
    spectatingPolicy = spectatingPolicy,
    spectatorHandVisibility = spectatorHandVisibility,
)
