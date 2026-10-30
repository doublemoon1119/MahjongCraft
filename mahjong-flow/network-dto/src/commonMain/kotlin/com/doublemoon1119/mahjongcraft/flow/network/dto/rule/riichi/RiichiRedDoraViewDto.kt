package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.network.dto.component.ComponentViewDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 只隨已獲准的牌面傳送的赤寶牌特性。 */
@Serializable
@SerialName("mahjongcraft:riichi/tile_view/red_dora")
data object RiichiRedDoraViewDto : ComponentViewDto
