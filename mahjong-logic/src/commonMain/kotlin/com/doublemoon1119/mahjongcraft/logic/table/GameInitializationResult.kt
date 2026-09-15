package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import kotlin.uuid.Uuid

/**
 * [GameInitializer.initialize]／[GameInitializer.startNextRound] 的結果。
 *
 * 除了權威 [tableState] 之外，額外攜帶只有平台呈現層需要、不該進入 [TableState]／
 * persistence／network DTO 的一次性資料——這些資料只在牌局剛初始化的那個當下存在，呼叫端用完即可
 * 丟棄，不需要另外保存。
 *
 * @property tableState 已完成洗牌、（若規則支援）擲骰開門、發牌、分數初始化的新權威桌況。
 * @property diceRoll 本次開門使用的權威擲骰個別點數；規則不支援開門流程時為 `null`。
 * @property wallStructure 本次牌牆所有牌（含活牌與王牌）的面／墩／層結構座標，鍵為
 *                         [IdentifiedTile.id]；規則不支援開門流程時為 `null`。
 * @property initialPhysicalWallLayout 本次完整初始牌牆的抽象實體布局；包含之後會發到手牌的牌張，僅供
 * 初始化 presentation 使用。規則不支援牌牆布局時為 null。
 */
data class GameInitializationResult(
    val tableState: TableState,
    val diceRoll: DiceRollResult?,
    val wallStructure: Map<Uuid, TileWallPosition>?,
    val initialPhysicalWallLayout: TileWallPhysicalLayout?,
)
