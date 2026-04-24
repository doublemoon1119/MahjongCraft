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
     * @property mentsus 最多四個面子（不包含副露）。
     * @property pair 雀頭。
     * @property fuuro 副露（已曝光的面子）。
     * @property completionType 判斷是單騎、雙碰聽、兩面、邊張、嵌張的情況。
     */
    data class Standard(
        val mentsus: List<Mentsu>,
        val pair: Janto,
        val fuuro: List<Fuuro> = emptyList(),
        val completionType: CompletionType = CompletionType.Ryanmen
    ) : HandStructure()

    /**
     * 七對子（Chiitoitsu）。
     *
     * 七對子必定為門清，不包含副露。
     *
     * @property pairs 七個對子。
     */
    data class Chiitoitsu(
        val pairs: List<Janto>
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
