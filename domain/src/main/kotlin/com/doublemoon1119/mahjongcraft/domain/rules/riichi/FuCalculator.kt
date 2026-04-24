package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.judgment.HandValueContext
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.CompletionType
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.Mentsu
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.RiichiYakuContext
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.standard.calculatePinfu
import com.doublemoon1119.mahjongcraft.domain.table.Wind
import com.doublemoon1119.mahjongcraft.domain.util.isHonor
import com.doublemoon1119.mahjongcraft.domain.util.isTerminal
import com.doublemoon1119.mahjongcraft.domain.util.withoutRed

/**
 * 日本麻將符數計算機。
 *
 * 符數計算規則（根據維基百科）：
 * 1. 符底：20符
 * 2. 門前清榮和加符：10符
 * 3. 自摸符：2符
 * 4. 聽牌型符：嵌張/邊張/單騎 +2符，兩面聽 +0符
 * 5. 面子和雀頭加符：
 *
 *    ||| 中張牌 | 么九牌/客風牌 | 自風牌/場風牌/三元牌 |
 *    | :---: | :---: | :---: | :---: | :---: |
 *    | 對子 | 雀頭 | 0符 | 0符 | 2符 |
 *    | 刻子 | 明刻 | 2符 | 4符 | 4符 |
 *    |     | 暗刻 | 4符 | 8符 | 8符 |
 *    | 槓子 | 明槓 | 8符 | 16符 | 16符 |
 *    |     | 暗槓 | 16符 | 32符 | 32符 |
 *    | 順子 |     | 0符 | 0符 | 0符 |
 *
 * 6. 特殊牌型：七對子固定 25符，門前平和自摸固定 20符、副露平和型的榮和固定 30符
 * 7. 合計後向上進到 10 的倍數
 */
object FuCalculator {

    /**
     * 計算總符數。
     *
     * @param context 役種上下文資訊。
     * @param handStructure 手牌結構。
     * @return 總符數。
     */
    fun calculateTotalFu(context: RiichiYakuContext, handStructure: HandStructure): Int {
        return when (handStructure) {
            is HandStructure.Standard -> calculateFuForStandard(context, handStructure)
            is HandStructure.Chiitoitsu -> 25  // 特殊牌型：七對子固定 25符
            is HandStructure.KokushiMusou -> 0
        }
    }

    /**
     * 計算標準手牌的符數。
     */
    private fun calculateFuForStandard(context: RiichiYakuContext, structure: HandStructure.Standard): Int {
        // 特殊牌型：門前平和自摸固定 20符
        if (isMenzenPinfuTsumo(context, structure)) {
            return 20
        }

        // 特殊牌型：副露平和型的榮和固定 30符
        if (isFuuroPinfuRon(context, structure)) {
            return 30
        }

        // 1. 符底 (20符)
        var fu = 20

        // 2. 門前清榮和加符 (10符)
        if (context.isMenzen && !context.isTsumo) {
            fu += 10
        }

        // 3. 自摸符 (2符)
        if (context.isTsumo) {
            fu += 2
        }

        // 4. 聽牌型符 (嵌張/邊張/單騎 +2符，其餘 +0符)
        fu += when (structure.completionType) {
            is CompletionType.Tanki,
            is CompletionType.Kanchan,
            is CompletionType.Penchan -> 2

            else -> 0
        }

        // 5. 面子和雀頭符
        fu += calculateMentsuAndJantoFu(context, structure)

        // 向上進到 10 的倍數
        return ceilToTen(fu)
    }

    /***
     * 是否為門前平和自摸
     */
    private fun isMenzenPinfuTsumo(context: RiichiYakuContext, structure: HandStructure.Standard): Boolean {
        if (context.isMenzen && context.isTsumo){
            val pinfu = calculatePinfu(
                handStructure = structure,
                isMenzen = context.isMenzen,
                roundWind = context.roundWind,
                seatWind = context.seatWind,
            )
            if (pinfu != null) {
                return true
            }
        }
        return false
    }

