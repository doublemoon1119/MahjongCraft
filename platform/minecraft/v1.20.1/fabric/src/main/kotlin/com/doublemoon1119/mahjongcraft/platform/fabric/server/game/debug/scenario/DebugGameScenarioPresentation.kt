package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

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
