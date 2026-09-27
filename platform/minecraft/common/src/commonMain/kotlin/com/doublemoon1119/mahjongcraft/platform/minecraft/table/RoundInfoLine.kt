package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.table.Wind

/** 局況顯示行可攜帶的規則中立參數。 */
sealed interface RoundInfoArgument {
    /** 供翻譯文字直接代入的整數。 */
    data class Number(val value: Int) : RoundInfoArgument

    /** 需要由呈現層轉成玩家語言的風位。 */
    data class WindValue(val value: Wind) : RoundInfoArgument
}

/**
 * 桌面局況顯示的一行內容。
 *
 * [key] 與參數由 Minecraft 呈現註冊決定，規則模組本身不需要依賴這個資料模型。
 */
data class RoundInfoLine(val key: String, val args: List<RoundInfoArgument> = emptyList())
