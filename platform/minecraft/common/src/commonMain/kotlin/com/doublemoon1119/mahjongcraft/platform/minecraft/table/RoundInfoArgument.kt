package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.table.Wind

/** 局況顯示行可攜帶的規則中立參數。 */
sealed interface RoundInfoArgument {
    /**
     * 供翻譯文字直接代入的整數。
     *
     * @property value 要代入翻譯文字的整數。
     */
    data class Number(val value: Int) : RoundInfoArgument

    /**
     * 需要由呈現層轉成玩家語言的風位。
     *
     * @property value 要本地化顯示的風位。
     */
    data class WindValue(val value: Wind) : RoundInfoArgument
}
