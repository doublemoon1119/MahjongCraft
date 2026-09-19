package com.doublemoon1119.mahjongcraft.testing.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.MatchSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.MeldPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinPresentationRequest
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.RoundInfoLine
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionPhase
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
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
    /** 依對局 Uuid 紀錄所有已成立的動作語音請求。 */
    private val gameActionSounds = mutableMapOf<Uuid, MutableList<GameActionSoundContext>>()

    override fun publishGameActionSound(gameId: Uuid, actorId: Uuid, action: GameAction) {
        gameActionSounds.getOrPut(gameId, ::mutableListOf).add(GameActionSoundContext(actorId, action))
    }

    /** 依對局 Uuid 紀錄最後一次終局結算呈現。 */
    private val matchSettlements = mutableMapOf<Uuid, MatchSettlementPresentationRequest>()

    /** 依對局 Uuid 紀錄最後一次統一流局結算呈現。 */
    private val exhaustiveDrawSettlements = mutableMapOf<Uuid, ExhaustiveDrawSettlementPresentationRequest>()

    /** 依對局 Uuid 紀錄最後一次收到的擲骰結果。 */
    private val diceRolls = mutableMapOf<Uuid, DiceRollResult>()

    /** 依對局 Uuid 紀錄最後一次收到的擲骰隨附桌況資料。 */
    private val diceRollContexts = mutableMapOf<Uuid, DiceRollContext>()

    /** 依對局 Uuid 紀錄最後一次收到的牌牆結構座標。 */
    private val wallStructures = mutableMapOf<Uuid, TileWallPhysicalLayout>()

    /** 依對局 Uuid 紀錄最後一次收到的牌牆組裝格位。 */
    private val wallAssemblyStructures = mutableMapOf<Uuid, Map<Uuid, TileWallPosition>>()

    /** 依對局 Uuid 紀錄所有收到的實體牌牆 transition。 */
    private val wallLayoutTransitions = mutableMapOf<Uuid, MutableList<List<PhysicalWallLayoutTransitionPhase>>>()

    /** 依對局 Uuid 紀錄最後一次收到的牌牆結構隨附桌況資料。 */
    private val wallStructureContexts = mutableMapOf<Uuid, WallStructureContext>()

    /** 依對局 Uuid 紀錄收到桌上物件更新通知的次數。 */
    private val tablePropsUpdateCounts = mutableMapOf<Uuid, Int>()

    /** 依對局 Uuid 紀錄最後一次收到的桌面局況顯示內容。 */
    private val roundInfos = mutableMapOf<Uuid, List<RoundInfoLine>>()

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

    /** 依對局 Uuid 紀錄收到的每一筆胡牌慶祝演出呼叫，依呼叫順序排列——一炮多響可能同一局收到多筆。 */
    private val winCelebrations = mutableMapOf<Uuid, MutableList<WinCelebrationContext>>()
    private val winSettlements = mutableMapOf<Uuid, MutableList<WinSettlementContext>>()
    private val winPresentations = mutableMapOf<Uuid, MutableList<WinPresentationRequest>>()
    private val publishOrder = mutableMapOf<Uuid, MutableList<List<WinPresentationSegment>>>()

    override fun publishExhaustiveDrawSettlement(gameId: Uuid, request: ExhaustiveDrawSettlementPresentationRequest) {
        exhaustiveDrawSettlements[gameId] = request
    }

    override fun publishMatchSettlement(gameId: Uuid, request: MatchSettlementPresentationRequest) {
        matchSettlements[gameId] = request
    }

    override fun publishDiceRoll(gameId: Uuid, dice: DiceRollResult, dealerSeatIndex: Int, roundNumber: Int, comboCount: Int) {
        diceRolls[gameId] = dice
        diceRollContexts[gameId] = DiceRollContext(dealerSeatIndex, roundNumber, comboCount)
    }

    override fun publishWallStructure(
        gameId: Uuid,
        assemblyStructure: Map<Uuid, TileWallPosition>,
        layout: TileWallPhysicalLayout,
        dealerSeatIndex: Int,
        deadWallTileIds: Set<Uuid>,
        diceCount: Int,
        animateOpening: Boolean,
        revealedTileIds: Set<Uuid>,
    ) {
        wallStructures[gameId] = layout
        wallAssemblyStructures[gameId] = assemblyStructure
        wallStructureContexts[gameId] = WallStructureContext(
            dealerSeatIndex,
            deadWallTileIds,
            diceCount,
            animateOpening,
            revealedTileIds,
        )
    }

    override fun publishWallLayoutTransition(gameId: Uuid, phases: List<PhysicalWallLayoutTransitionPhase>) {
        wallLayoutTransitions.getOrPut(gameId, ::mutableListOf).add(phases)
    }

    override fun publishWallTilesRevealed(gameId: Uuid, revealedTileIds: Set<Uuid>) {
        deadWallReveals[gameId] = revealedTileIds
    }

    override fun publishTablePropsUpdated(gameId: Uuid) {
        tablePropsUpdateCounts[gameId] = getTablePropsUpdateCount(gameId) + 1
    }

    override fun publishRoundInfoUpdated(gameId: Uuid, lines: List<RoundInfoLine>) {
        roundInfos[gameId] = lines
    }

    override fun publishPlayerAreaUpdated(
        gameId: Uuid,
        seatIndex: Int,
        standingTileIds: List<Uuid>,
        drawnTileId: Uuid?,
        melds: List<MeldPresentation>,
        animateDrawnTile: Boolean,
        animatedMeldClaimTileIds: Set<Uuid>,
    ) {
        playerAreas[gameId] = PlayerAreaContext(seatIndex, standingTileIds, drawnTileId, melds, animateDrawnTile, animatedMeldClaimTileIds)
    }

    override fun publishInitialDealAnimation(
        gameId: Uuid,
        handTileIdsBySeatIndex: Map<Int, List<Uuid>>,
        postFlipHandTileIdsBySeatIndex: Map<Int, List<Uuid>>,
        dealerSeatIndex: Int,
        dealBatchSizes: List<Int>,
        diceCount: Int,
    ) {
        initialDealAnimations[gameId] = InitialDealAnimationContext(
            handTileIdsBySeatIndex,
            postFlipHandTileIdsBySeatIndex,
            dealerSeatIndex,
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

    override fun publishWinCelebration(gameId: Uuid, request: WinCelebrationRequest) {
        winCelebrations.getOrPut(gameId) { mutableListOf() } += WinCelebrationContext(request)
    }

    override fun publishWinSettlement(gameId: Uuid, request: WinSettlementPresentationRequest) {
        winSettlements.getOrPut(gameId) { mutableListOf() } += WinSettlementContext(request)
    }

    override fun publishWinPresentation(gameId: Uuid, request: WinPresentationRequest) {
        winPresentations.getOrPut(gameId) { mutableListOf() } += request
        // 依序記錄成兩段，讓既有斷言與「celebration 一定先於 settlement」的驗證都能直接沿用。
        winCelebrations.getOrPut(gameId) { mutableListOf() } += WinCelebrationContext(request.celebration)
        winSettlements.getOrPut(gameId) { mutableListOf() } += WinSettlementContext(request.settlement)
        val segments = listOf(WinPresentationSegment.CELEBRATION, WinPresentationSegment.SETTLEMENT)
        publishOrder.getOrPut(gameId) { mutableListOf() }.add(segments)
    }

    /** 取得指定對局最後一次收到的擲骰結果；若無紀錄則回傳 null。 */
    fun getPublishedDiceRoll(gameId: Uuid): DiceRollResult? = diceRolls[gameId]

    /** 取得指定對局依序收到的已成立動作語音請求。 */
    fun getPublishedGameActionSounds(gameId: Uuid): List<GameActionSoundContext> = gameActionSounds[gameId].orEmpty()

    /** 取得指定對局最後一次收到的擲骰隨附桌況資料；若無紀錄則回傳 null。 */
    fun getPublishedDiceRollContext(gameId: Uuid): DiceRollContext? = diceRollContexts[gameId]

    /** 取得指定對局最後一次收到的牌牆結構座標；若無紀錄則回傳 null。 */
    fun getPublishedWallStructure(gameId: Uuid): TileWallPhysicalLayout? = wallStructures[gameId]

    /** 取得指定對局最後一次收到的牌牆組裝格位；若無紀錄則回傳 null。 */
    fun getPublishedWallAssemblyStructure(gameId: Uuid): Map<Uuid, TileWallPosition>? = wallAssemblyStructures[gameId]

    /** 取得指定對局依序收到的實體牌牆 transition。 */
    fun getPublishedWallLayoutTransitions(gameId: Uuid): List<List<PhysicalWallLayoutTransitionPhase>> = wallLayoutTransitions[gameId].orEmpty()

    /** 取得指定對局最後一次收到的牌牆結構隨附桌況資料；若無紀錄則回傳 null。 */
    fun getPublishedWallStructureContext(gameId: Uuid): WallStructureContext? = wallStructureContexts[gameId]

    /** 取得指定對局收到桌上物件更新通知的次數；沒有收到過則為 0。 */
    fun getTablePropsUpdateCount(gameId: Uuid): Int = tablePropsUpdateCounts[gameId] ?: 0

    /** 取得指定對局最後一次收到的桌面局況顯示內容；若無紀錄則回傳 null。 */
    fun getPublishedRoundInfo(gameId: Uuid): List<RoundInfoLine>? = roundInfos[gameId]

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

    /** 取得指定對局收到的全部胡牌慶祝演出呼叫，依呼叫順序排列；若無紀錄則回傳空清單。 */
    fun getPublishedWinCelebrations(gameId: Uuid): List<WinCelebrationContext> = winCelebrations[gameId].orEmpty()

    /** 取得指定對局收到的全部胡牌結算面板呼叫，依呼叫順序排列；若無紀錄則回傳空清單。 */
    fun getPublishedWinSettlements(gameId: Uuid): List<WinSettlementContext> = winSettlements[gameId].orEmpty()

    /** 取得指定對局收到的全部完整胡牌呈現請求，依呼叫順序排列；若無紀錄則回傳空清單。 */
    fun getPublishedWinPresentations(gameId: Uuid): List<WinPresentationRequest> = winPresentations[gameId].orEmpty()

    /** 取得每次 [publishWinPresentation] 內部實際包含的段落順序，供驗證 celebration 永遠先於 settlement。 */
    fun getWinPresentationSegmentOrder(gameId: Uuid): List<List<WinPresentationSegment>> = publishOrder[gameId].orEmpty()

    /** 取得指定對局最後一次統一流局結算呈現。 */
    fun getPublishedExhaustiveDrawSettlement(gameId: Uuid): ExhaustiveDrawSettlementPresentationRequest? = exhaustiveDrawSettlements[gameId]

    /** 取得指定對局最後一次終局結算呈現。 */
    fun getPublishedMatchSettlement(gameId: Uuid): MatchSettlementPresentationRequest? = matchSettlements[gameId]
}

/** 單筆已成立動作語音請求。 */
data class GameActionSoundContext(val actorId: Uuid, val action: GameAction)

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
    val animateOpening: Boolean,
    val revealedTileIds: Set<Uuid>,
)

