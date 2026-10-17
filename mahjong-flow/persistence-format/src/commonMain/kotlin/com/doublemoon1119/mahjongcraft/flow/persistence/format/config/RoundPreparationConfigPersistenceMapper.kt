package com.doublemoon1119.mahjongcraft.flow.persistence.format.config

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationConfig

/**
 * 將保存資料還原成局前準備設定。
 *
 * @return 還原後的局前準備設定。
 */
fun RoundPreparationConfigPersistenceDto.toDomain(): RoundPreparationConfig = RoundPreparationConfig(baseSeconds)

/**
 * 將局前準備設定轉換成保存資料。
 *
 * @return 可保存的局前準備設定。
 */
fun RoundPreparationConfig.toPersistenceDto(): RoundPreparationConfigPersistenceDto = RoundPreparationConfigPersistenceDto(baseSeconds)
