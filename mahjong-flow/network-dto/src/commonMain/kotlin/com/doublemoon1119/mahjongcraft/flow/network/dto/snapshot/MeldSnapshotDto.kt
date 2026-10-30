package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.flow.network.dto.component.ComponentViewDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.IdentifiedTileSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.MeldTypeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.RelativeDirectionDto
import com.doublemoon1119.mahjongcraft.logic.base.MeldSnapshot
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

/**
 * [MeldSnapshot] 的網路 DTO。
 *
 * @property componentViews 已裁切的副露組件輸出。
 * @property id 副露的穩定識別碼。
 * @property type 副露類型。
 * @property tiles 副露包含的牌張。
 * @property sourceTile 被鳴入的牌；沒有時為 `null`。
 * @property sourceDirection 牌張來源方向。
 * @property faceDownTileIds 需要在桌面上朝下呈現的牌張 ID。
 */
@Serializable
data class MeldSnapshotDto(
    val id: String,
    val type: MeldTypeDto,
    val tiles: List<IdentifiedTileSnapshotDto>,
    val sourceTile: IdentifiedTileSnapshotDto?,
    val sourceDirection: RelativeDirectionDto,
    val faceDownTileIds: Set<String>,
    val componentViews: List<@Polymorphic ComponentViewDto>,
)
