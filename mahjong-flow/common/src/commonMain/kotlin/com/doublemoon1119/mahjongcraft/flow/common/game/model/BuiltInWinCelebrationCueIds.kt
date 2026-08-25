package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/** MahjongCraft 內建胡牌展示 cue 的穩定識別碼。 */
object BuiltInWinCelebrationCueIds {
    /** 無專屬定義時使用的通用 cue。 */
    val GENERIC: String = MahjongCraftMetadata.id("generic")

    /** 以役種名稱建立內建日麻役滿 cue。 */
    fun riichiYakuman(path: String): String = MahjongCraftMetadata.id(path)
}
