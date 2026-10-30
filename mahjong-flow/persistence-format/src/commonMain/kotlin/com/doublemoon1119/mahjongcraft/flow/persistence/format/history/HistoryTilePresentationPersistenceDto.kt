package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.TypedPersistenceDto
import kotlinx.serialization.Serializable

/**
 * 一張牌在交易提交後解算出的呈現結果。
 *
 * @property appearanceId 外觀樣式；沒有時為 null。
 * @property views 可公開的牌面特徵。
 * @property pose 共同姿態名稱。
 * @property audience 當時知道牌面的讀取者。
 */
@Serializable
data class HistoryTilePresentationPersistenceDto(
    val appearanceId: String?,
    val views: List<TypedPersistenceDto>,
    val pose: String,
    val audience: HistoryTileAudiencePersistenceDto,
)
