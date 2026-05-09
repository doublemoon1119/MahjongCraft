package com.doublemoon1119.mahjongcraft.testing.domain.config

import com.doublemoon1119.mahjongcraft.domain.config.GameLength

/**
 * 用於測試的模擬對局長度配置。
 */
class FakeGameLength(
    override val totalRounds: Int = 4,
    override val roundsOffset: Int = 0
) : GameLength