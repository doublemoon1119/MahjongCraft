package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import kotlin.uuid.Uuid

/**
 * [GameInitializer.initialize]／[GameInitializer.startNextRound] 的結果。
 *
 * 除了權威 [tableState] 之外，也攜帶本次初始化產生的牌牆結構座標與完整初始牌牆布局。
 *
 * @property tableState 已完成洗牌、（若規則支援）擲骰開門、發牌、分數初始化的新權威桌況。
 * @property wallStructure 本次牌牆所有牌（含活牌與王牌）的面／墩／層結構座標，鍵為
 * [IdentifiedTile.id]；規則不支援開門流程時為 `null`。
 * @property initialPhysicalWallLayout 本次完整初始牌牆的抽象實體布局，包含之後會發到手牌的牌張；規則
 * 不支援牌牆布局時為 null。
 */
data class GameInitializationResult(
    val tableState: TableState,
    val wallStructure: Map<Uuid, TileWallPosition>?,
    val initialPhysicalWallLayout: TileWallPhysicalLayout?,
)
