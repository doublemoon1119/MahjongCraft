package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.IdentifiedTileSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.MeldTypeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.RelativeDirectionDto
import com.doublemoon1119.mahjongcraft.logic.base.MeldSnapshot
import kotlinx.serialization.Serializable

/**
 * [MeldSnapshot] 的網路 DTO。
 *
 * @property type 副露類型。
 * @property tiles 副露包含的牌張。
 * @property sourceTile 被鳴入的牌；沒有時為 `null`。
 * @property sourceDirection 牌張來源方向。
 */
@Serializable
data class MeldSnapshotDto(
    val type: MeldTypeDto,
    val tiles: List<IdentifiedTileSnapshotDto>,
    val sourceTile: IdentifiedTileSnapshotDto?,
    val sourceDirection: RelativeDirectionDto,
)
