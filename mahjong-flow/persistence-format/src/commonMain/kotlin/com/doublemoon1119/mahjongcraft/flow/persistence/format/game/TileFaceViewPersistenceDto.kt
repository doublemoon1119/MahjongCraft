package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.TypedPersistenceDto
import kotlinx.serialization.Serializable

/**
 * 保存已裁切的牌面特徵，不包含局內身分或原始組件。
 *
 * @property tile 基本牌面。
 * @property views 提交當時已裁切的特徵資料。
 * @property appearanceId 已授權的外觀識別碼；未指定時為 null。
 */
@Serializable
data class TileFaceViewPersistenceDto(
    val tile: TilePersistenceDto,
    val views: List<TypedPersistenceDto>,
    val appearanceId: String?,
)
