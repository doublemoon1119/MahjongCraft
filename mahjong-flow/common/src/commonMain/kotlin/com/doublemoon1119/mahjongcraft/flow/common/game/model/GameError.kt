package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.flow.common.error.ApplicationError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlin.uuid.Uuid

/**
 * 與對局（Game）進行中操作相關的應用層錯誤定義。
 *
 * 涵蓋所有 Game 相關 use case 中可能出現的業務邏輯錯誤情境，供 [Outcome] 的錯誤型別使用。
 *
 * 後續鳴牌/立直/胡牌/流局等 use case 視需要再擴充此 sealed interface，不需要一次列完。
 */
sealed interface GameError : ApplicationError {

    /**
     * 找不到指定的對局。
     *
     * @param gameId 欲操作的對局 Uuid。
     */
    data class GameNotFound(val gameId: Uuid) : GameError

    /**
     * 目標玩家不在指定的對局內。
     *
     * @param playerId 目標玩家 Uuid。
     * @param gameId 對局 Uuid。
     */
    data class PlayerNotInGame(val playerId: Uuid, val gameId: Uuid) : GameError

    /**
     * 尚未輪到該玩家的回合。
     *
     * @param playerId 發起操作的玩家 Uuid。
     * @param gameId 對局 Uuid。
     */
    data class NotPlayersTurn(val playerId: Uuid, val gameId: Uuid) : GameError

    /**
     * 該動作在目前的桌況下不合法。
     *
     * @param playerId 發起操作的玩家 Uuid。
     * @param gameId 對局 Uuid。
     * @param action 欲執行的動作。
     */
    data class IllegalAction(val playerId: Uuid, val gameId: Uuid, val action: GameAction) : GameError

    /**
     * 牌山已摸盡。實際的流局判定交由後續的流局 use case 處理，此處僅回報現況。
     *
     * @param gameId 對局 Uuid。
     */
    data class WallExhausted(val gameId: Uuid) : GameError

    /**
     * 該對局採用的規則模組不支援目前嘗試的系統性動作（無對應玩家發起者，例如流局結算）。
     *
     * 刻意不像 [IllegalAction] 一樣攜帶具體的 [GameAction]——系統性動作觸發此錯誤的時間點，
     * 通常是規則模組的 `declare*` 系列鉤子直接回傳 null（例如此對局的規則根本不支援流局結算），
     * 呼叫端此時尚未能建構出一個有意義的 [GameAction] 具體實例（那正是問題所在），
     * 沒有 [GameAction] payload 也不影響呼叫端判斷錯誤類型。
     *
     * @param gameId 對局 Uuid。
     */
    data class UnsupportedAction(val gameId: Uuid) : GameError
}
