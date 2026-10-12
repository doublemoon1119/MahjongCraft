package com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionGameCommand
import kotlin.uuid.Uuid

/**
 * 日麻規則提供的立直宣告命令。
 *
 * @property tileId 宣告立直時要打出的牌張識別碼。
 */
data class RiichiGameCommand(val tileId: Uuid) : ExtensionGameCommand
