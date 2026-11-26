package com.doublemoon1119.mahjongcraft.flow.server.game.random

import com.doublemoon1119.mahjongcraft.flow.common.game.random.MatchSeed
import com.doublemoon1119.mahjongcraft.logic.table.RoundRandomness

/**
 * 每一局開局時取得亂數來源的方式，開局與換局都從這裡取得。
 *
 * - 正式對局一律由對局種子衍生（[SEEDED]）。
 * - 固定內容的測試對局可以換成指定的骰子與牌山排列，其餘流程不變。
 */
fun interface RoundRandomnessSource {
    /**
     * 取得一局的亂數來源。
     *
     * @param matchSeed 這場對局的種子。
     * @param roundOrdinal 這場對局中第幾次開局，從 0 起算。
     * @return 這一局的亂數來源。
     */
    fun roundRandomness(matchSeed: MatchSeed, roundOrdinal: Int): RoundRandomness

    /** 正式對局使用的來源。 */
    companion object {
        /** 由對局種子衍生。 */
        val SEEDED: RoundRandomnessSource = RoundRandomnessSource { matchSeed, roundOrdinal -> matchSeed.roundRandomness(roundOrdinal) }
    }
}
