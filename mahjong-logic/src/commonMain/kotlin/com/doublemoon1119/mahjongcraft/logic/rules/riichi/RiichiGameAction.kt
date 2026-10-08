package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/** 日本麻將規則專屬的擴充動作。 */
sealed interface RiichiGameAction : ExtensionGameAction {
    /** 宣告立直。 */
    data object Riichi : RiichiGameAction {
        override val id: String = MahjongCraftMetadata.id("riichi/declare_riichi")
    }

    /** 三人麻將拔北：把手中的北放到一旁當寶牌，並從嶺上補一張牌。 */
    data object PullNorth : RiichiGameAction {
        override val id: String = MahjongCraftMetadata.id("riichi/pull_north")
    }
}

/** 日本麻將的立直動作。 */
val RIICHI_GAME_ACTION: GameAction.Extension = GameAction.Extension(RiichiGameAction.Riichi)

/** 三人麻將的拔北動作。 */
val PULL_NORTH_GAME_ACTION: GameAction.Extension = GameAction.Extension(RiichiGameAction.PullNorth)
