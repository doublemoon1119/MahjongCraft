package com.doublemoon1119.mahjongcraft.flow.network.dto.model

import com.doublemoon1119.mahjongcraft.flow.network.dto.component.ComponentViewDto
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

/**
 * 不帶局內身分的已裁切牌面資料。
 *
 * @property tile 基本牌面。
 * @property views 這次允許交付的特徵。
 * @property appearanceId 可使用的外觀識別碼；沒有時為 null。
 */
@Serializable
data class TileFaceViewDto(
    val tile: TileDto,
    val views: List<@Polymorphic ComponentViewDto>,
    val appearanceId: String?,
)
