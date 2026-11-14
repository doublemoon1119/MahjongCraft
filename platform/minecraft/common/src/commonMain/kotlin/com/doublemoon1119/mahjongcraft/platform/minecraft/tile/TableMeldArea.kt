package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/** 副露擺放的區域。 */
enum class TableMeldArea {
    /** 玩家右手邊的桌角，往手牌方向排開，靠桌緣對齊。 */
    CORNER,

    /** 牌牆與手牌之間，先宣告的在右邊，整列對齊立牌的中心，摸牌時不移動；放不下的部分放到手牌那一列的右手邊，手牌往左讓。 */
    BETWEEN_WALL_AND_HAND,
}
