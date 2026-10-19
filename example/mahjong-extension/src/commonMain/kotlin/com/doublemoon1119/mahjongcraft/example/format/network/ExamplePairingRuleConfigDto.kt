package com.doublemoon1119.mahjongcraft.example.format.network

import com.doublemoon1119.mahjongcraft.example.format.ExampleFormatIds
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.MahjongRuleConfigDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 簡易配對規則設定的網路格式。
 *
 * @property exchangeEnabled 是否在每局摸打前交換兩張牌。
 * @property paymentPerPlayer 每位其他玩家在自摸成立時支付的點數。
 * @property roundCount 整場預定局數。
 */
@Serializable
@SerialName(ExampleFormatIds.RULE_CONFIG)
data class ExamplePairingRuleConfigDto(
    val exchangeEnabled: Boolean,
    val paymentPerPlayer: Int,
    val roundCount: Int,
) : MahjongRuleConfigDto
