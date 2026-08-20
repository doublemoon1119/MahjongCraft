package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.riichiCanonical

/**
 * 包牌（責任払い）觸發判定器。
 *
 * 負責判斷「碰或明槓三元牌／風牌」這個動作，是否讓玩家湊齊大三元（三組三元牌）
 * 或大四喜（四組風牌）所需的最後一組面子，進而觸發包牌責任。
 *
 * 此判定必須在該次碰／明槓「實際套用到手牌之前」呼叫，以取得鳴牌當下、
 * 尚未加入新副露的手牌狀態；加槓（[MeldType.ADDED_KAN]）
 * 與暗槓（[MeldType.CLOSED_KAN]）不會產生新的包牌責任，
 * 不需要呼叫此判定器：
 * - 加槓沿用原本碰的來源方位，不會新增責任。
 * - 暗槓沒有鳴牌來源，不構成包牌。
 */
object PaoDetector {

    private val dragons: Set<Tile> = setOf(Tile.Honor.Red, Tile.Honor.Green, Tile.Honor.White)
    private val winds: Set<Tile> = setOf(Tile.Honor.East, Tile.Honor.South, Tile.Honor.West, Tile.Honor.North)

    /**
     * 檢查本次碰／明槓是否觸發包牌責任。
     *
     * @param hand 碰／明槓「之前」的手牌狀態。
     * @param calledTile 本次鳴取的牌。若非三元牌或風牌，一律回傳 null。
     * @param sourceDirection 本次鳴取的來源相對方位。
     * @return 若此次鳴牌觸發包牌責任，回傳對應的 [PaoLiability]；否則回傳 null。
     */
    fun check(hand: Hand, calledTile: Tile, sourceDirection: RelativeDirection): PaoLiability? {
        val baseTile = calledTile.riichiCanonical
        return when (baseTile) {
            in dragons -> checkGroup(hand, baseTile, dragons, PaoYaku.Daisangen, sourceDirection)
            in winds -> checkGroup(hand, baseTile, winds, PaoYaku.Daisuushii, sourceDirection)
            else -> null
        }
    }

    /**
     * 檢查扣除 [calledTile] 後，[group] 中其餘的牌是否都已經湊齊面子
     * （不論是已經副露的面子，或是手牌中已有 3 張以上、足以形成暗刻的立牌）。
     *
     * 若成立，代表這次鳴牌正是湊齊大三元／大四喜的最後一組，須成立包牌責任。
     */
    private fun checkGroup(
        hand: Hand,
        calledTile: Tile,
        group: Set<Tile>,
        yaku: PaoYaku,
        sourceDirection: RelativeDirection,
    ): PaoLiability? {
        val otherTiles = group - calledTile

        val exposedGroupTiles = hand.exposedMelds
            .mapNotNull { it.tiles.firstOrNull()?.tile?.riichiCanonical }
            .toSet()

        val standingCounts = hand.standingTiles
            .map { it.tile.riichiCanonical }
            .groupingBy { it }
            .eachCount()

        val othersAlreadyFormed = otherTiles.all { tile ->
            tile in exposedGroupTiles || (standingCounts[tile] ?: 0) >= 3
        }

        return if (othersAlreadyFormed) PaoLiability(yaku, sourceDirection) else null
    }
}
