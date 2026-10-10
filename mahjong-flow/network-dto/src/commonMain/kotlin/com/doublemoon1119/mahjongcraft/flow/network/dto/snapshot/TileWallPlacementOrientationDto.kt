package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import kotlinx.serialization.Serializable

/** 實體牌牆位置的離散朝向網路 DTO。 */
@Serializable
enum class TileWallPlacementOrientationDto {
    /** 保持基本牌牆格位朝向。 */
    DEFAULT,

    /** 順時針旋轉九十度。 */
    CLOCKWISE_90,

    /** 旋轉一百八十度。 */
    HALF_TURN,

    /** 逆時針旋轉九十度。 */
    COUNTERCLOCKWISE_90,
}
