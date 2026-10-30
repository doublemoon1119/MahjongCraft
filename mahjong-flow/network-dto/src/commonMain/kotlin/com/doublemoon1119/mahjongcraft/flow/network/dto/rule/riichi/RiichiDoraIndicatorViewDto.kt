package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.network.dto.component.ComponentViewDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 隨已公開的表寶牌指示牌傳送其用途，不公開其他牌牆牌的隱藏用途。 */
@Serializable
@SerialName("mahjongcraft:riichi/tile_view/dora_indicator")
data object RiichiDoraIndicatorViewDto : ComponentViewDto
