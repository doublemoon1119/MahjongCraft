package com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure

import com.doublemoon1119.mahjongcraft.logic.base.Tile

/**
 * 代表麻將手牌分析用的面子組合（Mentsu）。
 *
 * 此類別用於立直麻將的手牌結構分析，專注於牌型的內部組合邏輯。
 * 與 [com.doublemoon1119.mahjongcraft.logic.base.Meld] 不同：
 * - [com.doublemoon1119.mahjongcraft.logic.base.Meld] 用於表示遊戲狀態中的副露（已公開的牌組），
 *   包含 [com.doublemoon1119.mahjongcraft.logic.base.MeldType]、來源方向等遊戲狀態資訊。
 * - 本類別用於靜態的手牌分析，不涉及遊戲狀態，僅包含牌面資訊。
 *
 * 面子是指 3 張或 4 張相同的牌（刻子 / 槓），或者是 3 張連續的牌（順子）。
 */
sealed class Mentsu {

    /**
     * 刻子（Kotsu）- 三張相同的牌。
     *
     * @property tile 構成刻子的牌（去除赤寶牌標記後的代表牌）。
     */
    data class Kotsu(val tile: Tile) : Mentsu() {
        override val tiles: List<Tile>
            get() = listOf(tile, tile, tile)
    }

    /**
     * 順子（Shuntsu）- 三張連續的數牌。
     *
     * @property headTile 順子的最小牌（去除赤寶牌標記後的代表牌）。
     */
    data class Shuntsu(val headTile: Tile) : Mentsu() {
        override val tiles: List<Tile>
            get() {
                val numTile = headTile as Tile.Numeric
                return listOf(
                    headTile,
                    Tile.Numeric(numTile.suit, numTile.value + 1),
                    Tile.Numeric(numTile.suit, numTile.value + 2),
                )
            }
    }

    /**
     * 暗槓（Ankan）- 四張相同的牌。
     *
     * @property tile 構成暗槓的牌（去除赤寶牌標記後的代表牌）。
     */
    data class Ankan(val tile: Tile) : Mentsu() {
        override val tiles: List<Tile>
            get() = listOf(tile, tile, tile, tile)
    }

    /**
     * 明槓（Minkan）- 四張相同的牌（鳴取他人的捨牌）。
     *
     * @property tile 構成明槓的牌（去除赤寶牌標記後的代表牌）。
     */
    data class Minkan(val tile: Tile) : Mentsu() {
        override val tiles: List<Tile>
            get() = listOf(tile, tile, tile, tile)
    }

    /**
     * 加槓（Kakan）- 四張相同的牌（在已有的碰基礎上增加）。
     *
     * @property tile 構成加槓的牌（去除赤寶牌標記後的代表牌）。
     */
    data class Kakan(val tile: Tile) : Mentsu() {
        override val tiles: List<Tile>
            get() = listOf(tile, tile, tile, tile)
    }

    /** 取得此面子所包含的所有牌。 */
    abstract val tiles: List<Tile>
}
