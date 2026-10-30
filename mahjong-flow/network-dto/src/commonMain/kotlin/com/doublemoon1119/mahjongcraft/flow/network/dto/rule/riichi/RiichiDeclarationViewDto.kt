package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.network.dto.component.ComponentViewDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 公開的立直宣告，不重複攜帶宣告牌的完整資料。
 *
 * @property kind 立直種類名稱。
 * @property discardTileId 宣告捨牌的識別碼。
 * @property isIppatsu 是否仍在一發期限內。
 */
@Serializable
@SerialName("mahjongcraft:riichi/player_view/declaration")
data class RiichiDeclarationViewDto(
    val kind: String,
    val discardTileId: String,
    val isIppatsu: Boolean,
) : ComponentViewDto
