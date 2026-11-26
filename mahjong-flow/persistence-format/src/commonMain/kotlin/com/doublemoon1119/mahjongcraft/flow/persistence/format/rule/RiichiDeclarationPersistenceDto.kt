package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import kotlinx.serialization.Serializable

/**
 * 立直宣告的保存資料。
 *
 * @property kind 立直種類名稱。
 * @property discardTileId 宣告捨牌的識別碼。
 * @property isIppatsu 是否仍在一發期限內。
 */
@Serializable
data class RiichiDeclarationPersistenceDto(
    val kind: String,
    val discardTileId: String,
    val isIppatsu: Boolean,
)
