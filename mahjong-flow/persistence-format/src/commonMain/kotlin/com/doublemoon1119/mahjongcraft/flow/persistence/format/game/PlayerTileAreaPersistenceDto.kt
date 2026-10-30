package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import kotlinx.serialization.Serializable

/**
 * 明確保存玩家移出牌區的身分與牌張版本。
 *
 * @property id 區域完整識別碼。
 * @property tiles 此區域內依規則順序排列的牌張。
 */
@Serializable
data class PlayerTileAreaPersistenceDto(
    val id: String,
    val tiles: List<IdentifiedTilePersistenceDto>,
)
