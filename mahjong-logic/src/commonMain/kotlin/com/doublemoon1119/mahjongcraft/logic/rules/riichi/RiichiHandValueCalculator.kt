package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueCalculator
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.Fuuro
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.Mentsu
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.riichiCanonical
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.dora.calculateAkaDora
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.dora.calculateDora
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.dora.calculateUraDora
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.honor.calculateHonorYaku
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard.calculateChiitoitsu
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard.calculateChinitsu
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard.calculateHonchan
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard.calculateHonitsu
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard.calculateHonroutou
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard.calculateIipeikou
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard.calculateIttuitsu
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard.calculateJunchan
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard.calculatePinfu
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard.calculateRyanpeikou
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard.calculateSanankou
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard.calculateSankantsu
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard.calculateSanshokuDokoku
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard.calculateSanshokuDoujun
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard.calculateTanyao
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard.calculateToitoi
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.yakuman.calculateChinroutou
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.yakuman.calculateChurenPoto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.yakuman.calculateKokushiMusou
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.yakuman.calculateRyuuuiisou
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.yakuman.calculateSangaen
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.yakuman.calculateSukantsu
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.yakuman.calculateSuuankou
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.yakuman.calculateSuushii
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.yakuman.calculateTsuuiisou
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlin.math.abs

/**
 * 日本麻將手牌番數計算機。
 *
 * 負責計算手牌的全部番數，使用 [RiichiHandValueContext] 提供計算所需的上下文資訊。
 *
 * @property useLocalYaku 是否啟用古役（Local Yaku）。TODO: 回頭實作古役邏輯。
 */
