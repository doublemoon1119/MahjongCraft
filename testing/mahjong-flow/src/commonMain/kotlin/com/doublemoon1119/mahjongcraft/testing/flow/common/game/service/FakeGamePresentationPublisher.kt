package com.doublemoon1119.mahjongcraft.testing.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.MeldPresentation
import com.doublemoon1119.mahjongcraft.logic.table.Wind
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

    /** 依對局 Uuid 紀錄最後一次收到的擲骰隨附桌況資料。 */
    private val diceRollContexts = mutableMapOf<Uuid, DiceRollContext>()

    /** 依對局 Uuid 紀錄最後一次收到的牌牆結構座標。 */
    private val wallStructures = mutableMapOf<Uuid, Map<Uuid, TileWallPosition>>()

    /** 依對局 Uuid 紀錄最後一次收到的牌牆結構隨附桌況資料。 */
    private val wallStructureContexts = mutableMapOf<Uuid, WallStructureContext>()

    /** 依對局 Uuid 紀錄最後一次收到的積棒呈現資料。 */
    private val scoringSticks = mutableMapOf<Uuid, ScoringStickContext>()

    /** 依對局 Uuid 紀錄最後一次收到的立直中座位集合。 */
    private val riichiSticks = mutableMapOf<Uuid, Set<Int>>()

    /** 依對局 Uuid 紀錄最後一次收到的桌面局況顯示資料。 */
    private val roundInfos = mutableMapOf<Uuid, RoundInfoContext>()

    /** 依對局 Uuid 紀錄最後一次收到的桌角區域（手牌/摸牌位/副露）呈現資料。 */
    private val playerAreas = mutableMapOf<Uuid, PlayerAreaContext>()

    /** 依對局 Uuid 紀錄最後一次收到的開局發牌動畫資料。 */
    private val initialDealAnimations = mutableMapOf<Uuid, InitialDealAnimationContext>()

    /** 依對局 Uuid 紀錄是否收到過 [clearPlayerAreas]。 */
    private val clearedPlayerAreas = mutableSetOf<Uuid>()

    /** 依對局 Uuid 紀錄最後一次收到的開局座位傳送清單。 */
    private val gameStartedSeatings = mutableMapOf<Uuid, List<Uuid>>()

    /** 依對局 Uuid 紀錄最後一次收到的牌河更新資料。 */
    private val discardPiles = mutableMapOf<Uuid, DiscardPileContext>()

    /** 依對局 Uuid 紀錄最後一次收到的王牌追加公開集合。 */
    private val deadWallReveals = mutableMapOf<Uuid, Set<Uuid>>()

    override fun publishDiceRoll(gameId: Uuid, dice: DiceRollResult, dealerSeatIndex: Int, roundNumber: Int, comboCount: Int) {
        diceRolls[gameId] = dice
        diceRollContexts[gameId] = DiceRollContext(dealerSeatIndex, roundNumber, comboCount)
    }

    override fun publishWallStructure(
        gameId: Uuid,
        structure: Map<Uuid, TileWallPosition>,
        dealerSeatIndex: Int,
        deadWallTileIds: Set<Uuid>,
        diceCount: Int,
        revealedTileIds: Set<Uuid>,
    ) {
        wallStructures[gameId] = structure
        wallStructureContexts[gameId] = WallStructureContext(dealerSeatIndex, deadWallTileIds, diceCount, revealedTileIds)
    }

    override fun publishDeadWallRevealUpdated(gameId: Uuid, revealedTileIds: Set<Uuid>) {
        deadWallReveals[gameId] = revealedTileIds
    }

    override fun publishScoringSticksUpdated(gameId: Uuid, dealerSeatIndex: Int, stickCount: Int) {
        scoringSticks[gameId] = ScoringStickContext(dealerSeatIndex, stickCount)
    }

    override fun publishRiichiSticksUpdated(gameId: Uuid, riichiSeatIndices: Set<Int>) {
        riichiSticks[gameId] = riichiSeatIndices
    }

    override fun publishRoundInfoUpdated(
        gameId: Uuid,
        prevalentWind: Wind,
        localRoundNumber: Int,
        comboCount: Int,
        wallRemainingCount: Int,
    ) {
        roundInfos[gameId] = RoundInfoContext(prevalentWind, localRoundNumber, comboCount, wallRemainingCount)
    }

    override fun publishPlayerAreaUpdated(
        gameId: Uuid,
        seatIndex: Int,
        standingTileIds: List<Uuid>,
        drawnTileId: Uuid?,
        melds: List<MeldPresentation>,
        comboStickCount: Int,
        animateDrawnTile: Boolean,
    ) {
        playerAreas[gameId] = PlayerAreaContext(seatIndex, standingTileIds, drawnTileId, melds, comboStickCount, animateDrawnTile)
    }

    override fun publishInitialDealAnimation(
        gameId: Uuid,
        handTileIdsBySeatIndex: Map<Int, List<Uuid>>,
        postFlipHandTileIdsBySeatIndex: Map<Int, List<Uuid>>,
        dealerSeatIndex: Int,
        comboStickCount: Int,
        dealBatchSizes: List<Int>,
        diceCount: Int,
    ) {
        initialDealAnimations[gameId] = InitialDealAnimationContext(
            handTileIdsBySeatIndex,
            postFlipHandTileIdsBySeatIndex,
            dealerSeatIndex,
            comboStickCount,
            dealBatchSizes,
            diceCount,
        )
    }

    override fun clearPlayerAreas(gameId: Uuid) {
        clearedPlayerAreas += gameId
    }

    override fun publishGameStarted(gameId: Uuid, seatedPlayerIds: List<Uuid>) {
        gameStartedSeatings[gameId] = seatedPlayerIds
    }

    override fun publishDiscardPileUpdated(
        gameId: Uuid,
        seatIndex: Int,
        discardTileIds: List<Uuid>,
        sidewaysMarkedTileId: Uuid?,
        newlyDiscardedTileId: Uuid?,
    ) {
        discardPiles[gameId] = DiscardPileContext(seatIndex, discardTileIds, sidewaysMarkedTileId, newlyDiscardedTileId)
    }

    /** 取得指定對局最後一次收到的擲骰結果；若無紀錄則回傳 null。 */
    fun getPublishedDiceRoll(gameId: Uuid): DiceRollResult? = diceRolls[gameId]

    /** 取得指定對局最後一次收到的擲骰隨附桌況資料；若無紀錄則回傳 null。 */
    fun getPublishedDiceRollContext(gameId: Uuid): DiceRollContext? = diceRollContexts[gameId]

    /** 取得指定對局最後一次收到的牌牆結構座標；若無紀錄則回傳 null。 */
    fun getPublishedWallStructure(gameId: Uuid): Map<Uuid, TileWallPosition>? = wallStructures[gameId]

    /** 取得指定對局最後一次收到的牌牆結構隨附桌況資料；若無紀錄則回傳 null。 */
    fun getPublishedWallStructureContext(gameId: Uuid): WallStructureContext? = wallStructureContexts[gameId]

    /** 取得指定對局最後一次收到的積棒呈現資料；若無紀錄則回傳 null。 */
    fun getPublishedScoringSticks(gameId: Uuid): ScoringStickContext? = scoringSticks[gameId]

    /** 取得指定對局最後一次收到的立直中座位集合；若無紀錄則回傳 null。 */
    fun getPublishedRiichiSticks(gameId: Uuid): Set<Int>? = riichiSticks[gameId]

    /** 取得指定對局最後一次收到的桌面局況顯示資料；若無紀錄則回傳 null。 */
    fun getPublishedRoundInfo(gameId: Uuid): RoundInfoContext? = roundInfos[gameId]

    /** 取得指定對局最後一次收到的桌角區域（手牌/摸牌位/副露）呈現資料；若無紀錄則回傳 null。 */
    fun getPublishedPlayerArea(gameId: Uuid): PlayerAreaContext? = playerAreas[gameId]

    /** 取得指定對局最後一次收到的開局發牌動畫資料；若無紀錄則回傳 null。 */
    fun getPublishedInitialDealAnimation(gameId: Uuid): InitialDealAnimationContext? = initialDealAnimations[gameId]

    /** 指定對局是否曾經收到過 [clearPlayerAreas]。 */
    fun wasPlayerAreasCleared(gameId: Uuid): Boolean = gameId in clearedPlayerAreas

    /** 取得指定對局最後一次收到的開局座位傳送清單；若無紀錄則回傳 null。 */
    fun getPublishedGameStartedSeating(gameId: Uuid): List<Uuid>? = gameStartedSeatings[gameId]

    /** 取得指定對局最後一次收到的牌河更新資料；若無紀錄則回傳 null。 */
    fun getPublishedDiscardPile(gameId: Uuid): DiscardPileContext? = discardPiles[gameId]

    /** 取得指定對局最後一次收到的王牌追加公開集合；若無紀錄則回傳 null。 */
    fun getPublishedDeadWallReveal(gameId: Uuid): Set<Uuid>? = deadWallReveals[gameId]
}

