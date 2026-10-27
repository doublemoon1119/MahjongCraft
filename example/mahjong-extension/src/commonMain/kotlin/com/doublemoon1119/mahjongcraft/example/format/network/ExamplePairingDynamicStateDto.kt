package com.doublemoon1119.mahjongcraft.example.format.network

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DynamicRuleStateDto
import kotlinx.serialization.Serializable

/**
 * 簡易配對麻將動態狀態的網路格式。
 *
 * @property bonusTileValues 本局公開展示並會在自摸時加分的牌面數值。
 */
@Serializable
data class ExamplePairingDynamicStateDto(
    val bonusTileValues: Set<Int>,
) : DynamicRuleStateDto
