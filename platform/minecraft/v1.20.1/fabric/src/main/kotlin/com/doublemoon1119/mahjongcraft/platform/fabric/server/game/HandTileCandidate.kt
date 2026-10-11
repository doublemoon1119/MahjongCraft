package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import kotlin.uuid.Uuid

/**
 * `discard` 與動作選牌使用的手牌候選項目。
 *
 * @property tileId 手牌實體識別碼。
 * @property token 指令使用的選牌代號。
 * @property tile 候選牌的牌種。
 */
data class HandTileCandidate(
    val tileId: Uuid,
    val token: String,
    val tile: Tile,
)
