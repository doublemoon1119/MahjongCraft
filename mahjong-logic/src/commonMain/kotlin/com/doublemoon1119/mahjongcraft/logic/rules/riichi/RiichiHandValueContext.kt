package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueContext
import com.doublemoon1119.mahjongcraft.logic.table.Wind

/**
 * 立直麻將手牌價值計算所需的上下文資訊。
 *
 * 包含手牌資訊、遊戲狀態、環境資訊等，用於各役種的檢測計算。正式結算與局面評估都經由 [forPlayer] 建立，
 * 規則設定與玩家狀態帶入的欄位因此不會因呼叫端而不同。
 *
 * @property hand 玩家手牌（包含立牌與副露）。
 * @property winningTile 胡牌張（放銃或自摸的牌）。
 * @property isTsumo 是否為自摸。
 * @property isMenzen 是否有門前清（無副露）。
 * @property roundWind 圈風。
 * @property seatWind 自風。
 * @property isDealer 胡牌者是否為本局權威莊家。
 * @property isRiichi 是否已宣告立直。
 * @property isDoubleRiichi 是否為兩立直（雙立直）。
 * @property isIppatsu 是否為一發。
 * @property allowOpenTanyao 是否允許食斷（斷么九鳴牌有效）。
 * @property doraIndicators 寶牌指示牌列表。
 * @property uraDoraIndicators 裏寶牌指示牌列表（立直時）。
 * @property isLastDraw 是否為海底撈月。
 * @property isLastDiscard 是否為河底撈魚。
 * @property isRobbingKan 是否為搶槓。
 * @property isRinshanKaihou 是否為嶺上花。
 * @property isFirstTurn 是否為第一巡。用於天和、地和與古役人和的判定。
 * @property isRiichiDeclarationDiscard 榮和的牌是否為放銃者的立直宣言牌；用於古役燕返。
 * @property isDiscardAfterKan 榮和的牌是否為放銃者槓牌並補牌後打出的牌；用於古役槓振。
 * @property paoLiability 本局是否已成立包牌責任（大三元／大四喜），若無則為 null。
 * @property nukiDoraTiles 三人麻將中和牌者拔出的北；每張算一張拔北寶牌，也計入寶牌與裏寶牌。
 * @property usesThreePlayerTiles 是否使用沒有二～八萬的三人麻將牌組；決定一萬與九萬的寶牌指示。
 */
data class RiichiHandValueContext(
    override val hand: Hand,
    override val winningTile: Tile,
    override val isTsumo: Boolean,
    override val isMenzen: Boolean,
    override val roundWind: Wind,
    override val seatWind: Wind,
    val isDealer: Boolean,
    val isRiichi: Boolean,
    val isDoubleRiichi: Boolean,
    val isIppatsu: Boolean,
    val allowOpenTanyao: Boolean,
    val doraIndicators: List<Tile>,
    val uraDoraIndicators: List<Tile>,
    val isLastDraw: Boolean,
    val isLastDiscard: Boolean,
    val isRobbingKan: Boolean,
    val isRinshanKaihou: Boolean,
    val isFirstTurn: Boolean,
    val isRiichiDeclarationDiscard: Boolean,
    val isDiscardAfterKan: Boolean,
    val paoLiability: PaoLiability?,
    val nukiDoraTiles: List<Tile>,
    val usesThreePlayerTiles: Boolean,
) : HandValueContext {
    /** [RiichiHandValueContext] 的建立方式。 */
    companion object {
        /**
         * 依規則設定 [config] 與和牌者的 [riichiState] 建立手牌價值上下文。
         *
         * 規則設定（食斷、三人牌組）與玩家狀態（立直、兩立直、拔出的北、包牌責任）一律在這裡帶入；只有正式結算才知道的
         * 和牌情境（一發、裏寶牌、海底、河底、搶槓、嶺上、第一巡、燕返、槓振）一律為否或空，由正式結算另外補上。
         *
         * @param hand 不含和牌張的手牌；門前清依副露是否全為暗槓判定。
         * @param doraIndicators 和牌者看得到的寶牌指示牌。
         */
        fun forPlayer(
            config: RiichiFamilyRuleConfig,
            riichiState: RiichiPlayerState,
            hand: Hand,
            winningTile: Tile,
            isTsumo: Boolean,
            roundWind: Wind,
            seatWind: Wind,
            isDealer: Boolean,
            doraIndicators: List<Tile>,
        ): RiichiHandValueContext = RiichiHandValueContext(
            hand = hand,
            winningTile = winningTile,
            isTsumo = isTsumo,
            isMenzen = hand.melds.all { it.type == MeldType.CLOSED_KAN },
            roundWind = roundWind,
            seatWind = seatWind,
            isDealer = isDealer,
            isRiichi = riichiState.isRiichi,
            isDoubleRiichi = riichiState.isDoubleRiichi,
            isIppatsu = false,
            allowOpenTanyao = config.allowOpenTanyao,
            doraIndicators = doraIndicators,
            uraDoraIndicators = emptyList(),
            isLastDraw = false,
            isLastDiscard = false,
            isRobbingKan = false,
            isRinshanKaihou = false,
            isFirstTurn = false,
            isRiichiDeclarationDiscard = false,
            isDiscardAfterKan = false,
            paoLiability = riichiState.paoLiability,
            nukiDoraTiles = riichiState.nukiDoraTiles.map { it.tile },
            usesThreePlayerTiles = config.usesThreePlayerTiles,
        )
    }
}
