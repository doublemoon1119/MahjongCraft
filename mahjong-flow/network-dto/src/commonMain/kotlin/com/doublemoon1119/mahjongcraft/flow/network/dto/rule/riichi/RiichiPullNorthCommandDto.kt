package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExtensionGameCommandDto
import kotlinx.serialization.Serializable

/** 三人日麻拔北命令的網路 DTO。 */
@Serializable
data object RiichiPullNorthCommandDto : ExtensionGameCommandDto
