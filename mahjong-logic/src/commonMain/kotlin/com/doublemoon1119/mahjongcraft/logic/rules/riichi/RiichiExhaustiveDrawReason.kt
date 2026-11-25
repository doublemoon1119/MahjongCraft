package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.table.PendingRobbingReaction
import com.doublemoon1119.mahjongcraft.logic.util.isHonor
import com.doublemoon1119.mahjongcraft.logic.util.isTerminal
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/**
 * 立直麻將和局原因。
 */
sealed class RiichiExhaustiveDrawReason : ExhaustiveDrawReason {

    /**
     * 一般流局：牌山摸盡且無人和牌。
     */
    data object Normal : RiichiExhaustiveDrawReason() {
        override val id: String = MahjongCraftMetadata.id("riichi/exhaustive_draw/normal")
    }

    /**
     * 九種九牌：玩家在第一巡摸牌後，手牌中有 9 種以上不同的么九牌（1、9 數牌與字牌）。
     */
    data object KyuushuKyuuhai : RiichiExhaustiveDrawReason() {
        override val id: String = MahjongCraftMetadata.id("riichi/exhaustive_draw/kyuushu_kyuuhai")

        /** 預覽手牌中觸發九種九牌資格的全部么九牌。 */
        override fun previewTiles(hand: Hand): List<Tile> = hand.tiles.map { it.tile }.filter { it.isTerminal || it.isHonor }
    }

    /**
     * 四風連打：第一巡中，四名玩家連續打出同一種風牌（東、南、西、北）。
     */
    data object SuufonRenda : RiichiExhaustiveDrawReason() {
        override val id: String = MahjongCraftMetadata.id("riichi/exhaustive_draw/suufon_renda")
    }

    /**
     * 四槓散了：全場玩家合計槓了 4 次，明槓、暗槓、加槓都算。
     *
     * 4 個槓都由同一個人槓出時不流局，因為該玩家可能正在做四槓子役滿。
     */
    data object SuukanNagare : RiichiExhaustiveDrawReason() {
        override val id: String = MahjongCraftMetadata.id("riichi/exhaustive_draw/suukan_nagare")
    }

    /**
     * 四家立直：四位玩家全部宣告立直。
     */
    data object SuuchaRiichi : RiichiExhaustiveDrawReason() {
        override val id: String = MahjongCraftMetadata.id("riichi/exhaustive_draw/suucha_riichi")
    }

    /**
     * 三家和了（トリロン）：同一張牌有多家可以榮和，且 [MultiRonPolicy] 對這個人數設定為流局
     * （[RonResolution.ABORTIVE_DRAW]）。
     *
     * - 這張牌可以是一般捨牌，也可以是被搶槓的牌（[PendingRobbingReaction]）。兩者共用同一套多響判定與這個原因。
     * - 一般規則的三家和了只指三家同時榮和。[RiichiRuleConfig.multiRonPolicy] 也允許把雙響設定為流局，
     *   這時同樣使用這個原因。
     * - 雙響與三響觸發時的結果相同：本局流局、莊家連莊、不結算點數。
     */
    data object SanchaHou : RiichiExhaustiveDrawReason() {
        override val id: String = MahjongCraftMetadata.id("riichi/exhaustive_draw/sancha_hou")
    }
}
