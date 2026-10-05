package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/** MahjongCraft 內建胡牌展示理由的穩定識別碼。 */
object BuiltInWinCelebrationCueIds {
    /** 以役種名稱建立內建日麻役滿的展示理由。 */
    fun riichiYakuman(path: String): String = MahjongCraftMetadata.id(path)
}
