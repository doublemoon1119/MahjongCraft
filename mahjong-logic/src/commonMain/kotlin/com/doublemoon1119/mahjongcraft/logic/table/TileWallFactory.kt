package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.tile.TileComposition
import kotlin.random.Random

/** 同一份規則牌組同時提供純組成與實際建牌。 */
interface TileWallFactory {
    /**
     * 取得牌值與張數，不建立牌、不洗牌或產生 UUID。
     *
     * @return 目前設定使用的牌組組成。
     */
    fun composition(): TileComposition

    /**
     * 依同一份組成建立並洗好一副牌。
     *
     * @param random 產生牌張 ID 與洗牌使用的亂數來源；不得改用其他亂數，否則同一個來源無法重現同一副牌山。
     * @return 新的牌山。
     */
    fun create(random: Random): TileWall = composition().createWall(random)
}
