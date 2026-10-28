package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import kotlinx.serialization.Serializable

/** 網路快照中的手牌姿態，以名稱編碼。 */
@Serializable
enum class HandTilePoseDto {
    /** 直立。 */
    UPRIGHT,

    /** 正面朝上平放。 */
    FACE_UP,
}
