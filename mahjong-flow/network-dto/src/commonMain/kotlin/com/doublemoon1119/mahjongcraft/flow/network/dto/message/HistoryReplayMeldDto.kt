package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.MeldTypeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.RelativeDirectionDto
import kotlinx.serialization.Serializable

/**
 * Replay 副露 DTO。
 *
 * @property type 副露種類。
 * @property tiles 副露牌索引。
 * @property sourceTile 來源牌索引。
 * @property sourceDirection 來源方向。
 */
@Serializable
data class HistoryReplayMeldDto(
    val type: MeldTypeDto,
    val tiles: List<Int>,
    val sourceTile: Int?,
    val sourceDirection: RelativeDirectionDto?,
)
