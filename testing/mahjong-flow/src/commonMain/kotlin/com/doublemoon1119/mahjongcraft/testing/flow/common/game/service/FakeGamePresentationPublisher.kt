package com.doublemoon1119.mahjongcraft.testing.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import kotlin.uuid.Uuid

/**
 * 供測試使用的 [GamePresentationPublisher] 模擬實作。
 *
 * 紀錄每個對局最後一次收到的擲骰結果、牌牆結構與開局座位傳送，以便在單元測試中驗證業務邏輯是否
 * 正確觸發呈現。
 */
class FakeGamePresentationPublisher : GamePresentationPublisher {
    /** 依對局 Uuid 紀錄最後一次收到的擲骰結果。 */
    private val diceRolls = mutableMapOf<Uuid, DiceRollResult>()

    /** 依對局 Uuid 紀錄最後一次收到的牌牆結構座標。 */
    private val wallStructures = mutableMapOf<Uuid, Map<Uuid, TileWallPosition>>()

    /** 依對局 Uuid 紀錄最後一次收到的開局座位傳送清單。 */
    private val gameStartedSeatings = mutableMapOf<Uuid, List<Uuid>>()

    override fun publishDiceRoll(gameId: Uuid, dice: DiceRollResult) {
        diceRolls[gameId] = dice
    }

    override fun publishWallStructure(gameId: Uuid, structure: Map<Uuid, TileWallPosition>) {
        wallStructures[gameId] = structure
    }

    override fun publishGameStarted(gameId: Uuid, seatedPlayerIds: List<Uuid>) {
        gameStartedSeatings[gameId] = seatedPlayerIds
    }

    /** 取得指定對局最後一次收到的擲骰結果；若無紀錄則回傳 null。 */
    fun getPublishedDiceRoll(gameId: Uuid): DiceRollResult? = diceRolls[gameId]

    /** 取得指定對局最後一次收到的牌牆結構座標；若無紀錄則回傳 null。 */
    fun getPublishedWallStructure(gameId: Uuid): Map<Uuid, TileWallPosition>? = wallStructures[gameId]

    /** 取得指定對局最後一次收到的開局座位傳送清單；若無紀錄則回傳 null。 */
    fun getPublishedGameStartedSeating(gameId: Uuid): List<Uuid>? = gameStartedSeatings[gameId]
}
