package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import kotlinx.serialization.Serializable

/** 網路快照中的牌張姿態，以名稱編碼。 */
@Serializable
enum class TilePoseDto {
    /** 直立。 */
    UPRIGHT,

    /** 正面朝上平放。 */
    FACE_UP,

    /** 正面朝下平放。 */
    FACE_DOWN,
}
