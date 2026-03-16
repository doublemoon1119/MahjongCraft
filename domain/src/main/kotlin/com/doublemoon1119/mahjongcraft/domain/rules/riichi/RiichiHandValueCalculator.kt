package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.base.MeldType
import com.doublemoon1119.mahjongcraft.domain.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.Fuuro
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.Mentsu
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.HandYakuResult
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.RiichiYakuContext
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.dora.calculateDora
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.standard.calculateChiitoitsu
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.standard.calculateChinitsu
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.standard.calculateHonitsu
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.standard.calculateIipeikou
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.standard.calculateIttuitsu
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.standard.calculateRyanpeikou
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.standard.calculateTanyao
import com.doublemoon1119.mahjongcraft.domain.util.withoutRed

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
        // 1. 嘗試分解手牌（用於需要手牌結構的役種）
        val allTiles = context.hand.standingTiles.map { it.tile.withoutRed } + context.winningTile.withoutRed
        val fuuro = context.hand.exposedMelds.map { meld ->
            // 將 base.Meld 轉換為 structure.Fuuro
            val tile = meld.tiles.first().tile.withoutRed
            val mentsu = when (meld.type) {
                MeldType.PON -> Mentsu.Kotsu(tile)
                MeldType.CHI -> Mentsu.Shuntsu(headTile = tile)
                MeldType.OPEN_KAN -> Mentsu.Minkan(tile)
                MeldType.CLOSED_KAN -> Mentsu.Ankan(tile)
                MeldType.ADDED_KAN -> Mentsu.Kakan(tile)
            }
            Fuuro(
                mentsu = mentsu,
                from = meld.sourceDirection
            )
        }

        val handStructure = RiichiHandDecomposer.decompose(allTiles, fuuro)

        // 2. 先計算役滿
        val yakumanResults = mutableListOf<YakuResult>()
        calculateYakuman(context, handStructure, yakumanResults)

        // 若有役滿，則只計算役滿（役滿疊加）
        if (yakumanResults.isNotEmpty()) {
            val totalHan = calculateTotalHan(yakumanResults)
            return HandYakuResult(
                yakuResults = yakumanResults,
                totalHan = totalHan,
                isCompleteHand = true
            )
        }

        // 3. 無役滿時，計算一般役
        val yakuResults = mutableListOf<YakuResult>()

        // 計算寶牌
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

        // 計算赤寶牌
        val akaDoraCount = countAkaDora(context)
        if (akaDoraCount > 0) {
            yakuResults.add(YakuResult.han(YakuType.AkaDora, akaDoraCount))
        }

        // 計算一般役
        calculateStandardYaku(context, handStructure, yakuResults)

        // 計算字牌役
        calculateHonorYaku(context, yakuResults)

        // 計算特殊役
        calculateSpecialYaku(context, yakuResults)

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
     * 赤寶牌為帶有 [Tile.Numeric.isRed] 標記的牌（5 萬、5 筒、5 條）。
     * 每一張赤寶牌額外提供 1 翻。
     *
     * @param context 役種計算上下文。
     * @return 赤寶牌數量。
     */
    private fun countAkaDora(context: RiichiYakuContext): Int {
        var count = 0

        // 檢查立牌中的赤寶牌
        context.hand.standingTiles.forEach { identifiedTile ->
            if (identifiedTile.tile is Tile.Numeric &&
                identifiedTile.tile.isRed
            ) {
                count++
            }
        }

        // 檢查胡牌張是否為赤寶牌
        if (context.winningTile is Tile.Numeric &&
            context.winningTile.isRed
        ) {
            count++
        }

        return count
    }

    /**
     * 計算一般役（1-6 翻）。
     *
     * 處理役種之間的互斥與優先級：
     * - 清一色 > 混一色（保留較高番數者）
     * - 兩杯口 > 一杯口（保留較高番數者）
     * - 七對子與一杯口、兩杯口互斥（按點數決定）
     */
    private fun calculateStandardYaku(
        context: RiichiYakuContext,
        handStructure: HandStructure?,
        results: MutableList<YakuResult>
    ) {
        val standardResults = mutableListOf<YakuResult>()

        // 斷么九
        calculateTanyao(
            hand = context.hand,
            winningTile = context.winningTile,
            isMenzen = context.isMenzen,
            allowOpenTanyao = context.allowOpenTanyao
        )?.let { standardResults.add(it) }

        // 一氣通貫
        calculateIttuitsu(
            hand = context.hand,
            winningTile = context.winningTile,
            isMenzen = context.isMenzen
        )?.let { standardResults.add(it) }

        // 混一色與清一色
        val honitsu = calculateHonitsu(
            hand = context.hand,
            winningTile = context.winningTile
        )
        val chinitsu = calculateChinitsu(
            hand = context.hand,
            winningTile = context.winningTile
        )
        // 清一色優先於混一色
        if (chinitsu != null) {
            standardResults.add(chinitsu)
        } else if (honitsu != null) {
            standardResults.add(honitsu)
        }

        // 計算一杯口與兩杯口
        val iipeikou = calculateIipeikou(
            handStructure = handStructure,
            isMenzen = context.isMenzen
        )
        val ryanpeikou = calculateRyanpeikou(
            handStructure = handStructure,
            isMenzen = context.isMenzen
        )

        // 計算七對子
        val chiitoitsu = calculateChiitoitsu(
            handStructure = handStructure,
            isMenzen = context.isMenzen
        )

        // 處理七對子與一杯口、兩杯口的衝突
        // 兩杯口 (3 han) > 七對子 (2 han) > 一杯口 (1 han)
        when {
            ryanpeikou != null -> standardResults.add(ryanpeikou)
            chiitoitsu != null -> standardResults.add(chiitoitsu)
            iipeikou != null -> standardResults.add(iipeikou)
        }

        results.addAll(standardResults)
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

        // 搶槓
        if (context.isRobbingKan) {
            results.add(YakuResult.han(YakuType.Chankan, 1))
        }
    }

    /**
     * 計算役滿。
     */
    private fun calculateYakuman(
        context: RiichiYakuContext,
        handStructure: HandStructure?,
        results: MutableList<YakuResult>
    ) {
        // TODO: 使用 handStructure 實作國士無雙、九蓮寶燈、四暗刻等役滿檢測
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
