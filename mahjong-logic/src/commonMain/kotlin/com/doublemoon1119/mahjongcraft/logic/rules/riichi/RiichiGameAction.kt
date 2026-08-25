package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.BuiltInGameActionIds
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction

/** 日本麻將規則專屬的擴充動作。 */
sealed interface RiichiGameAction : ExtensionGameAction {
    /** 宣告立直。 */
    data object Riichi : RiichiGameAction {
        override val id: String = BuiltInGameActionIds.RIICHI
    }
}

/** 日本麻將的立直動作。 */
val RIICHI_GAME_ACTION: GameAction = GameAction.Extension(RiichiGameAction.Riichi)
