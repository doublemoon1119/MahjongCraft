package com.doublemoon1119.mahjongcraft.flow.network.dto.rule

import com.doublemoon1119.mahjongcraft.logic.table.PlayerRuleState

/**
 * 將 [PlayerRuleState] 轉換成已註冊的網路 DTO。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun PlayerRuleState.toDto(registries: NetworkDtoRegistries): PlayerRuleStateDto = registries.playerRuleState.toDto(this)

/**
 * 將已註冊的 [PlayerRuleStateDto] 還原成領域玩家規則狀態。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun PlayerRuleStateDto.toDomain(registries: NetworkDtoRegistries): PlayerRuleState = registries.playerRuleState.toDomain(this)
