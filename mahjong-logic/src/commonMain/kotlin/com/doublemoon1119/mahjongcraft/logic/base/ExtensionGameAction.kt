package com.doublemoon1119.mahjongcraft.logic.base

/** 規則模組可擴充的強型別遊戲動作。 */
interface ExtensionGameAction {
    /** 跨網路、存檔與呈現層保持穩定的 namespaced ID。 */
    val id: String
}
