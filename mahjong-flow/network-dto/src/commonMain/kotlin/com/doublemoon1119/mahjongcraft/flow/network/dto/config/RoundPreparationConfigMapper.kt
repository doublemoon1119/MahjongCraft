package com.doublemoon1119.mahjongcraft.flow.network.dto.config

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationConfig

/**
 * 將網路 DTO 還原成局前準備設定。
 *
 * @return 還原後的局前準備設定。
 */
fun RoundPreparationConfigDto.toDomain(): RoundPreparationConfig = RoundPreparationConfig(baseSeconds)

/**
 * 將局前準備設定轉換成網路 DTO。
 *
 * @return 局前準備設定的網路 DTO。
 */
fun RoundPreparationConfig.toDto(): RoundPreparationConfigDto = RoundPreparationConfigDto(baseSeconds)
