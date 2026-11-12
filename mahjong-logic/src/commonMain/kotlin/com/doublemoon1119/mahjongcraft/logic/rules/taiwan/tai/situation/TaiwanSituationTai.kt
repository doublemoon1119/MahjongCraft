package com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.situation

import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanWinSituation
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiResult
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiType

/**
 * 計算與胡牌方式及時機有關的台：門清、自摸、不求人、獨聽、搶槓、槓上開花、海底撈月、天胡、地胡、人胡、
 * 報聽、天聽與地聽。
 *
 * 天胡、地胡、人胡成立時不再計報聽類的資格；天胡不另計獨聽與槓上開花，天胡與地胡不另計不求人。
 *
 * @param isMenzen 是否門清。
 * @param isTsumo 是否自摸。
 * @param situation 胡牌時的情境。
 * @param waitCount 胡牌前整手可等的牌面種類數。
 * @param isSingleShape 胡牌的面子形狀是否為單吊、中洞或邊張。
 * @param isAllExposed 是否五組面子都已副露且沒有暗槓。
 * @return 成立的台。
 */
fun calculateTaiwanSituationTai(
    isMenzen: Boolean,
    isTsumo: Boolean,
    situation: TaiwanWinSituation,
    waitCount: Int,
    isSingleShape: Boolean,
    isAllExposed: Boolean,
): List<TaiwanTaiResult> {
    val results = mutableListOf<TaiwanTaiResult>()
    val opening = situation.openingWin
    if (isMenzen && opening == null) results += TaiwanTaiResult(TaiwanTaiType.ConcealedHand, 1)
    if (isTsumo) results += TaiwanTaiResult(TaiwanTaiType.SelfDraw, 1)
    if (isMenzen && isTsumo && opening != TaiwanTaiType.HeavenlyWin && opening != TaiwanTaiType.EarthlyWin) {
        results += TaiwanTaiResult(TaiwanTaiType.FullyConcealedHand, 1)
    }
    if (waitCount == 1 && isSingleShape && !isAllExposed && opening != TaiwanTaiType.HeavenlyWin) {
        results += TaiwanTaiResult(TaiwanTaiType.SingleWait, 1)
    }
    if (situation.isRobbingKong) results += TaiwanTaiResult(TaiwanTaiType.RobbingKong, 1)
    if (situation.isReplacementWin && opening != TaiwanTaiType.HeavenlyWin) results += TaiwanTaiResult(TaiwanTaiType.ReplacementWin, 1)
    if (situation.isLastDraw) results += TaiwanTaiResult(TaiwanTaiType.LastDraw, 1)
    val timing = opening ?: situation.readyQualification
    if (timing != null) results += TaiwanTaiResult(timing, timingTai(timing))
    return results
}

/**
 * 天胡、地胡、人胡與報聽類資格的台數。
 *
 * @param type 天胡、地胡、人胡、報聽、天聽或地聽。
 * @return 台數。
 */
private fun timingTai(type: TaiwanTaiType): Int = when (type) {
    TaiwanTaiType.HeavenlyWin -> 24
    TaiwanTaiType.EarthlyWin -> 16
    TaiwanTaiType.HumanWin -> 16
    TaiwanTaiType.DeclaredReady -> 1
    TaiwanTaiType.HeavenlyReady -> 16
    TaiwanTaiType.EarthlyReady -> 8
    else -> error("Not an opening win or ready qualification: $type")
}
