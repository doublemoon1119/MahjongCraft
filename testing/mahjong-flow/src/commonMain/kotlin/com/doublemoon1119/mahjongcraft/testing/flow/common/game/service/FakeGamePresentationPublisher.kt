package com.doublemoon1119.mahjongcraft.testing.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.AppliedActionScoring
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.MatchSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.TableStep
import com.doublemoon1119.mahjongcraft.flow.common.game.service.TableStepEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinPresentationRequest
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.opening.OpeningDiceRoll
import kotlin.uuid.Uuid

/**
 * 供測試使用的 [GamePresentationPublisher] 模擬實作。
 *
 * 紀錄每個對局收到的桌況步驟、擲骰結果、牌牆結構與開局座位安排，以便在單元測試中驗證業務邏輯是否
 * 正確觸發呈現。
 */
class FakeGamePresentationPublisher : GamePresentationPublisher {
    /** 依對局 Uuid 依序紀錄每次收到的桌況步驟。 */
    private val tableStepBatches = mutableMapOf<Uuid, MutableList<List<TableStep>>>()

    override fun publishTableSteps(gameId: Uuid, steps: List<TableStep>) {
        tableStepBatches.getOrPut(gameId, ::mutableListOf).add(steps.toList())
    }

    /** 依對局 Uuid 紀錄最後一次終局結算呈現。 */
    private val matchSettlements = mutableMapOf<Uuid, MatchSettlementPresentationRequest>()

    /** 依對局 Uuid 紀錄最後一次統一流局結算呈現。 */
    private val exhaustiveDrawSettlements = mutableMapOf<Uuid, ExhaustiveDrawSettlementPresentationRequest>()

    /** 依對局 Uuid 紀錄最後一次收到的擲骰結果。 */
    private val diceRolls = mutableMapOf<Uuid, List<OpeningDiceRoll>>()

    /** 依對局 Uuid 紀錄最後一次收到的牌牆結構座標。 */
    private val wallStructures = mutableMapOf<Uuid, TileWallPhysicalLayout>()

    /** 依對局 Uuid 紀錄最後一次收到的牌牆組裝格位。 */
    private val wallAssemblyStructures = mutableMapOf<Uuid, Map<Uuid, TileWallPosition>>()

    /** 依對局 Uuid 紀錄最後一次收到的牌牆結構隨附桌況資料。 */
    private val wallStructureContexts = mutableMapOf<Uuid, WallStructureContext>()

    /** 依對局 Uuid 紀錄最後一次收到的開局發牌動畫資料。 */
    private val initialDeals = mutableMapOf<Uuid, InitialDealContext>()

    /** 依對局 Uuid 紀錄是否收到過 [clearPlayerTiles]。 */
    private val clearedPlayerTiles = mutableSetOf<Uuid>()

    /** 依對局 Uuid 紀錄最後一次收到的開局座位安排。 */
    private val gameStartedSeatings = mutableMapOf<Uuid, List<Uuid>>()

    /** 依對局 Uuid 依序紀錄收到的局間換位座位順序。 */
    private val seatOrderChanges = mutableMapOf<Uuid, MutableList<List<Uuid>>>()

    /** 依對局 Uuid 依序紀錄單獨慶祝呼叫及完整呈現請求所含的慶祝內容；一炮多響可能同一局收到多筆。 */
    private val winCelebrations = mutableMapOf<Uuid, MutableList<WinCelebrationContext>>()

    /** 依對局 Uuid 依序紀錄單獨結算呼叫及完整呈現請求所含的結算內容。 */
    private val winSettlements = mutableMapOf<Uuid, MutableList<WinSettlementContext>>()

    /** 依對局 Uuid 紀錄收到的完整胡牌呈現請求，依呼叫順序排列。 */
    private val winPresentations = mutableMapOf<Uuid, MutableList<WinPresentationRequest>>()

    /** 依對局 Uuid 紀錄每次完整胡牌呈現內的段落順序。 */
    private val publishOrder = mutableMapOf<Uuid, MutableList<List<WinPresentationSegment>>>()

    override fun publishExhaustiveDrawSettlement(gameId: Uuid, request: ExhaustiveDrawSettlementPresentationRequest) {
        exhaustiveDrawSettlements[gameId] = request
    }

    override fun publishMatchSettlement(gameId: Uuid, request: MatchSettlementPresentationRequest) {
        matchSettlements[gameId] = request
    }

    override fun publishOpeningRolls(gameId: Uuid, rolls: List<OpeningDiceRoll>) {
        diceRolls[gameId] = rolls.toList()
    }

    override fun publishWallStructure(
        gameId: Uuid,
        assemblyStructure: Map<Uuid, TileWallPosition>,
        layout: TileWallPhysicalLayout,
        dealerSeatIndex: Int,
        reservedWallTileIds: Set<Uuid>,
        openingDiceCounts: List<Int>,
        isNewOpening: Boolean,
        revealedTileIds: Set<Uuid>,
    ) {
        wallStructures[gameId] = layout
        wallAssemblyStructures[gameId] = assemblyStructure
        wallStructureContexts[gameId] = WallStructureContext(
            dealerSeatIndex = dealerSeatIndex,
            reservedWallTileIds = reservedWallTileIds,
            openingDiceCounts = openingDiceCounts,
            isNewOpening = isNewOpening,
            revealedTileIds = revealedTileIds,
        )
    }

