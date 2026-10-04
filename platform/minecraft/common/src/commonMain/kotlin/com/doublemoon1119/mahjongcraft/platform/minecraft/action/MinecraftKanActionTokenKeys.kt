package com.doublemoon1119.mahjongcraft.platform.minecraft.action

/** 指令候選與歷史動作正規化使用的槓牌簡寫；與保存格式中的 `kan` 種類分開管理。 */
object MinecraftKanActionTokenKeys {
    /** 大明槓的動作簡寫。 */
    const val OPEN: String = "kan_open"

    /** 暗槓的動作簡寫。 */
    const val CLOSED: String = "kan_closed"

    /** 加槓的動作簡寫。 */
    const val ADDED: String = "kan_added"
}
