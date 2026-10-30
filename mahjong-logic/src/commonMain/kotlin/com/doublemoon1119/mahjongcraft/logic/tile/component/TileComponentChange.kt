package com.doublemoon1119.mahjongcraft.logic.tile.component

import com.doublemoon1119.mahjongcraft.logic.component.ComponentPatch
import kotlin.uuid.Uuid

/**
 * 正式規則動作要求修改的一張牌。
 *
 * @property tileId 當前牌張 UUID。
 * @property patch 明確的組件設定與移除，帶著防止覆蓋過期資料的預期舊值。
 */
data class TileComponentChange(
    val tileId: Uuid,
    val patch: ComponentPatch,
)
