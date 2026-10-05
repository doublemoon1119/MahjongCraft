package com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure

import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection

/**
 * 副露（Fuuro）。
 *
 * @property mentsu 面子組合。
 * @property from 副露來源的方向。
 * @property isOpen 是否為明面子。只有暗槓（Ankan）為暗面子，碰、吃、明槓（Minkan）與加槓（Kakan）都是明面子。
 */
data class Fuuro(
    val mentsu: Mentsu,
    val from: RelativeDirection,
    val isOpen: Boolean = mentsu !is Mentsu.Ankan,
)