    override fun publishInitialDeal(
        gameId: Uuid,
        tableState: TableState,
        handTileIdsBySeatIndex: Map<Int, List<Uuid>>,
        dealBatchSizes: List<Int>,
        openingDiceCounts: List<Int>,
    ) {
        initialDeals[gameId] = InitialDealContext(
            handTileIdsBySeatIndex = handTileIdsBySeatIndex,
            postFlipHandTileIdsBySeatIndex = tableState.players.withIndex().associate { (seatIndex, player) -> seatIndex to player.hand.tiles.map { it.id } },
            dealerSeatIndex = tableState.dealerIndex,
            dealBatchSizes = dealBatchSizes,
            openingDiceCounts = openingDiceCounts,
        )
    }

    override fun clearPlayerTiles(gameId: Uuid) {
        clearedPlayerTiles += gameId
    }

    override fun publishGameStarted(gameId: Uuid, seatedPlayerIds: List<Uuid>) {
        gameStartedSeatings[gameId] = seatedPlayerIds
    }

    override fun publishSeatOrderChanged(gameId: Uuid, seatedPlayerIds: List<Uuid>) {
        seatOrderChanges.getOrPut(gameId) { mutableListOf() } += seatedPlayerIds
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

    /** 讀取每次收到的桌況步驟，依收到順序排列。 */
    fun getPublishedTableStepBatches(gameId: Uuid): List<List<TableStep>> = tableStepBatches[gameId].orEmpty().toList()

    /** 讀取所有收到的桌況步驟，依收到順序攤平。 */
    fun getPublishedTableSteps(gameId: Uuid): List<TableStep> = tableStepBatches[gameId].orEmpty().flatten()

    /** 讀取最後一次收到的步驟中最後一步的桌況；沒有收到時為 null。 */
    fun getLastPresentedTableState(gameId: Uuid): TableState? = getPublishedTableSteps(gameId).lastOrNull()?.tableState

    /** 讀取所有步驟中宣告成立的動作，依發生順序排列。 */
    fun getDeclaredGameActions(gameId: Uuid): List<DeclaredGameActionContext> = getPublishedTableSteps(gameId)
        .flatMap { it.events }
        .filterIsInstance<TableStepEvent.ActionDeclared>()
        .map { DeclaredGameActionContext(it.actorId, it.action) }

    /** 讀取所有步驟中的即時收付，依發生順序排列。 */
    fun getPublishedActionScorings(gameId: Uuid): List<AppliedActionScoring> = getPublishedTableSteps(gameId)
        .flatMap { it.events }
        .filterIsInstance<TableStepEvent.ActionScored>()
        .map { it.scoring }

    /** 讀取本局全部擲骰呈現記錄。 */
    fun getPublishedOpeningRolls(gameId: Uuid): List<OpeningDiceRoll> = diceRolls[gameId].orEmpty()

    /** 取得指定對局最後一次收到的牌牆結構座標；若無紀錄則回傳 null。 */
    fun getPublishedWallStructure(gameId: Uuid): TileWallPhysicalLayout? = wallStructures[gameId]

    /** 取得指定對局最後一次收到的牌牆組裝格位；若無紀錄則回傳 null。 */
    fun getPublishedWallAssemblyStructure(gameId: Uuid): Map<Uuid, TileWallPosition>? = wallAssemblyStructures[gameId]

    /** 取得指定對局最後一次收到的牌牆結構隨附桌況資料；若無紀錄則回傳 null。 */
    fun getPublishedWallStructureContext(gameId: Uuid): WallStructureContext? = wallStructureContexts[gameId]

    /** 取得指定對局最後一次收到的開局發牌動畫資料；若無紀錄則回傳 null。 */
    fun getPublishedInitialDeal(gameId: Uuid): InitialDealContext? = initialDeals[gameId]

    /** 指定對局是否曾經收到過 [clearPlayerTiles]。 */
    fun werePlayerTilesCleared(gameId: Uuid): Boolean = gameId in clearedPlayerTiles

    /** 取得指定對局最後一次收到的開局座位安排；若無紀錄則回傳 null。 */
    fun getPublishedGameStartedSeating(gameId: Uuid): List<Uuid>? = gameStartedSeatings[gameId]

    /** 取得指定對局依序收到的局間換位座位順序；沒有換位時為空清單。 */
    fun getPublishedSeatOrderChanges(gameId: Uuid): List<List<Uuid>> = seatOrderChanges[gameId].orEmpty().toList()

    /** 取得指定對局收到的全部胡牌慶祝演出呼叫，依呼叫順序排列；若無紀錄則回傳空清單。 */
    fun getPublishedWinCelebrations(gameId: Uuid): List<WinCelebrationContext> = winCelebrations[gameId].orEmpty()

    /** 取得指定對局收到的全部完整胡牌呈現請求，依呼叫順序排列；若無紀錄則回傳空清單。 */
    fun getPublishedWinPresentations(gameId: Uuid): List<WinPresentationRequest> = winPresentations[gameId].orEmpty()

    /** 取得每次 [publishWinPresentation] 內部實際包含的段落順序，供驗證 celebration 永遠先於 settlement。 */
    fun getWinPresentationSegmentOrder(gameId: Uuid): List<List<WinPresentationSegment>> = publishOrder[gameId].orEmpty()

    /** 取得指定對局最後一次統一流局結算呈現。 */
    fun getPublishedExhaustiveDrawSettlement(gameId: Uuid): ExhaustiveDrawSettlementPresentationRequest? = exhaustiveDrawSettlements[gameId]

    /** 取得指定對局最後一次終局結算呈現。 */
    fun getPublishedMatchSettlement(gameId: Uuid): MatchSettlementPresentationRequest? = matchSettlements[gameId]
}
