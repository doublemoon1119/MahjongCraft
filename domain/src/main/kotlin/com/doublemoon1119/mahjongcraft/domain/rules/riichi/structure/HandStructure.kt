package com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure

import com.doublemoon1119.mahjongcraft.domain.base.Tile

/**
 * 手牌結構。
 *
 * 用於表示經過分割後的手牌，可以是標準手牌（4 面子 + 1 雀頭）、
 * 七對子（Chiitoitsu）、或國士無雙（KokushiMusou）。
 */
sealed class HandStructure {

    /**
     * 標準手牌（4 面子 + 1 雀頭）。
     *
     * @property mentsus 四個面子（不包含副露）。
     * @property pair 雀頭。
     * @property fuuro 副露（已曝光的面子）。
     */
    data class Standard(
        val mentsus: List<Mentsu>,
        val pair: Janto,
        val fuuro: List<Fuuro> = emptyList()
    ) : HandStructure()

    /**
     * 七對子（Chiitoitsu）。
     *
     * @property pairs 七個對子。
     * @property fuuro 副露（已曝光的面子）。
     */
    data class Chiitoitsu(
        val pairs: List<Janto>,
        val fuuro: List<Fuuro> = emptyList()
    ) : HandStructure()

    /**
     * 國士無雙（KokushiMusou）。
     *
     * @property orphans 十三張么九牌。
     * @property headTile 做雀頭的牌（必須是十三張之一）。
     */
    data class KokushiMusou(
        val orphans: List<Tile>,
        val headTile: Tile
    ) : HandStructure()
}
