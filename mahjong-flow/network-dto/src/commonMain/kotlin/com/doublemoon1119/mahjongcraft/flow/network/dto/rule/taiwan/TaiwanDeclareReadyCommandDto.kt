package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExtensionGameCommandDto
import kotlinx.serialization.Serializable

/**
 * 台灣麻將宣告聽牌命令的網路 DTO。
 *
 * @property tileId 宣告時打出的牌 ID。
 */
@Serializable
data class TaiwanDeclareReadyCommandDto(
    val tileId: String,
) : ExtensionGameCommandDto