class RiichiHandValueCalculator(
    private val useLocalYaku: Boolean = false,
) : HandValueCalculator<RiichiHandValueContext, RiichiHandValueResult> {

    override fun calculate(context: RiichiHandValueContext): RiichiHandValueResult {
        // 嘗試分解手牌（用於需要手牌結構的役種）
        val handTiles = context.hand.standingTiles.map { it.tile.riichiCanonical }
        val winningTile = context.winningTile.riichiCanonical
        val fuuro = context.hand.exposedMelds.map { meld ->
            // 將 base.Meld 轉換為 structure.Fuuro
            val tile = meld.tiles.first().tile.riichiCanonical
            val mentsu = when (meld.type) {
                MeldType.PON -> Mentsu.Kotsu(tile)
                // CHI 的 tiles 依鳴牌時呼叫端選牌順序排列（被鳴牌固定最後一張），不保證數值遞增，
                // Shuntsu 的 headTile 必須是三張裡數值最小的那張，不能直接沿用 tiles.first()。
                MeldType.CHI -> Mentsu.Shuntsu(
                    headTile = meld.tiles.minBy { (it.tile.riichiCanonical as Tile.Numeric).value }.tile.riichiCanonical,
                )
                MeldType.OPEN_KAN -> Mentsu.Minkan(tile)
                MeldType.CLOSED_KAN -> Mentsu.Ankan(tile)
                MeldType.ADDED_KAN -> Mentsu.Kakan(tile)
            }
            Fuuro(
                mentsu = mentsu,
                from = meld.sourceDirection,
            )
        }

        // [HandStructure] 用於分析手牌內部結構，將一副手牌拆解成所有可能胡牌的形式，empty 表示手牌不符合任何胡牌形式。
        // 會需要 [HandStructure] 進行役種判定的役，例如：Pinfu、Chiitoitsu、Iipeikou、Ryanpeikou 等。
        // 不需要 [HandStructure] 的役種（如 Dora、Tanyao、Chinitsu 等）則直接從 hand 計算。
        val handStructures =
            RiichiHandDecomposer.decompose(handTiles = handTiles, winningTile = winningTile, fuuro = fuuro)
                .ifEmpty {
                    return RiichiHandValueResult(
                        yakuResults = emptyList(),
                        totalHan = 0,
                        totalFu = 0,
                        pointResult = RiichiPointResult.Ron(0),
                    )
                }

        // 將 handStructures 轉化成對應的點數
        val handValueResults = handStructures.map { handStructure ->
            // 先計算役滿
            val yakuResults = mutableListOf<YakuResult>()
            calculateYakuman(context, handStructure, yakuResults)

            // 若有任何役滿，則只計算役滿（役滿疊加）
            if (yakuResults.any { it.isYakuman }) {
                val totalHan = HanCalculator.calculateTotalHan(yakuResults)

                // 包牌僅在「狀態上已成立包牌責任」且「本次胡牌實際包含對應役滿」兩者皆成立時才適用，
                // 避免狀態成立後手牌卻演變成其他役滿（如濃厚牌型被打散重組）時誤套用包牌。
                val paoYakuType = context.paoLiability?.let { pao ->
                    when (pao.yaku) {
                        PaoYaku.Daisangen -> YakuType.Daisangen
                        PaoYaku.Daisuushii -> YakuType.Daisuushii
                    }
                }
                val paoYakuResult = paoYakuType?.let { type -> yakuResults.firstOrNull { it.yaku == type } }
                val pao = context.paoLiability?.takeIf { paoYakuResult != null }

                val pointResult = PointCalculator.calculateYakumanPoint(
                    yakumanMultiplier = abs(totalHan), // 役滿總翻數為負數，這裡帶入絕對值
                    // 莊家的自風永遠是東，不會隨場風（圈風）轉南——不能直接比較 roundWind == seatWind，
                    // 那算的是連風牌（雙東）是否成立，只有東場時才會跟「是否為莊家」剛好重合
                    isDealer = context.seatWind == Wind.EAST,
                    isTsumo = context.isTsumo,
                    isPao = pao != null,
                    // 包牌責任只承擔「觸發包牌的那個役滿本身」的倍數，不是整體役滿倍數：大三元是
                    // 1 倍役滿，但大四喜是雙倍役滿（見 YakuResult.doubleYakuman(Daisuushii)），兩者
                    // 包牌範圍不同，必須讀該役種自己的 han 而非寫死 1 倍。
                    paoYakuMultiplier = paoYakuResult?.let { abs(it.han) } ?: 1,
                )

                return@map RiichiHandValueResult(
                    yakuResults = yakuResults,
                    totalHan = totalHan,
                    totalFu = 0,
                    pointResult = pointResult,
                    paoLiability = pao,
                )
            }

            // 無役滿時，計算一般役
            // 計算寶牌
            val doraResult = calculateDora(
                hand = context.hand,
                winningTile = context.winningTile,
                doraIndicators = context.doraIndicators,
            )
            if (doraResult.han > 0) {
                yakuResults.add(doraResult)
            }

            // 計算裏寶牌（立直時）
            if (context.isRiichi) {
                val uraDoraResult = calculateUraDora(
                    hand = context.hand,
                    winningTile = context.winningTile,
                    uraDoraIndicators = context.uraDoraIndicators,
                )
                if (uraDoraResult.han > 0) {
                    yakuResults.add(uraDoraResult)
                }
            }

            // 計算赤寶牌
            val akaDoraResult = calculateAkaDora(
                hand = context.hand,
                winningTile = context.winningTile,
            )
            if (akaDoraResult.han > 0) {
                yakuResults.add(akaDoraResult)
            }

            // 計算一般役
            calculateStandardYaku(context, handStructure, yakuResults)

            // 計算字牌役
            calculateHonorYaku(context, fuuro, yakuResults)

            // 計算特殊役
            calculateSpecialYaku(context, yakuResults)

            // 計算總番數
            val totalHan = HanCalculator.calculateTotalHan(yakuResults)

            // 計算符數（滿貫(5翻)以上時不計算）
            val totalFu = if (totalHan >= 5) {
                -1 // 滿貫(5翻)以上時返回 -1
            } else {
                FuCalculator.calculateTotalFu(context, handStructure)
            }

            // 計算總點數
            val pointResult = PointCalculator.calculateNonYakumanPoint(
                han = totalHan,
                fu = totalFu,
                isDealer = context.roundWind == context.seatWind,
                isTsumo = context.isTsumo,
            )

            return@map RiichiHandValueResult(
                yakuResults = yakuResults,
                totalHan = totalHan,
                totalFu = totalFu,
                pointResult = pointResult,
            )
        }

        // 日本麻將適用高點法，選擇最終點數最高的結果。
        return handValueResults.maxWithOrNull(
            compareBy<RiichiHandValueResult> { it.totalPoint } // 1. 點數最高優先
                .thenBy { it.isYakuman } // 2. 點數相同時，役滿役優先於數役滿 (避免`累計役滿`覆蓋掉`役滿`)
                .thenBy { if (it.isYakuman) abs(it.totalHan) else it.totalHan } // 3. 翻數次之 (如果是役滿，其翻數以負數表示，這裡取其絕對值)
                .thenBy { it.totalFu }, // 4. 最後才是符數
        ) ?: RiichiHandValueResult(
            yakuResults = emptyList(),
            totalHan = 0,
            totalFu = 0,
            pointResult = RiichiPointResult.Ron(0),
        )
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
        context: RiichiHandValueContext,
        handStructure: HandStructure,
        results: MutableList<YakuResult>,
    ) {
        val standardResults = mutableListOf<YakuResult>()

        // 斷么九
        calculateTanyao(
            hand = context.hand,
            winningTile = context.winningTile,
            isMenzen = context.isMenzen,
            allowOpenTanyao = context.allowOpenTanyao,
        )?.let { standardResults.add(it) }

        // 一氣通貫
        calculateIttuitsu(
            hand = context.hand,
            winningTile = context.winningTile,
            isMenzen = context.isMenzen,
        )?.let { standardResults.add(it) }

        // 混一色與清一色
        val honitsu = calculateHonitsu(
            hand = context.hand,
            winningTile = context.winningTile,
            isMenzen = context.isMenzen,
        )
        val chinitsu = calculateChinitsu(
            hand = context.hand,
            winningTile = context.winningTile,
            isMenzen = context.isMenzen,
        )
        // 清一色優先於混一色
        if (chinitsu != null) {
            standardResults.add(chinitsu)
        } else if (honitsu != null) {
            standardResults.add(honitsu)
        }

        // 計算一杯口、兩杯口、七對子
        val iipeikou = calculateIipeikou(
            handStructure = handStructure,
            isMenzen = context.isMenzen,
        )
        val ryanpeikou = calculateRyanpeikou(
            handStructure = handStructure,
            isMenzen = context.isMenzen,
        )
        val chiitoitsu = calculateChiitoitsu(
            handStructure = handStructure,
            isMenzen = context.isMenzen,
        )

        // 處理七對子與一杯口、兩杯口的衝突
        // 兩杯口 (3 han) > 七對子 (2 han) > 一杯口 (1 han)
        when {
            ryanpeikou != null -> standardResults.add(ryanpeikou)
            chiitoitsu != null -> standardResults.add(chiitoitsu)
            iipeikou != null -> standardResults.add(iipeikou)
        }

        // 計算平和
        calculatePinfu(
            handStructure = handStructure,
            isMenzen = context.isMenzen,
            roundWind = context.roundWind,
            seatWind = context.seatWind,
        )?.let { standardResults.add(it) }

        // 計算對對胡
        calculateToitoi(
            handStructure = handStructure,
        )?.let { standardResults.add(it) }

        // 計算三暗刻
        calculateSanankou(
            handStructure = handStructure,
        )?.let { standardResults.add(it) }

        // 計算三杠子
        calculateSankantsu(
            handStructure = handStructure,
        )?.let { standardResults.add(it) }

        // 計算三色同刻
        calculateSanshokuDokoku(
            handStructure = handStructure,
        )?.let { standardResults.add(it) }

        // 計算三色同順
        calculateSanshokuDoujun(
            handStructure = handStructure,
            isMenzen = context.isMenzen,
        )?.let { standardResults.add(it) }

        // 計算混老頭
        calculateHonroutou(
            hand = context.hand,
            winningTile = context.winningTile,
        )?.let { standardResults.add(it) }

        // 計算混全帶么九
        calculateHonchan(
            handStructure = handStructure,
            isMenzen = context.isMenzen,
        )?.let { standardResults.add(it) }

        // 計算純全帶么九
        calculateJunchan(
            handStructure = handStructure,
            isMenzen = context.isMenzen,
        )?.let { standardResults.add(it) }

        results.addAll(standardResults)
    }

    /**
     * 計算字牌役（場風、自風、役牌）。
     */
    private fun calculateHonorYaku(
        context: RiichiHandValueContext,
        fuuro: List<Fuuro>,
        results: MutableList<YakuResult>,
    ) {
        // 取得所有牌（去除赤寶牌標記）
        val handTiles = context.hand.standingTiles.map { it.tile.riichiCanonical }

        // 計算字牌役
        val honorResults = calculateHonorYaku(
            handTiles = handTiles,
            fuuro = fuuro,
            roundWind = context.roundWind,
            seatWind = context.seatWind,
        )

        results.addAll(honorResults)
    }

    /**
     * 計算特殊役（立直、一發、嶺上花等）。
     */
    private fun calculateSpecialYaku(context: RiichiHandValueContext, results: MutableList<YakuResult>) {
        // 立直 和 雙立直
        if (context.isMenzen) {
            if (context.isDoubleRiichi) { // 雙立直
                results.add(YakuResult.han(YakuType.DoubleRiichi, 2))
            } else if (context.isRiichi) { // 立直
                results.add(YakuResult.han(YakuType.Riichi, 1))
            }
        }

        // 一發
        if (context.isMenzen && context.isIppatsu) {
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

        // 門前清自摸
        if (context.isMenzen && context.isTsumo) {
            results.add(YakuResult.han(YakuType.Menzentsumo, 1))
        }
    }

    /**
     * 計算役滿。
     *
     * 可能會含有小三元 (非役滿)
     */
    private fun calculateYakuman(
        context: RiichiHandValueContext,
        handStructure: HandStructure,
        results: MutableList<YakuResult>,
    ) {
        // 計算國士無雙
        calculateKokushiMusou(
            handStructure = handStructure,
            winningTile = context.winningTile,
        )?.let { results.add(it) }

        // 計算九蓮寶燈
        calculateChurenPoto(
            hand = context.hand,
            winningTile = context.winningTile,
            handStructure = handStructure,
            isMenzen = context.isMenzen,
        )?.let { results.add(it) }

        // 計算字一色
        calculateTsuuiisou(
            hand = context.hand,
            winningTile = context.winningTile,
        )?.let { results.add(it) }

        // 計算綠一色
        calculateRyuuuiisou(
            hand = context.hand,
            winningTile = context.winningTile,
        )?.let { results.add(it) }

        // 計算四暗刻
        calculateSuuankou(
            handStructure = handStructure,
            isMenzen = context.isMenzen,
            isTsumo = context.isTsumo,
        )?.let { results.add(it) }

        // 計算四杠子
        calculateSukantsu(
            handStructure = handStructure,
        )?.let { results.add(it) }

        // 計算四喜
        calculateSuushii(
            handStructure = handStructure,
        )?.let { results.add(it) }

        // 計算三元
        calculateSangaen(
            handStructure = handStructure,
        )?.let { results.add(it) }

        // 計算清老頭
        calculateChinroutou(
            hand = context.hand,
            winningTile = context.winningTile,
        )?.let { results.add(it) }

        // 計算天和與地和
        if (context.isFirstTurn && context.isTsumo) {
            // 天和：親（莊家）在第一巡自摸
            if (context.seatWind == context.roundWind) {
                results.add(YakuResult.yakuman(YakuType.Tenhou))
            }
            // 地和：子在第一巡自摸
            else {
                results.add(YakuResult.yakuman(YakuType.Chiihou))
            }
        }
    }
}
