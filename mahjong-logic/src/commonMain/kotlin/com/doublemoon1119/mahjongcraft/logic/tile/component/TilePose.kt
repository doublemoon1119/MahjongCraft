package com.doublemoon1119.mahjongcraft.logic.tile.component

/** 一張牌的共同姿態，不決定讀取者是否有權知道牌面。 */
enum class TilePose {
    /** 直立。 */
    UPRIGHT,

    /** 正面朝上平放，必須有公共牌面讀取權。 */
    FACE_UP,

    /** 背面朝上平放；仍可能有額外授予的牌面讀取權。 */
    FACE_DOWN,
}
