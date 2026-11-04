package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/** 台灣麻將規則專屬的擴充動作。 */
sealed interface TaiwanGameAction : ExtensionGameAction {
    /** 公開摸到的花牌並補牌。 */
    data object RevealFlower : TaiwanGameAction {
        override val id: String = MahjongCraftMetadata.id("taiwan/reveal_flower")
    }

    /** 打出一張牌並宣告聽牌；依宣告時機成為一般宣告聽牌、天聽或地聽。 */
    data object DeclareReady : TaiwanGameAction {
        override val id: String = MahjongCraftMetadata.id("taiwan/declare_ready")
    }
}

/** 台灣麻將的宣告聽牌動作。 */
val TAIWAN_DECLARE_READY_ACTION: GameAction.Extension = GameAction.Extension(TaiwanGameAction.DeclareReady)
