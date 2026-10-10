package com.doublemoon1119.mahjongcraft.flow.network.dto.rule

import com.doublemoon1119.mahjongcraft.logic.config.ScoreConfig

/**
 * 將 [ScoreConfig] 轉換成已註冊的網路 DTO。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun ScoreConfig.toDto(registries: NetworkDtoRegistries): ScoreConfigDto = registries.scoreConfig.toDto(this)

/**
 * 將已註冊的 [ScoreConfigDto] 還原成領域計分設定。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun ScoreConfigDto.toDomain(registries: NetworkDtoRegistries): ScoreConfig = registries.scoreConfig.toDomain(this)
