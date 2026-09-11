package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import kotlin.uuid.Uuid

/** 建立 development-only 權威對局情境的受控輸入。 */
data class DebugGameScenarioContext(
    /** 目前同桌的權威遊戲。 */
    val currentGame: Game,
    /** 執行載入指令的同桌玩家。 */
    val invokingPlayerId: Uuid,
)

/** 一次完整 debug 情境的權威狀態及一次性牌牆呈現資料。 */
data class DebugGameScenarioResult(
    /** 應原子寫入 repository 的新遊戲。 */
    val game: Game,
    /** 新桌況所有牌的實體牌牆結構座標。 */
    val wallStructure: Map<Uuid, TileWallPosition>,
)

/** 可重複建立完整權威桌況的 development-only 情境。 */
interface DebugGameScenario {
    /** 穩定且完整的 namespaced scenario ID。 */
    val id: String

    /** 依目前同桌玩家建立新的權威情境。 */
    fun build(context: DebugGameScenarioContext): DebugGameScenarioResult
}