/** [FakeGamePresentationPublisher] 紀錄的 [GamePresentationPublisher.publishPlayerAreaUpdated] 資料。 */
data class PlayerAreaContext(
    val seatIndex: Int,
    val standingTileIds: List<Uuid>,
    val drawnTileId: Uuid?,
    val melds: List<MeldPresentation>,
    val animateDrawnTile: Boolean,
    val animatedMeldClaimTileIds: Set<Uuid>,
)

/** [FakeGamePresentationPublisher] 紀錄的 [GamePresentationPublisher.publishInitialDealAnimation] 資料。 */
data class InitialDealAnimationContext(
    val handTileIdsBySeatIndex: Map<Int, List<Uuid>>,
    val postFlipHandTileIdsBySeatIndex: Map<Int, List<Uuid>>,
    val dealerSeatIndex: Int,
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

/** [FakeGamePresentationPublisher] 紀錄的單一筆 [GamePresentationPublisher.publishWinCelebration] 呼叫資料。 */
data class WinSettlementContext(val request: WinSettlementPresentationRequest)

/** 一次完整胡牌呈現裡的段落種類，依實際排定順序記錄。 */
enum class WinPresentationSegment { CELEBRATION, SETTLEMENT }

/** 一次胡牌慶祝演出呼叫的紀錄。 */
data class WinCelebrationContext(val request: WinCelebrationRequest) {
    /** 單一贏家測試相容用座位；多家和時為第一位。 */
    val winnerSeatIndex: Int get() = request.winners.first().seatIndex

    /** 共享胡牌張。 */
    val winningTileId: Uuid get() = request.winningTileId

    /** 是否為自摸。 */
    val isTsumo: Boolean get() = request.isTsumo
}
