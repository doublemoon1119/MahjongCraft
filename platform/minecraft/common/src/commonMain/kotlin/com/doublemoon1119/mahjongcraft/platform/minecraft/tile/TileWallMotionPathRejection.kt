package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/** 路徑規劃拒絕原因。 */
enum class TileWallMotionPathRejection {
    /** 開門來源與最終布局的牌張集合不一致。 */
    INCONSISTENT_LAYOUT,

    /** 起訖位置同時改變水平位置與垂直層級，無法判斷動作順序。 */
    MIXED_HORIZONTAL_AND_VERTICAL,

    /** 起訖位置跨越超過一個相鄰牆角。 */
    NON_ADJACENT_SIDES,
}
