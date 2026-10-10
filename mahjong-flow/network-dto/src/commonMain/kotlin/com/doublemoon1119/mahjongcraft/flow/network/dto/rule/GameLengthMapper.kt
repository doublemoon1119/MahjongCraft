package com.doublemoon1119.mahjongcraft.flow.network.dto.rule

import com.doublemoon1119.mahjongcraft.logic.config.GameLength

/**
 * 將 [GameLength] 轉換成已註冊的網路 DTO。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun GameLength.toDto(registries: NetworkDtoRegistries): GameLengthDto = registries.gameLength.toDto(this)

/**
 * 將已註冊的 [GameLengthDto] 還原成領域對局長度。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun GameLengthDto.toDomain(registries: NetworkDtoRegistries): GameLength = registries.gameLength.toDomain(this)
