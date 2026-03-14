package com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure

import com.doublemoon1119.mahjongcraft.domain.base.Tile

/**
 * 雀頭（Janto / 對子）。
 *
 * @property tile 構成雀頭的牌（去除赤寶牌標記後的代表牌）。
 */
data class Janto(val tile: Tile)
