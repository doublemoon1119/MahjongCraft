package com.doublemoon1119.mahjongcraft.example.format.persistence

import kotlinx.serialization.Serializable

/**
 * 本局已選定的加分牌保存資料。
 *
 * @property values 萬子點數集合；讀取時不重新抽選。
 */
@Serializable
data class ExamplePairingBonusTilesPersistenceDto(val values: Set<Int>)
