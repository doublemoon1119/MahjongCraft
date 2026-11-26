package com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionGameCommand
import kotlin.uuid.Uuid

/**
 * 台灣麻將的宣告聽牌命令。
 *
 * @property tileId 宣告時要打出的牌張識別碼。
 */
data class TaiwanDeclareReadyCommand(val tileId: Uuid) : ExtensionGameCommand
