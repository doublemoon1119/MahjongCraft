package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import kotlin.uuid.Uuid

/**
 * 一次已成立的立直宣告與其一發資格。
 *
 * @property kind 立直種類。
 * @property discardTileId 宣告時打出的牌張識別碼，對應牌河紀錄。
 * @property isIppatsu 目前是否仍在一發期限內。
 */
data class RiichiDeclaration(
    val kind: RiichiDeclarationKind,
    val discardTileId: Uuid,
    val isIppatsu: Boolean,
) {
    /** 是否為雙立直。 */
    val isDoubleRiichi: Boolean get() = kind == RiichiDeclarationKind.DOUBLE_RIICHI
}
