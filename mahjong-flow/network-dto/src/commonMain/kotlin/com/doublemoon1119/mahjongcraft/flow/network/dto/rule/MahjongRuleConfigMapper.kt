package com.doublemoon1119.mahjongcraft.flow.network.dto.rule

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig

/**
 * 將 [MahjongRuleConfig] 轉換成已註冊的網路 DTO。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun MahjongRuleConfig.toDto(registries: NetworkDtoRegistries): MahjongRuleConfigDto = registries.ruleConfig.toDto(this)

/**
 * 將已註冊的 [MahjongRuleConfigDto] 還原成領域規則設定。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun MahjongRuleConfigDto.toDomain(registries: NetworkDtoRegistries): MahjongRuleConfig = registries.ruleConfig.toDomain(this)
