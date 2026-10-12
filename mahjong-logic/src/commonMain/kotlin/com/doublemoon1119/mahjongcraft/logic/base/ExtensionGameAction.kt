package com.doublemoon1119.mahjongcraft.logic.base

/** 規則模組可擴充的強型別遊戲動作。 */
interface ExtensionGameAction {
    /** 供序列化與跨模組識別使用的穩定 namespaced ID。 */
    val id: String
}
