package com.doublemoon1119.mahjongcraft.platform.fabric.entity

/** 管理中牌張落地後可短暫呈現的牌面提示種類。 */
enum class TileActionPopupKind {
    /** 不呈現短暫牌面。 */
    NONE,

    /** 捨牌落入牌河。 */
    DISCARD,

    /** 牌張落入副露區。 */
    MELD,
    ;

    companion object {
        /** 從同步 ordinal 解析種類，非法值安全回退為 [NONE]。 */
        fun fromOrdinal(ordinal: Int): TileActionPopupKind = entries.getOrElse(ordinal) { NONE }
    }
}
