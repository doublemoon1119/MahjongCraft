package com.doublemoon1119.mahjongcraft.logic.table.layout

/**
 * 規則指定的牌牆面與四個標準側面的對應。
 *
 * 第幾個元素對應同索引的邏輯牌牆面；數值為從上方俯視、相對 [reference] 的逆時針四分之一圈數，
 * `0` 為基準側、`1` 為右側、`2` 為對側、`3` 為左側。這裡不指定長度、座標或牌張尺寸。
 *
 * @property reference 側面配置是否隨莊家方向旋轉。
 * @param quarterTurns 各邏輯牌牆面使用的標準側面，不可重複；建立時會複製清單。
 */
class TileWallSideArrangement(
    val reference: TileWallSideReference,
    quarterTurns: List<Int>,
) {
    /** 脫離呼叫端可變清單的側面對應。 */
    private val sides = quarterTurns.toList()

    /** 此配置的牌牆面數，與玩家人數無關。 */
    val sideCount: Int get() = sides.size

    init {
        require(sides.size in 1..4) { "Wall arrangement must contain one to four sides" }
        require(sides.all { it in 0..3 }) { "Wall side quarter turns must be between zero and three" }
        require(sides.distinct().size == sides.size) { "Wall arrangement sides must be distinct" }
    }

    /**
     * 取得一面牌牆相對基準方向的旋轉量。
     *
     * @param side 邏輯牌牆面的零基底索引。
     * @return 從上方俯視的逆時針四分之一圈數。
     * @throws IndexOutOfBoundsException 面索引不在配置範圍內。
     */
    fun quarterTurnsOf(side: Int): Int = sides[side]
}
