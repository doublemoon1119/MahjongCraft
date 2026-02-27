package com.doublemoon1119.mahjongcraft.test.fakes

import com.doublemoon1119.mahjongcraft.model.config.GameLength

/**
 * 用於測試的模擬對局長度配置。
 */
class FakeGameLength(
    override val totalRounds: Int = 4,
    override val name: String = "Test Game Length"
) : GameLength