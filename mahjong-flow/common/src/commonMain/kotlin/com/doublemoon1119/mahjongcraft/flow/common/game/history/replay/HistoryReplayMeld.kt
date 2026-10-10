package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection

/**
 * 歷史副露。
 *
 * @property type 副露種類。
 * @property tiles 副露牌。
 * @property sourceTile 來源牌。
 * @property sourceDirection 來源方向。
 */
data class HistoryReplayMeld(
    val type: MeldType,
    val tiles: List<HistoryTileReference>,
    val sourceTile: HistoryTileReference?,
    val sourceDirection: RelativeDirection?,
)
