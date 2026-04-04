package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.yakuman

import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.Mentsu
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.domain.util.withoutRed

/**
 * 四暗刻 (Suuankou / Four Concealed Triplets) 役滿檢測器。
 *
 * 四暗刻是立直麻將中的役滿，條件如下：
 * - 手牌必須由四組暗面子組成（暗刻或暗槓）
 * - 暗面子可來自手牌或暗槓（Ankan）
 * - 不可有副露（碰、吃，明槓、加槓）
 * - 門清限定
 *
 * 暗面子定義：
 * - 暗刻 (Kotsu)：手牌中自行湊成的三張相同牌組
 * - 暗槓 (Ankan)：手牌中自行湊成的四張相同牌組
 *
 * 四暗刻單騎：
 * - 同樣是 4 個暗面子，但胡牌形成第四個暗刻（單騎）
 * - 當雀頭的牌與胡牌相同時，表示單騎和牌
 *
 * 牌型範例：
 * - 四暗刻：1m 1m 1m、9m 9m 9m、5s 5s 5s、2p 2p 2p、發 發（雀頭，胡牌東）
 * - 四暗刻單騎：1m 1m 1m、9m 9m 9m、5s 5s 5s、發 發 發、2p 2p（雀頭，等待發）
 *
 * @param handStructure 手牌結構（由 [com.doublemoon1119.mahjongcraft.domain.rules.riichi.RiichiHandDecomposer] 分割後的結果）。
 * @param winningTile 胡牌張（用於判斷四暗刻單騎）。
 * @param isMenzen 是否為門前清。
 * @param isTsumo 是否為自摸。
 * @return 四暗刻或四暗刻單騎結果，若不符合則返回 null。
 */
fun calculateSuuankou(
    handStructure: HandStructure,
    winningTile: Tile,
    isMenzen: Boolean,
    isTsumo: Boolean,
): YakuResult? {
    val standard = handStructure as? HandStructure.Standard ?: return null

    // 必須為門前清
    if (!isMenzen) {
        return null
    }

    // 四暗刻：4 個面子都是暗面子（Kotsu 或 Ankan）
    val concealedKotsuCount = standard.mentsus.count { mentsu ->
        mentsu is Mentsu.Kotsu
    } + standard.fuuro.count { fuuro ->
        fuuro.mentsu is Mentsu.Ankan
    }

    // 雀頭的牌與胡牌相同（單騎和牌）
    val isTankiWait = standard.pair.tile.withoutRed == winningTile.withoutRed

    val isSuuuankou = if (isTankiWait) {
        concealedKotsuCount == 4
    } else {
        isTsumo && concealedKotsuCount == 4  // 雙碰聽的時候，要自摸才算四暗刻
    }

    return when {
        // 四暗刻單騎（雙倍役滿）：4 個暗面子 + 單騎聽牌
        isTankiWait && isSuuuankou -> YakuResult.doubleYakuman(YakuType.SuuankouTanki)
        // 四暗刻（役滿）：4 個暗面子
        isSuuuankou -> YakuResult.yakuman(YakuType.Suuankou)
        else -> null
    }
}
