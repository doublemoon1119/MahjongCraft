package com.doublemoon1119.mahjongcraft.logic.base

/** 規則模組可擴充的強型別遊戲動作；同一種動作的不同參數共用一個穩定 ID。 */
interface ExtensionGameAction {
    /** 供保存與跨模組識別使用的穩定 namespaced ID；保存登記及動作用語使用同一個 ID。 */
    val id: String
}
