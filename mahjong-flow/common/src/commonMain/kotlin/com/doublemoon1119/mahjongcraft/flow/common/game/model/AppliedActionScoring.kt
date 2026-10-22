package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlin.uuid.Uuid

/**
 * 已通過驗證並寫入權威狀態的一次動作即時收付。
 *
 * @property actorPlayerId 執行動作的玩家。
 * @property action 觸發收付的動作。
 * @property scoreDeltas 各玩家的點數增減；只包含本局玩家，不包含 0。
 * @property potDeposit 放進規則點數池的點數，正數為放入、負數為取出；與 [scoreDeltas] 的合計為 0。
 */
data class AppliedActionScoring(
    val actorPlayerId: Uuid,
    val action: GameAction,
    val scoreDeltas: Map<Uuid, Int>,
    val potDeposit: Int,
)
