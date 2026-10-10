package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * Replay 捨牌 DTO。
 *
 * @property tile 捨牌索引。
 * @property isTaken 是否已被副露取走。
 * @property markers 規則提供的公開標記。
 */
@Serializable
data class HistoryReplayDiscardDto(val tile: Int, val isTaken: Boolean, val markers: Set<String>)
