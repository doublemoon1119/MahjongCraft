package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueContext
import com.doublemoon1119.mahjongcraft.logic.table.Wind

/**
 * 立直麻將手牌價值計算所需的上下文資訊。
 *
 * 包含手牌資訊、遊戲狀態、環境資訊等，用於各役種的檢測計算。
 *
 * @property hand 玩家手牌（包含立牌與副露）。
 * @property winningTile 胡牌張（放銃或自摸的牌）。
 * @property isTsumo 是否為自摸。
 * @property isMenzen 是否有門前清（無副露）。
 * @property roundWind 圈風。
 * @property seatWind 自風。
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
 * @property isFirstTurn 是否為第一巡。用於天和、地和的判定。
 */
data class RiichiHandValueContext(
    override val hand: Hand,
    override val winningTile: Tile,
    override val isTsumo: Boolean,
    override val isMenzen: Boolean = true,
    override val roundWind: Wind = Wind.EAST,
    override val seatWind: Wind = Wind.EAST,
    val isRiichi: Boolean = false,
    val isDoubleRiichi: Boolean = false,
    val isIppatsu: Boolean = false,
    val allowOpenTanyao: Boolean = true,
    val doraIndicators: List<Tile> = emptyList(),
    val uraDoraIndicators: List<Tile> = emptyList(),
    val isLastDraw: Boolean = false,
    val isLastDiscard: Boolean = false,
    val isRobbingKan: Boolean = false,
    val isRinshanKaihou: Boolean = false,
    val isFirstTurn: Boolean = false
) : HandValueContext