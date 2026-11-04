package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/** 台灣麻將規則專屬的擴充動作。 */
sealed interface TaiwanGameAction : ExtensionGameAction {
    /** 公開摸到的花牌並補牌。 */
    data object RevealFlower : TaiwanGameAction {
        override val id: String = MahjongCraftMetadata.id("taiwan/reveal_flower")
    }
}
