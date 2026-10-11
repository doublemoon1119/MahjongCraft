package com.doublemoon1119.mahjongcraft.testing.flow.common.game.service

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlin.uuid.Uuid

/**
 * 單筆已成立動作語音請求。
 *
 * @property actorId 執行動作的玩家 UUID。
 * @property action 已成立的遊戲動作。
 */
data class DeclaredGameActionContext(
    val actorId: Uuid,
    val action: GameAction,
)
