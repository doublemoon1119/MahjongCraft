package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan

import com.doublemoon1119.mahjongcraft.flow.network.dto.component.ComponentViewDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 只向過水中的玩家本人交付的標記。 */
@Serializable
@SerialName("mahjongcraft:taiwan/player_view/passed_win")
data object TaiwanPassedWinViewDto : ComponentViewDto
