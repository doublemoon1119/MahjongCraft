package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import kotlin.uuid.Uuid

/**
 * 一次吃、碰或槓完成後交給規則的情境。
 *
 * @property playersBefore 動作套用前的完整玩家列表。
 * @property playersAfter 動作套用後、尚未套用本次規則回呼的完整玩家列表。
 * @property actorPlayerId 完成本次動作的玩家。
 * @property meldType 本次完成的副露或槓種類。
 */
class MeldCompletedContext(
    val playersBefore: List<MahjongPlayer>,
    val playersAfter: List<MahjongPlayer>,
    val actorPlayerId: Uuid,
    val meldType: MeldType,
)
