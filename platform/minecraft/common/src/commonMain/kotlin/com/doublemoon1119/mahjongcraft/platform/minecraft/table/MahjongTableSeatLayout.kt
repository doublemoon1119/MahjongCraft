package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableSide

/**
 * 描述標準麻將桌上，依玩家席位 index 對應的桌面側面。
 *
 * 這個布局只描述玩家席位，不描述牌牆或規則的回合順序。兩人時玩家面對面；三人與四人時沿用
 * 標準桌面順序。桌子在世界中的朝向由使用端另外處理。
 *
 * @property playerCount 此布局支援的玩家人數。
 * @property seatSides 各座位對應的固定桌邊，只由建立入口提供，不對外公開可變清單。
 */
class MahjongTableSeatLayout private constructor(
    val playerCount: Int,
    private val seatSides: List<MahjongTableSide>,
) {
    /**
     * 取得指定玩家席位所在的桌面側面。
     *
     * @param seatIndex 玩家席位的零基底 index。
     * @return 該席位對應的桌面側面。
     * @throws IndexOutOfBoundsException [seatIndex] 不在此布局的有效範圍內。
     */
    fun sideOf(seatIndex: Int): MahjongTableSide {
        if (seatIndex !in 0 until playerCount) {
            throw IndexOutOfBoundsException("Seat index $seatIndex is outside 0 until $playerCount")
        }
        return seatSides[seatIndex]
    }

    /** 提供固定玩家人數的標準座位布局。 */
    companion object {
        /** 兩位玩家面對面的標準座位布局。 */
        val TWO_PLAYER: MahjongTableSeatLayout = createLayout(2)

        /** 三位玩家使用南、東、北三側的標準座位布局。 */
        val THREE_PLAYER: MahjongTableSeatLayout = createLayout(3)

        /** 四位玩家使用四側的標準座位布局。 */
        val FOUR_PLAYER: MahjongTableSeatLayout = createLayout(4)

        /**
         * 依玩家人數取得標準座位布局。
         *
         * @param playerCount 玩家人數，只接受 2、3 或 4。
         * @return 對應玩家人數的固定座位布局。
         * @throws IllegalArgumentException [playerCount] 不是 2、3 或 4。
         */
        fun forPlayerCount(playerCount: Int): MahjongTableSeatLayout = when (playerCount) {
            2 -> TWO_PLAYER
            3 -> THREE_PLAYER
            4 -> FOUR_PLAYER
            else -> throw IllegalArgumentException("Unsupported player count: $playerCount")
        }

        /** 建立指定玩家人數的固定標準座位布局。 */
        private fun createLayout(playerCount: Int): MahjongTableSeatLayout = when (playerCount) {
            2 -> MahjongTableSeatLayout(
                playerCount,
                listOf(MahjongTableSide.SOUTH, MahjongTableSide.NORTH),
            )

            3 -> MahjongTableSeatLayout(
                playerCount,
                listOf(MahjongTableSide.SOUTH, MahjongTableSide.EAST, MahjongTableSide.NORTH),
            )

            4 -> MahjongTableSeatLayout(
                playerCount,
                listOf(
                    MahjongTableSide.SOUTH,
                    MahjongTableSide.EAST,
                    MahjongTableSide.NORTH,
                    MahjongTableSide.WEST,
                ),
            )

            else -> throw IllegalArgumentException("Unsupported player count: $playerCount")
        }
    }
}
