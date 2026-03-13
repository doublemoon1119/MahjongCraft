package com.doublemoon1119.mahjongcraft.testing.fakes

import com.doublemoon1119.mahjongcraft.domain.rules.riichi.RiichiScoreConfig
import com.doublemoon1119.mahjongcraft.domain.rules.taiwan.TaiwanScoreConfig

/**
 * 集中管理測試中常用的預設配置實體。
 */
object TestConstants {
    /** 預設的台灣麻將積分配置 (30/10)。 */
    val TAIWAN_SCORE_CONFIG = TaiwanScoreConfig(
        baseScore = 30,
        pointPerTai = 10,
        initialScore = 0,
        bustThreshold = null
    )

    /** 預設的日本麻將積分配置 (25000/30000)。 */
    val RIICHI_SCORE_CONFIG = RiichiScoreConfig(
        initialScore = 25000,
        bustThreshold = 0,
        minPointsToWin = 30000
    )
}
