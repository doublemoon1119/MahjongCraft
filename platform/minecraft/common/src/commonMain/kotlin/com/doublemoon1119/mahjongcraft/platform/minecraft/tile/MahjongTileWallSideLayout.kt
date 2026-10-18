package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallSideArrangement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallSideReference
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableSide

/**
 * 已解析的邏輯牌牆面與桌子局部側面的對應，不依玩家人數推算牌牆面數。
 *
 * 固定桌邊基準的 `0`、`1`、`2`、`3` 分別是局部南、東、北、西；莊家基準則由莊家實際桌邊起算。
 * 所有方向最後仍隨桌子朝向轉成世界方向，不是世界固定南北。
 *
 * @param sides 各邏輯面的桌邊；由建立入口複製與驗證。
 */
class MahjongTileWallSideLayout private constructor(private val sides: List<MahjongTableSide>) {
    /** 這份配置包含的牌牆面數。 */
    val sideCount: Int get() = sides.size

    /**
     * 取得指定牌牆面的桌邊。
     *
     * @param side 邏輯牌牆面索引。
     * @return 桌子局部側面，尚未套用桌子的世界朝向。
     */
    fun sideOf(side: Int): MahjongTableSide = sides[side]

    /** 依各邏輯面對應的桌邊比較配置，不依建立次數區分相同配置。 */
    override fun equals(other: Any?): Boolean = other is MahjongTileWallSideLayout && sides == other.sides

    /** 相同桌邊配置使用相同雜湊值。 */
    override fun hashCode(): Int = sides.hashCode()

    /** 將規則的固定配置轉成標準桌邊。 */
    companion object {
        /** 標準側面依逆時針四分之一圈排列。 */
        private val SIDE_ORDER = listOf(MahjongTableSide.SOUTH, MahjongTableSide.EAST, MahjongTableSide.NORTH, MahjongTableSide.WEST)

        /**
         * 依規則指定的基準解析牌牆位置。
         *
         * @param arrangement 規則的牌牆面配置。
         * @param dealerSide 莊家實際所在的桌子局部側面。
         * @return 固定桌邊或隨莊家旋轉後的完整對應。
         */
        fun resolve(arrangement: TileWallSideArrangement, dealerSide: MahjongTableSide): MahjongTileWallSideLayout {
            val anchor = when (arrangement.reference) {
                TileWallSideReference.TABLE -> 0
                TileWallSideReference.DEALER -> SIDE_ORDER.indexOf(dealerSide)
            }
            return MahjongTileWallSideLayout(
                List(arrangement.sideCount) { side -> SIDE_ORDER[(anchor + arrangement.quarterTurnsOf(side)).mod(SIDE_ORDER.size)] },
            )
        }
    }
}
