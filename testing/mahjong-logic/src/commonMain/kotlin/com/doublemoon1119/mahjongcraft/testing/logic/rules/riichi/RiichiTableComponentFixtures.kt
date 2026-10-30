package com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiTableComponents
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiWallProgress

/**
 * 明確建立牌型測試需要的日麻桌況組件，不執行規則或建立另一套判定。
 *
 * @param potPoints 場上供託的實際點數。
 * @param wallProgress 本局補牌與指示牌進度。
 * @return 只有指定日麻桌況資料的組件集合。
 */
fun riichiTableComponents(
    potPoints: Int = 0,
    wallProgress: RiichiWallProgress = RiichiWallProgress(),
): ComponentSet = ComponentSet.EMPTY
    .with(RiichiTableComponents.POT_POINTS, potPoints)
    .with(RiichiTableComponents.WALL_PROGRESS, wallProgress)
