package com.doublemoon1119.mahjongcraft.example.format.persistence

import kotlinx.serialization.Serializable

/**
 * 簡易配對規則設定的保存格式。
 *
 * @property exchangeEnabled 是否在每局摸打前交換兩張牌。
 * @property paymentPerPlayer 每位其他玩家在自摸成立時支付的點數。
 * @property roundCount 整場預定局數。
 * @property wallTileValues 會加入牌山的牌面點數。
 * @property publicFacesEnabled 是否讓每種點數四張中的三張所有人都看得到牌面。
 */
@Serializable
data class ExamplePairingRuleConfigPersistenceDto(
    val exchangeEnabled: Boolean,
    val paymentPerPlayer: Int,
    val roundCount: Int,
    val wallTileValues: Set<Int>,
    val publicFacesEnabled: Boolean,
)
