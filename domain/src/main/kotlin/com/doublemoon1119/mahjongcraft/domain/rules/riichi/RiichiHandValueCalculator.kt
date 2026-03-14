package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.HandYakuResult
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.RiichiYakuContext
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.dora.calculateDora

/**
 * 日本麻將手牌番數計算機。
 *
 * 負責計算手牌的全部番數，包含：
 * - 寶牌 (Dora)
 * - 裏寶牌 (Ura Dora)
 * - 赤寶牌 (Aka Dora)
 * - 各類一般役 (1-6 翻)
 * - 字牌役
 * - 役滿
 *
 * 使用 [RiichiYakuContext] 提供計算所需的上下文資訊。
 */
class RiichiHandValueCalculator {
    /**
     * 計算手牌的總番數。
     *
     * @param context 役種計算所需的上下文資訊。
     * @return 包含所有役種結果的 [HandYakuResult]。
     */
    fun calculate(context: RiichiYakuContext): HandYakuResult {
        val yakuResults = mutableListOf<YakuResult>()

        // 1. 計算寶牌（包括裏寶牌與赤寶牌）
        val doraResult = calculateDora(
            hand = context.hand,
            winningTile = context.winningTile,
            doraIndicators = context.doraIndicators,
            isRiichi = context.isRiichi,
            uraDoraIndicators = context.uraDoraIndicators
        )
        if (doraResult.han > 0) {
            yakuResults.add(doraResult)
        }

        // 2. 計算赤寶牌（在 winningTile 上的赤寶牌額外計算）
        val akaDoraCount = countAkaDora(context)
        if (akaDoraCount > 0) {
            yakuResults.add(YakuResult.han(YakuType.AkaDora, akaDoraCount))
        }

        // 3. 計算一般役
        calculateStandardYaku(context, yakuResults)

        // 4. 計算字牌役
        calculateHonorYaku(context, yakuResults)

        // 5. 計算特殊役
        calculateSpecialYaku(context, yakuResults)

        // 6. 計算役滿
        calculateYakuman(context, yakuResults)

        // 計算總番數
        val totalHan = calculateTotalHan(yakuResults)

        return HandYakuResult(
            yakuResults = yakuResults,
            totalHan = totalHan,
            isCompleteHand = true
        )
    }

    /**
     * 計算赤寶牌數量。
     *
     * 赤寶牌為帶有 [com.doublemoon1119.mahjongcraft.domain.base.Tile.Numeric.isRed] 標記的牌（5 萬、5 筒、5 條）。
     * 每一張赤寶牌額外提供 1 翻。
     *
     * @param context 役種計算上下文。
     * @return 赤寶牌數量。
     */
    private fun countAkaDora(context: RiichiYakuContext): Int {
        var count = 0

        // 檢查立牌中的赤寶牌
        context.hand.standingTiles.forEach { identifiedTile ->
            if (identifiedTile.tile is com.doublemoon1119.mahjongcraft.domain.base.Tile.Numeric &&
                identifiedTile.tile.isRed
            ) {
                count++
            }
        }

        // 檢查胡牌張是否為赤寶牌
        if (context.winningTile is com.doublemoon1119.mahjongcraft.domain.base.Tile.Numeric &&
            context.winningTile.isRed
        ) {
            count++
        }

        return count
    }

    /**
     * 計算一般役（1-6 翻）。
     */
    private fun calculateStandardYaku(context: RiichiYakuContext, results: MutableList<YakuResult>) {
        // TODO: 實作斷么九、平和、一杯口等役種檢測
    }

    /**
     * 計算字牌役（場風、自風、役牌）。
     */
    private fun calculateHonorYaku(context: RiichiYakuContext, results: MutableList<YakuResult>) {
        // TODO: 實作字牌役檢測
    }

    /**
     * 計算特殊役（立直、一發、嶺上花等）。
     */
    private fun calculateSpecialYaku(context: RiichiYakuContext, results: MutableList<YakuResult>) {
        // 立直
        if (context.isRiichi) {
            val riichiHan = if (context.isDoubleRiichi) 2 else 1
            results.add(YakuResult.han(YakuType.Riichi, riichiHan))
        }

        // 一發
        if (context.isIppatsu) {
            results.add(YakuResult.han(YakuType.Ippatsu, 1))
        }

        // 嶺上花
        if (context.isRinshanKaihou) {
            results.add(YakuResult.han(YakuType.RinshanKaihou, 1))
        }

        // 海底撈月
        if (context.isLastDraw && context.isTsumo) {
            results.add(YakuResult.han(YakuType.Haitei, 1))
        }

        // 河底撈魚
        if (context.isLastDiscard && !context.isTsumo) {
            results.add(YakuResult.han(YakuType.Houtei, 1))
        }

        // 槓槓
        if (context.revealedExposedKans.isNotEmpty()) {
            results.add(YakuResult.han(YakuType.Chankan, context.revealedExposedKans.size))
        }

        // 搶槓
        if (context.isRobbingKan && context.revealedExposedKans.isEmpty()) {
            results.add(YakuResult.han(YakuType.Chankan, 1))
        }
    }

    /**
     * 計算役滿。
     */
    private fun calculateYakuman(context: RiichiYakuContext, results: MutableList<YakuResult>) {
        // TODO: 實作國士無雙、九蓮寶燈、四暗刻等役滿檢測
    }

    /**
     * 計算總番數。
     *
     * 若存在役滿，則返回負值表示役滿倍數：
     * -1 = 役滿 (1倍)
     * -2 = 雙倍役滿 (2倍)
     * -3 = 三倍役滿 (3倍)
     * ...
     */
    private fun calculateTotalHan(results: List<YakuResult>): Int {
        val yakumanCount = results.count { it.isYakuman && !it.isDoubleYakuman }
        val doubleYakumanCount = results.count { it.isDoubleYakuman }

        return if (yakumanCount > 0 || doubleYakumanCount > 0) {
            // 役滿：一般役滿 = 1，雙倍役滿 = 2
            -(yakumanCount + doubleYakumanCount * 2)
        } else {
            results.sumOf { it.han }
        }
    }
}
