package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExtensionGameCommandDto
import kotlinx.serialization.Serializable

/**
 * 日麻立直宣告命令的網路 DTO。
 *
 * @property tileId 宣告立直時打出的牌 ID。
 */
@Serializable
data class RiichiGameCommandDto(
    val tileId: String,
) : ExtensionGameCommandDto
