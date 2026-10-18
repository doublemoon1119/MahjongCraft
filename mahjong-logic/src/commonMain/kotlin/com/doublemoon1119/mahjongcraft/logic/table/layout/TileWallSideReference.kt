package com.doublemoon1119.mahjongcraft.logic.table.layout

/** 牌牆側面配置使用的方向基準；不影響開門與摸牌順序。 */
enum class TileWallSideReference {
    /** 固定布局的基準方向，換莊時不旋轉整組配置。 */
    TABLE,

    /** 以莊家所在側為基準，換莊時跟著莊家的方向旋轉。 */
    DEALER,
}
