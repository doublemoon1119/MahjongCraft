package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.component.ComponentPatch
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.module.PointPotCollection
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 取得已由正式建立流程產生的供託點數。
 *
 * @return 目前供託點數。
 * @throws IllegalArgumentException 桌況缺少必要的供託組件時。
 */
fun TableState.riichiPotPoints(): Int = requireNotNull(components[RiichiTableComponents.POT_POINTS]) {
    "Riichi table is missing its pot points component"
}

/**
 * 取得已由正式建立流程產生的本局牌山進度。
 *
 * @return 本局的補牌與指示牌公開進度。
 * @throws IllegalArgumentException 桌況缺少必要的牌山進度組件時。
 */
fun TableState.riichiWallProgress(): RiichiWallProgress = requireNotNull(components[RiichiTableComponents.WALL_PROGRESS]) {
    "Riichi table is missing its wall progress component"
}

/**
 * 收下日麻供託，產生只將供託歸零的組件變更。
 *
 * @param tableState 收取前的權威桌況。
 * @return 本次收取的實際點數與桌況變更。
 */
fun collectRiichiPot(tableState: TableState): PointPotCollection = PointPotCollection(
    points = tableState.riichiPotPoints(),
    tableComponents = ComponentPatch.basedOn(tableState.components, values = ComponentSet.EMPTY.with(RiichiTableComponents.POT_POINTS, 0)),
)
