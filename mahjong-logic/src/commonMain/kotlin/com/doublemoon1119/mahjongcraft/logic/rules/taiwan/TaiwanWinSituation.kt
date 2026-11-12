package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiType

/**
 * 胡牌時由規則流程確認的情境，不由牌型計算器推測行動經過。
 *
 * @property isRobbingKong 是否搶加槓。
 * @property isReplacementWin 是否為允許胡牌的補牌自摸。
 * @property isLastDraw 是否為最後可摸牌的自摸。
 * @property openingWin 本次成立的天胡、地胡或人胡；一般胡牌為null。
 * @property readyQualification 尚有效的宣告聽牌計台項目：一般宣告聽牌、天聽或地聽；沒有宣告為null。
 */
data class TaiwanWinSituation(
    val isRobbingKong: Boolean = false,
    val isReplacementWin: Boolean = false,
    val isLastDraw: Boolean = false,
    val openingWin: TaiwanTaiType? = null,
    val readyQualification: TaiwanTaiType? = null,
) {
    init {
        require(openingWin == null || openingWin in setOf(TaiwanTaiType.HeavenlyWin, TaiwanTaiType.EarthlyWin, TaiwanTaiType.HumanWin)) {
            "Opening win must be a heavenly, earthly or human win"
        }
        require(readyQualification == null || readyQualification in TaiwanReadyKind.entries.map { it.taiType }) {
            "Ready qualification must be a declared, heavenly or earthly ready"
        }
    }
}
