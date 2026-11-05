package com.doublemoon1119.mahjongcraft.testing.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.service.PlayerAreaPresentationState
import com.doublemoon1119.mahjongcraft.flow.common.game.service.PlayerAreaSequenceStep

/**
 * [FakeGamePresentationPublisher] 收到的一次依序牌區變化。
 *
 * @property areas 完成後各相關玩家的牌區。
 * @property steps 依呈現順序排列的牌區變化。
 */
data class PlayerAreaSequence(
    val areas: List<PlayerAreaPresentationState>,
    val steps: List<PlayerAreaSequenceStep>,
)
