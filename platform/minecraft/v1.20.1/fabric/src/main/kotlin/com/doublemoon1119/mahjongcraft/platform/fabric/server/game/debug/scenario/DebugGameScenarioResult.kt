package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import kotlin.uuid.Uuid

/**
 * 一次完整 debug 情境的權威狀態及一次性牌牆呈現資料。
 *
 * @property game 應原子寫入 repository 的新遊戲。
 * @property wallStructure 新桌況所有牌的實體牌牆結構座標。
 * @property wallLayout 所有實體牌生成時採用的權威最終 placement。
 * @property presentation 本次載入後重建桌面的呈現方式。
 */
data class DebugGameScenarioResult(
    val game: Game,
    val wallStructure: Map<Uuid, TileWallPosition>,
    val wallLayout: TileWallPhysicalLayout,
    val presentation: DebugGameScenarioPresentation = DebugGameScenarioPresentation.StaticWall,
)