/** [FakeGamePresentationPublisher] 紀錄的 [GamePresentationPublisher.publishDiceRoll] 隨附桌況資料。 */
data class DiceRollContext(
    val dealerSeatIndex: Int,
    val roundNumber: Int,
    val comboCount: Int,
)

/** [FakeGamePresentationPublisher] 紀錄的 [GamePresentationPublisher.publishWallStructure] 隨附桌況資料。 */
data class WallStructureContext(
    val dealerSeatIndex: Int,
    val deadWallTileIds: Set<Uuid>,
    val diceCount: Int,
    val revealedTileIds: Set<Uuid>,
)

/** [FakeGamePresentationPublisher] 紀錄的 [GamePresentationPublisher.publishScoringSticksUpdated] 資料。 */
data class ScoringStickContext(
    val dealerSeatIndex: Int,
    val stickCount: Int,
)

/** [FakeGamePresentationPublisher] 紀錄的 [GamePresentationPublisher.publishRoundInfoUpdated] 資料。 */
data class RoundInfoContext(
    val prevalentWind: Wind,
    val localRoundNumber: Int,
    val comboCount: Int,
    val wallRemainingCount: Int,
)

/** [FakeGamePresentationPublisher] 紀錄的 [GamePresentationPublisher.publishPlayerAreaUpdated] 資料。 */
data class PlayerAreaContext(
    val seatIndex: Int,
    val standingTileIds: List<Uuid>,
    val drawnTileId: Uuid?,
    val melds: List<MeldPresentation>,
    val comboStickCount: Int,
    val animateDrawnTile: Boolean,
)

/** [FakeGamePresentationPublisher] 紀錄的 [GamePresentationPublisher.publishInitialDealAnimation] 資料。 */
data class InitialDealAnimationContext(
    val handTileIdsBySeatIndex: Map<Int, List<Uuid>>,
    val postFlipHandTileIdsBySeatIndex: Map<Int, List<Uuid>>,
    val dealerSeatIndex: Int,
    val comboStickCount: Int,
    val dealBatchSizes: List<Int>,
    val diceCount: Int,
)

/** [FakeGamePresentationPublisher] 紀錄的 [GamePresentationPublisher.publishDiscardPileUpdated] 資料。 */
data class DiscardPileContext(
    val seatIndex: Int,
    val discardTileIds: List<Uuid>,
    val sidewaysMarkedTileId: Uuid?,
    val newlyDiscardedTileId: Uuid?,
)
