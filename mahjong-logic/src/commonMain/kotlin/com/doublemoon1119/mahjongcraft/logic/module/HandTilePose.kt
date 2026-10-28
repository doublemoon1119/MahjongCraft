package com.doublemoon1119.mahjongcraft.logic.module

/** 手牌對所有觀察者採用的擺放姿態，與牌面讀取權分開描述。 */
enum class HandTilePose {
    /** 直立；牌面是否可見由可見性政策決定。 */
    UPRIGHT,

    /** 正面朝上平放；必須對所有合法觀察者公開。 */
    FACE_UP,
}
