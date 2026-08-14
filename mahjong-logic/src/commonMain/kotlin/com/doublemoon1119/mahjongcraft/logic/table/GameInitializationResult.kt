package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import kotlin.uuid.Uuid

/**
 * [GameInitializer.initialize]／[GameInitializer.startNextRound] 的結果。
 *
 * 除了權威 [tableState] 之外，額外攜帶只有 Minecraft 等平台呈現層需要、不該進入 [TableState]／
 * persistence／network DTO 的一次性資料——這些資料只在牌局剛初始化的那個當下存在，呼叫端用完即可
 * 丟棄，不需要另外保存。
 *
 * @property tableState 已完成洗牌、（若規則支援）擲骰開門、發牌、分數初始化的新權威桌況。
 * @property diceRoll 本次開門使用的權威擲骰個別點數；規則不支援開門流程時為 `null`。
 * @property wallStructure 本次牌牆所有牌（含活牌與王牌）的面／墩／層結構座標，鍵為
 * [com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile.id]；規則不支援開門流程時為 `null`。
 */
data class GameInitializationResult(
    val tableState: TableState,
    val diceRoll: DiceRollResult?,
    val wallStructure: Map<Uuid, TileWallPosition>?,
)
