package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.yakuman

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandDecomposer
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType

/**
 * 國士無雙 (Kokushi Musou) 役滿檢測器。
 *
 * 國士無雙是立直麻將中的役滿，條件如下：
 * - 手牌必須由全部 13 種么九牌（老頭牌與字牌）組成
 * - 老頭牌：萬子、筒子、條子的 1 和 9（共 6 種）
 * - 字牌：東、南、西、北、白、發、中（共 7 種）
 * - 合計 13 種不同的牌，加上胡牌張共 14 張
 * - 必須為門前清（無副露）
 *
 * 國士無雙十三面 (Kokushi Musou 13-men) 為雙倍役滿：
 * - 指的是聽 13 張牌（聽所有么九牌）
 * - 胡牌時，手牌有全部 13 種么九牌，各 1 張（全部為單張）
 * - 胡牌張與雀頭相同，表示是湊齊最後一張形成對子
 *
 * 牌型範例（國士無雙十三面）：
 * - 牌型：1m 9m 1p 9p 1s 9s 東西南北白發中
 * - 也就是說，全部 13 種牌各 1 張
 * - 聽牌：全部 13 種牌
 * - 胡牌時： winningTile == headTile（例如自摸 1m 形成 1m1m）
 *
 * 牌型範例（一般國士無雙）：
 * - 牌型：1m1m 9m 1p 9p 1s 東西南北白發中
 * - 也就是說，有 1 個對子（1m），其餘 12 種牌各 1 張
 * - 聽牌：1m（單騎聽牌）
 * - 胡牌時： winningTile != headTile（例如摸 9s 與其他單張形成端子）
 *
 * @param handStructure 手牌結構（由 [RiichiHandDecomposer] 分割後的結果）。
 * @param winningTile 胡牌張。
 * @return 國士無雙役滿結果，若不符合則返回 null。
 */
fun calculateKokushiMusou(
    handStructure: HandStructure,
    winningTile: Tile,
): YakuResult? {
    // 檢查是否為國士無雙牌型
    val kokushiMusou = handStructure as? HandStructure.KokushiMusou ?: return null

    // 判斷是否為十三面
    // 十三面：胡牌張與雀頭相同（表示本來就有 13 張不同的牌，胡牌湊成對子）
    // 一般國士無雙：胡牌張與雀頭不同（表示本來就有 1 個對子 + 12 張單張）
    return if (winningTile == kokushiMusou.headTile) {
        // 國士無雙十三面 - 雙倍役滿
        YakuResult.doubleYakuman(YakuType.KokushiMusou13)
    } else {
        // 一般國士無雙 - 役滿
        YakuResult.yakuman(YakuType.KokushiMusou)
    }
}
