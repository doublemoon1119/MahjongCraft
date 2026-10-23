package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.config.dealBatchSizes
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializationResult
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.logic.table.TileWallRevealable
import kotlin.uuid.Uuid

/**
 * 開始遊戲的實例化用例。
 *
 * 負責將一個已準備完成的房間轉換為進行中的對局：驗證房間狀態、初始化桌況、
 * 在 [AuthoritativeStateStore] 的單次交易內把識別碼從 Room 移至 Game，並同步快照與廣播開局事件。
 *
 * @property authoritativeStateStore Room 與 Game 共用的權威狀態儲存。
 * @property moduleRegistry 麻將規則模組註冊中心，用於依房間配置解析對應的規則模組。
 * @property snapshotSynchronizer 對局快照同步服務。
 * @property handSortPreferenceStore 查詢玩家是否啟用自動整理手牌，見該類別 KDoc。
 * @property eventPublisher 對局通知服務。
 * @property presentationPublisher 對局 in-process 呈現觸發器。
 * @property beginRuleDecisionUseCase 開局後啟動第一個回合選擇步驟的用例；未提供時不啟動選擇流程。
 */
class StartGameUseCase(
    private val authoritativeStateStore: AuthoritativeStateStore,
    private val moduleRegistry: MahjongModuleRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    private val handSortPreferenceStore: HandSortPreferenceStore,
    private val eventPublisher: GameEventPublisher,
    private val presentationPublisher: GamePresentationPublisher,
    private val beginRuleDecisionUseCase: BeginRuleDecisionUseCase? = null,
) {
    /**
     * 執行開始遊戲邏輯。
     *
     * @param roomId 欲開始的房間 Uuid（開局後將同時作為對局的 Uuid）。
     * @param operatorId 發起開局請求的玩家 Uuid，須為房主。
     * @return 開局結果，成功時包含對局 Uuid（等同 [roomId]），失敗時為 [RoomError]。
     */
    suspend operator fun invoke(roomId: Uuid, operatorId: Uuid): Outcome<Uuid, RoomError> {
        // 1. 驗證房間狀態、初始化桌況、移除 Room 與新增 Game 都在同一次 store 交易內完成。
        val outcome = authoritativeStateStore.update { state ->
            val room = state.rooms[roomId]
            when {
                room == null -> AuthoritativeStateUpdate(state, Outcome.Error(RoomError.RoomNotFound(roomId)))
                room.hostId != operatorId -> AuthoritativeStateUpdate(state, Outcome.Error(RoomError.NotHost(operatorId)))
                !room.isPlayerCountValid -> AuthoritativeStateUpdate(state, Outcome.Error(RoomError.RoomPlayerCountInvalid(roomId)))
                !room.canStart -> AuthoritativeStateUpdate(state, Outcome.Error(RoomError.RoomNotReadyToStart(roomId)))
                else -> {
                    val module = moduleRegistry.getModule(room.gameConfig.ruleConfig)
                    val initializationResult = GameInitializer.initialize(
                        id = roomId,
                        playerIds = room.playerIds,
                        module = module,
                    )
                    val dealtState = initializationResult.tableState
                    // 發牌順序（哪張牌在哪一批發出），只提供給呈現使用，不是實際寫回權威狀態
                    // 的手牌順序——實際寫回的是下面整理過的 organizedState，讓後續所有讀取 Hand.tiles
                    // 的地方（例如摸牌後的重新呈現）自然維持排序，不需要每個呼叫點各自重新整理一次，
                    // 見 HandSortPreferenceStore KDoc。
                    val dealOrderHandTileIdsBySeatIndex = dealtState.players.withIndex().associate { (seatIndex, player) ->
                        seatIndex to player.hand.tiles.map { tile -> tile.id }
                    }
                    val organizedPlayers = dealtState.players.map { player ->
                        if (handSortPreferenceStore.isEnabled(player.id)) {
                            player.copy(hand = player.hand.organize(module.tileOrder))
                        } else {
                            player
                        }
                    }
                    val organizedState = dealtState.copy(players = organizedPlayers)
                    val organizedResult = initializationResult.copy(tableState = organizedState)

                    AuthoritativeStateUpdate(
                        state = state.copy(
                            rooms = state.rooms - roomId,
                            games = state.games + (
                                roomId to Game(
                                    tableState = organizedState,
                                    flowConfig = room.gameConfig.flowConfig,
                                    matchId = Uuid.random(),
                                    hostId = room.hostId,
                                    roomPlayerIds = room.playerIds,
                                    aiPlayerStrategyKeys = room.aiPlayerStrategyKeys,
                                )
                                ),
                        ),
                        result = Outcome.Success(StartGameOutcome(organizedResult, dealOrderHandTileIdsBySeatIndex)),
                        historyDraftsByVenueId = mapOf(
                            roomId to listOf(
                                HistoryEventDraft(
                                    actorPlayerId = null,
                                    fact = HistoryFact.MatchStarted(organizedState, room.gameConfig.flowConfig, room.aiPlayerStrategyKeys),
                                ),
                            ) + organizedState.openingDiceRolls.map { roll ->
                                HistoryEventDraft(roll.playerId, HistoryFact.DiceRolled(roll))
                            },
                        ),
                    )
                }
            }
        }

        if (outcome is Outcome.Error) return outcome
        val startOutcome = (outcome as Outcome.Success).value
        val initializationResult = startOutcome.initializationResult
        val tableState = initializationResult.tableState

        // 2. 為每位玩家同步一份對局快照
        tableState.players.forEach { player ->
            snapshotSynchronizer.sync(roomId, player.id)
        }

        // 3. 廣播「對局已開始」事件
        val seatedPlayerIds = tableState.players.map { it.id }
        eventPublisher.publishToAllObservers(roomId, seatedPlayerIds, operatorId, GameAction.GameStarted)

        // 4. 觸發平台呈現層：規則不支援開門流程時皆為 null，直接跳過。牌牆先建、骰子後擲——真實麻將
        // 是先砌好牌牆才擲骰決定開門位置，呈現層依賴這個固定呼叫順序（先牌牆、後擲骰），這裡不能對調。
        val dealerSeatIndex = tableState.dealerIndex
        initializationResult.initialPhysicalWallLayout?.let { layout ->
            val deadWallTileIds = tableState.reservedWallTiles.map { tile -> tile.id }.toSet()
            val openingDiceCounts = tableState.openingDiceRolls.map { it.dice.values.size }
            val revealedTileIds = (tableState.dynamicRuleState as? TileWallRevealable)?.getVisibleTileIds(tableState) ?: emptySet()
            presentationPublisher.publishWallStructure(
                roomId,
                requireNotNull(initializationResult.wallStructure),
                layout,
                dealerSeatIndex,
                deadWallTileIds,
                openingDiceCounts,
                revealedTileIds = revealedTileIds,
            )
        }
        if (tableState.openingDiceRolls.isNotEmpty()) {
            presentationPublisher.publishOpeningRolls(
                roomId,
                tableState.openingDiceRolls,
            )
            tableState.openingDiceRolls.forEach { roll ->
                eventPublisher.publishToAllObservers(roomId, seatedPlayerIds, roll.playerId, GameAction.DiceRolled(roll))
            }
        }
        // 規則狀態的呈現跟牌牆同時更新，緊接在 publishWallStructure 之後呼叫；開局時也要呼叫，
        // 確保上一場殘留的物件被清乾淨。
        presentationPublisher.publishRuleStateUpdated(roomId)
        presentationPublisher.publishRoundInfoUpdated(roomId, tableState)
        // 發牌完成後的最終牌序——tableState 此時已經是整理過的順序（見上方 organizedState），
        // 與發牌順序 startOutcome.dealOrderHandTileIdsBySeatIndex 分開，見
        // GamePresentationPublisher.publishInitialDeal KDoc。
        val postFlipHandTileIdsBySeatIndex = tableState.players.withIndex().associate { (seatIndex, player) ->
            seatIndex to player.hand.tiles.map { tile -> tile.id }
        }
        presentationPublisher.publishInitialDeal(
            roomId,
            startOutcome.dealOrderHandTileIdsBySeatIndex,
            postFlipHandTileIdsBySeatIndex,
            dealerSeatIndex,
            dealBatchSizes = tableState.config.dealBatchSizes(),
            openingDiceCounts = tableState.openingDiceRolls.map { it.dice.values.size },
        )
        presentationPublisher.publishGameStarted(roomId, tableState.players.map { it.id })
        beginRuleDecisionUseCase?.invoke(roomId)

        return Outcome.Success(roomId)
    }

    /**
     * `update` 區塊內部使用的中繼結果，讓 [dealOrderHandTileIdsBySeatIndex]（發牌順序，在
     * [initializationResult] 的手牌已經被整理過之後就無法再從它反推出來）能跟著
     * [initializationResult] 一起帶出 `authoritativeStateStore.update` 的作用域。
     *
     * @property initializationResult 初始化完成且已整理手牌的桌況與發牌資訊。
     * @property dealOrderHandTileIdsBySeatIndex 各座位實際發牌順序的牌張 ID。
     */
    private data class StartGameOutcome(
        val initializationResult: GameInitializationResult,
        val dealOrderHandTileIdsBySeatIndex: Map<Int, List<Uuid>>,
    )
}
