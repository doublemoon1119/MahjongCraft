package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

/**
 * 胡牌時由規則流程確認的情境，不由牌型計算器推測行動經過。
 *
 * @property isRobbingKong 是否搶加槓。
 * @property isReplacementWin 是否為允許胡牌的補牌自摸。
 * @property isLastDraw 是否為最後可摸牌的自摸。
 * @property openingWin 本次成立的天胡、地胡或人胡；一般胡牌為null。
 * @property readyQualification 尚有效的天聽或地聽；沒有資格為null。
 */
data class TaiwanWinSituation(
    val isRobbingKong: Boolean = false,
    val isReplacementWin: Boolean = false,
    val isLastDraw: Boolean = false,
    val openingWin: TaiwanTaiType? = null,
    val readyQualification: TaiwanTaiType? = null,
) {
    init {
        require(openingWin == null || openingWin in setOf(TaiwanTaiType.HEAVENLY_WIN, TaiwanTaiType.EARTHLY_WIN, TaiwanTaiType.HUMAN_WIN)) {
            "Opening win must be a heavenly, earthly or human win"
        }
        require(readyQualification == null || readyQualification in setOf(TaiwanTaiType.HEAVENLY_READY, TaiwanTaiType.EARTHLY_READY)) {
            "Ready qualification must be heavenly or earthly ready"
        }
    }
}
