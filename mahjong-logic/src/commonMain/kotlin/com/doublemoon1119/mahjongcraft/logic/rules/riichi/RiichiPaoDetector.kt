package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.HonorSet
import com.doublemoon1119.mahjongcraft.logic.judgment.HonorSetCompletionDetector

/**
 * 包牌（責任払い）觸發判定器。
 *
 * 負責判斷「碰或明槓三元牌／風牌」這個動作，是否正是大三元的第 3 組副露或大四喜的第 4 組副露，進而觸發
 * 包牌責任；是否補齊整套字牌副露由 [HonorSetCompletionDetector] 判斷。
 *
 * 此判定必須在該次碰／明槓「實際套用到手牌之前」呼叫，以取得鳴牌當下、
 * 尚未加入新副露的手牌狀態；加槓（[MeldType.AddedKan]）
 * 與暗槓（[MeldType.ClosedKan]）不會產生新的包牌責任，
 * 不需要呼叫此判定器：
 * - 加槓沿用原本碰的來源方位，不會新增責任。
 * - 暗槓沒有鳴牌來源，不構成包牌。
 */
object RiichiPaoDetector {
    /**
     * 檢查本次碰／明槓是否觸發包牌責任。
     *
     * @param hand 碰／明槓「之前」的手牌狀態。
     * @param calledTile 本次鳴取的牌。若非三元牌或風牌，一律回傳 null。
     * @param sourceDirection 本次鳴取的來源相對方位。
     * @return 若此次鳴牌觸發包牌責任，回傳對應的 [RiichiPaoLiability]；否則回傳 null。
     */
    fun check(hand: Hand, calledTile: Tile, sourceDirection: RelativeDirection): RiichiPaoLiability? {
        val yaku = when (HonorSetCompletionDetector.completedBy(hand, calledTile)) {
            HonorSet.DRAGONS -> RiichiPaoYaku.Daisangen
            HonorSet.WINDS -> RiichiPaoYaku.Daisuushii
            null -> return null
        }
        return RiichiPaoLiability(yaku, sourceDirection)
    }
}
