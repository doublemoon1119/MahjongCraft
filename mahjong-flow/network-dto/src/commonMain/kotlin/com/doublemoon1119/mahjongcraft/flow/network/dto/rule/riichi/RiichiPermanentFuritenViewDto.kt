package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.network.dto.component.ComponentViewDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 只向已成立永久振聽的玩家本人交付的標記。 */
@Serializable
@SerialName("mahjongcraft:riichi/player_view/permanent_furiten")
data object RiichiPermanentFuritenViewDto : ComponentViewDto
