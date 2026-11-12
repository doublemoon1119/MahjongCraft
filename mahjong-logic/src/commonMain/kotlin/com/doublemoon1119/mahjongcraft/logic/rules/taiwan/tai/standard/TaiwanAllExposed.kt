package com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.standard

import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiResult
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiType

/**
 * 全求人：五組面子都已副露且沒有暗槓，單吊胡別人打出的牌。
 *
 * @param isAllExposed 是否五組面子都已副露且沒有暗槓。
 * @param isTsumo 是否自摸。
 * @return 成立時為兩台。
 */
fun calculateTaiwanAllExposed(isAllExposed: Boolean, isTsumo: Boolean): TaiwanTaiResult? = TaiwanTaiResult(TaiwanTaiType.AllExposed, 2).takeIf { isAllExposed && !isTsumo }