    /***
     * 是否為副露平和型的榮和
     */
    private fun isFuuroPinfuRon(context: RiichiYakuContext, structure: HandStructure.Standard): Boolean {
        if (!context.isMenzen && !context.isTsumo){
            val pinfu = calculatePinfu(
                handStructure = structure,
                isMenzen = true,  // 這裡強制丟 true 讓 calculatePinfu 可以進行判斷
                roundWind = context.roundWind,
                seatWind = context.seatWind,
            )
            if (pinfu != null) {
                return true
            }
        }
        return false
    }

    /**
     * 計算所有面子+雀頭的符數。
     */
    private fun calculateMentsuAndJantoFu(
        context: HandValueContext,
        structure: HandStructure.Standard
    ): Int {
        // 三元牌
        val dragonTiles = listOf(
            Tile.Honor.Red,
            Tile.Honor.Green,
            Tile.Honor.White
        )

        // 客風牌
        val kazeTiles = mapOf(
            Wind.EAST to Tile.Honor.East,
            Wind.SOUTH to Tile.Honor.South,
            Wind.WEST to Tile.Honor.West,
            Wind.NORTH to Tile.Honor.North
        ).filterKeys { wind ->
            wind != context.roundWind && wind != context.seatWind  // 不屬於場風牌和自風牌，視為客風牌
        }.values.toList()

        // 副露的符數
        val fuuroFu = structure.fuuro.sumOf { fuuro ->
            val mentsu = fuuro.mentsu
            val tile = mentsu.tiles.first().withoutRed

            // 是否為么九牌
            val isTerminal = tile.isTerminal

            // 是否為三元牌
            val isDragon = tile in dragonTiles

            // 是否為客風牌
            val isKaze = tile in kazeTiles

            // 是否為自風牌或者場風牌
            val isSeatOrRoundWind = tile.isHonor && !isDragon && !isKaze

            when (mentsu) {
                is Mentsu.Minkan,
                is Mentsu.Kakan -> when {
                    isSeatOrRoundWind || isDragon -> 16
                    isTerminal || isKaze -> 16
                    else -> 8
                }

                is Mentsu.Ankan -> when {
                    isSeatOrRoundWind || isDragon -> 32
                    isTerminal || isKaze -> 32
                    else -> 16
                }
                // 明刻
                is Mentsu.Kotsu -> when {
                    isSeatOrRoundWind || isDragon -> 4
                    isTerminal || isKaze -> 4
                    else -> 2
                }

                is Mentsu.Shuntsu -> 0
            }
        }

        // 手牌中面子的符數
        val handFu = structure.mentsus.sumOf { mentsu ->
            val tile = mentsu.tiles.first().withoutRed

            // 是否為么九牌
            val isTerminal = tile.isTerminal

            // 是否為三元牌
            val isDragon = tile in dragonTiles

            // 是否為客風牌
            val isKaze = tile in kazeTiles

            // 是否為自風牌或者場風牌
            val isSeatOrRoundWind = tile.isHonor && !isDragon && !isKaze

            when (mentsu) {
                // 暗刻
                is Mentsu.Kotsu -> when {
                    isSeatOrRoundWind || isDragon -> 8
                    isTerminal || isKaze -> 8
                    else -> 4
                }
                // 手牌當中的面子只有暗刻，其他都視為 0 符
                else -> 0
            }
        }

        // 雀頭符數
        val jantoTile = structure.pair.tile.withoutRed
        val isJantoDragon = jantoTile in dragonTiles
        val isJantoKaze = jantoTile in kazeTiles
        val isJantoSeatOrRoundWind = jantoTile.isHonor && !isJantoDragon && !isJantoKaze
        val jantoFu = if (isJantoSeatOrRoundWind || isJantoDragon) 2 else 0

        // 回傳總和
        return fuuroFu + handFu + jantoFu
    }

    /**
     * 向上進到 10 的倍數。
     */
    private fun ceilToTen(value: Int): Int {
        return if (value % 10 == 0) value else (value / 10 + 1) * 10
    }
}