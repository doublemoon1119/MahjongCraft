package com.doublemoon1119.mahjongcraft.flow.network.dto.rule

import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState

/**
 * 將 [DynamicRuleState] 轉換成已註冊的網路 DTO。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun DynamicRuleState.toDto(registries: NetworkDtoRegistries): DynamicRuleStateDto = registries.dynamicRuleState.toDto(this)

/**
 * 將已註冊的 [DynamicRuleStateDto] 還原成領域動態規則狀態。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun DynamicRuleStateDto.toDomain(registries: NetworkDtoRegistries): DynamicRuleState = registries.dynamicRuleState.toDomain(this)
