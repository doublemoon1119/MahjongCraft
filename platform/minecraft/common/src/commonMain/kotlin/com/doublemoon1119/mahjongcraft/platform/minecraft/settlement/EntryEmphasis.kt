package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/** 結算清單條目的顯示樣式。 */
enum class EntryEmphasis {
    /** 一般條目。 */
    NORMAL,

    /** 強調條目，例如日麻的役滿或台麻的八台以上台種。 */
    HIGHLIGHTED,

    /** 只適用於部分付款者的條目，例如只有其中一家多付的台數。 */
    CONDITIONAL,
}
