package com.doublemoon1119.mahjongcraft.flow.network.dto.component

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 只隨已獲准的牌面傳送的透明牌特性。 */
@Serializable
@SerialName("mahjongcraft:tile_view/transparent")
data object TransparentTileViewDto : ComponentViewDto
