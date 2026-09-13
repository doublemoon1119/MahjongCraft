package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import kotlin.uuid.Uuid

/** Debug 情境載入後採用的桌面呈現方式。 */
sealed interface DebugGameScenarioPresentation {
    /** 直接呈現權威目前桌況，不重播已經完成的歷史動畫。 */
    data object StaticWall : DebugGameScenarioPresentation

    /**
     * 使用正式開局資料播放牌牆、骰子與初次發牌的完整時間線。
     *
     * @property diceRoll 本次正式初始化產生的擲骰結果。
     * @property dealOrderHandTileIdsBySeatIndex 各座位依實際發牌先後排列的手牌 ID。
     * @property postFlipHandTileIdsBySeatIndex 各座位翻牌完成後依規則整理順序排列的手牌 ID。
     */
    data class InitialRound(
        val diceRoll: DiceRollResult,
        val dealOrderHandTileIdsBySeatIndex: Map<Int, List<Uuid>>,
        val postFlipHandTileIdsBySeatIndex: Map<Int, List<Uuid>>,
    ) : DebugGameScenarioPresentation
}

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
    /** 所有實體牌生成時採用的權威最終 placement。 */
    val wallLayout: TileWallPhysicalLayout,
    /** 本次載入後重建桌面的呈現方式。 */
    val presentation: DebugGameScenarioPresentation = DebugGameScenarioPresentation.StaticWall,
)

/** 可重複建立完整權威桌況的 development-only 情境。 */
interface DebugGameScenario {
    /** 穩定且完整的 namespaced scenario ID。 */
    val id: String

    /** 依目前同桌玩家建立新的權威情境。 */
    fun build(context: DebugGameScenarioContext): DebugGameScenarioResult
}
