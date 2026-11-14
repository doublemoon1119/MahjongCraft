package com.doublemoon1119.mahjongcraft.testing.flow.common.game

import com.doublemoon1119.mahjongcraft.flow.common.game.random.MatchSeed

/**
 * 測試用：由一個數字建立固定的對局種子，同一個 [seed] 每次重現同一場對局的座位、牌山與骰子。
 *
 * @param seed 決定對局的數字。
 * @return 固定的對局種子。
 */
fun testMatchSeed(seed: Int): MatchSeed = MatchSeed.fromByteArray(
    bytes = ByteArray(MatchSeed.SIZE_BYTES) { index ->
        if (index < Int.SIZE_BYTES) (seed ushr (index * Byte.SIZE_BITS)).toByte() else 0
    },
)
