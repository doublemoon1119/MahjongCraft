package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 規則解析本局自動操作時可讀取的完整權威情境。
 *
 * @property tableState 目前權威桌況。
 * @property player 目前取得決策權的玩家。
 * @property legalActions 此次決策可用的特殊動作。
 * @property enabledControlIds 玩家目前啟用且受此規則支援的 control ID。
 */
data class AutomaticControlContext(
    val tableState: TableState,
    val player: MahjongPlayer,
    val legalActions: List<GameAction>,
    val enabledControlIds: Set<String>,
)
