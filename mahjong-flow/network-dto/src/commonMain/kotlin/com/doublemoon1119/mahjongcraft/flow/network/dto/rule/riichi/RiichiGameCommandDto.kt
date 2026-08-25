package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExtensionGameCommandDto
import kotlinx.serialization.Serializable

/** 日麻立直宣告命令的網路 DTO。 */
@Serializable
data class RiichiGameCommandDto(val tileId: String) : ExtensionGameCommandDto
