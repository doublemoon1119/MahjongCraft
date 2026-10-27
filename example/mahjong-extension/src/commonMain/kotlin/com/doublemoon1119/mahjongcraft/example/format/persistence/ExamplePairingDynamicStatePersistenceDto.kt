package com.doublemoon1119.mahjongcraft.example.format.persistence

import kotlinx.serialization.Serializable

/**
 * 簡易配對麻將動態狀態的保存格式。
 *
 * @property bonusTileValues 本局公開展示並會在自摸時加分的牌面數值。
 */
@Serializable
data class ExamplePairingDynamicStatePersistenceDto(
    val bonusTileValues: Set<Int>,
)
