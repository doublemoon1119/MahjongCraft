package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.MatchSettlementPlayerPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.MatchSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingGameTransition
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.config.dealBatchSizes
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.logic.table.RoundAdvancementResult
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWallRevealable
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 連莊/過莊、開下一局的實例化用例。
 *
 * 這是「連莊/過莊/開下一局」系列子項的最後一塊：把前面各自獨立寫好、測好的
 * [TableState.advanceRound]（連莊/過莊判定與局數/本場數/場風推進）
 * 與 [GameInitializer.startNextRound]（開下一局的新初始化路徑）串起來，
 * 做成一個真正會寫回 [GameRepository] 的 use case。
 *
 * 由誰、在什麼時機呼叫本用例（例如接在 [DeclareTsumoUseCase]/[RespondToDiscardUseCase] 成功之後
 * 自動觸發，或由前端明確請求）是更外層（伺服器流程編排）的決定，不在這裡處理；呼叫前應確認本局
 * 已經結算完畢（沒有 `pendingReaction`）。
 *
 * 「莊家是否連莊」優先使用 [Game.roundTransitionDirective] 保存的明確規則決策；尚未遷移到 outcome
 * resolver 的既有胡牌與流局路徑才暫時回退到以下歷史紀錄：
 * [DeclareTsumoUseCase]/[RespondToDiscardUseCase] 會把 [GameAction.Tsumo]/[GameAction.Ron] 記錄進
 * 贏家的 `actionHistory`；[DeclareExhaustiveDrawUseCase] 則只把 [GameAction.ExhaustiveDraw] 記錄進
 * 聽牌玩家的 `actionHistory`，不聽的玩家不記錄。
 * [GameInitializer.startNextRound] 每次開新的一局都會把 `actionHistory` 重置成空，所以「這局的
 * `actionHistory` 裡有沒有 Tsumo/Ron/ExhaustiveDraw」可以直接拿來判斷「莊家這局是不是贏家之一，
 * 或流局時是否聽牌」。九種九牌等途中流局的連莊依據（莊家固定連莊，不需要判斷聽牌與否）留給
 * 對應子項擴充。
 *
 * 整場對局已結束（[RoundAdvancementResult.isMatchOver]）
 * 時，本用例把這個事實記到 [Game.isMatchOver]，
 * 並同步最終桌況快照、廣播 [GameAction.MatchEnded]（帶著最終分數的快照，供呼叫端／客戶端組出排名
 * 呈現）——但不會開新的一局；`TableState` 除了「桌上未被收下的供託歸給最終第一名」這個結算之外
 * 維持呼叫前的樣子不變（見 `MahjongRuleModule.collectStickPot` 的呼叫）。房間清理（把桌子從 Game
 * 轉回 Room）留給呼叫端接著呼叫 `ReturnToRoomUseCase`，不在這裡處理。務必記下 `isMatchOver` 這個事實：
 * `AiTurnDriver`／`ForcedAutoPlayDriver` 都靠它提前跳過已結束的對局，否則牌山已空的桌況會被反覆
 * 嘗試摸牌、反覆觸發流局結算。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析當前對局的規則模組。
 * @property snapshotSynchronizer 對局快照同步服務。
 * @property handSortPreferenceStore 查詢玩家是否啟用自動整理手牌，見該類別 KDoc。
 * @property eventPublisher 對局通知服務。
 * @property presentationPublisher 對局 in-process 呈現觸發器。
 */
@Factory
class AdvanceRoundUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    private val handSortPreferenceStore: HandSortPreferenceStore,
    @Provided private val eventPublisher: GameEventPublisher,
    @Provided private val presentationPublisher: GamePresentationPublisher,
    private val beginRoundPreparationUseCase: BeginRoundPreparationUseCase? = null,
) {
    /**
     * 執行連莊/過莊、開下一局邏輯。
     *
     * @param gameId 對局 Uuid。
     * @return 執行結果，成功時為 [AdvanceRoundResult]，失敗時為 [GameError]。
     */
    suspend operator fun invoke(gameId: Uuid): Outcome<AdvanceRoundResult, GameError> {
        // 1. 以原子方式讀取桌況、計算連莊/過莊結果並寫回
        val outcome = gameRepository.updateGame(gameId) { game ->
            val state = game?.tableState
            when {
                game == null || state == null -> game to Outcome.Error(GameError.GameNotFound(gameId))
                else -> {
                    val module = moduleRegistry.getModule(state.config)
                    val dealer = state.players.first { it.currentWind == Wind.EAST }
                    val dealerRepeats = when (game.roundTransitionDirective) {
                        RoundTransitionDirective.REPEAT_DEALER -> true
                        RoundTransitionDirective.ADVANCE_DEALER -> false
                        null -> dealer.actionHistory.any {
                            it is GameAction.Tsumo || it is GameAction.Ron || it is GameAction.ExhaustiveDraw
                        }
                    }
                    val roundAdvancement = state.advanceRound(dealerRepeats)
                    // 除了 GameLength.totalRounds（已經反映在 roundAdvancement.isMatchOver）之外，
                    // 規則模組可能還有額外造成對局立即結束的條件（例如日麻的擊飛），見
                    // MahjongRuleModule.hasAdditionalMatchEndCondition KDoc。用「本局結算完畢後」的
                    // state（分數已是最終值）判斷，只會讓對局比 totalRounds 更早結束，不會延後。
                    val isMatchOver = roundAdvancement.isMatchOver || module.hasAdditionalMatchEndCondition(state)

                    if (isMatchOver) {
                        // 整場對局結束時，桌上未被任何人收下的供託（如立直棒）歸給最終第一名——排名
                        // 判準交給 module.compareForMatchRanking()，跟用戶端的終局排名呈現邏輯共用
                        // 同一支規則 hook（見 GameEventChatNotifier）。沿用既有的 collectStickPot
                        // （贏家收供託也是同一支函式），不支援供託機制的規則回傳 null，維持 state 不變。
                        val stickPot = module.collectStickPot(state)
                        val finalState = if (stickPot != null && stickPot.second > 0) {
                            val topPlayerId = state.players
                                .sortedWith(module.compareForMatchRanking())
                                .first().id
                            state.copy(
                                players = state.players.map {
                                    if (it.id == topPlayerId) it.copy(score = it.score + stickPot.second) else it
                                },
                                dynamicRuleState = stickPot.first ?: state.dynamicRuleState,
                            )
                        } else {
                            state
                        }
                        val advanceOutcome = AdvanceRoundOutcome(
                            result = AdvanceRoundResult(finalState, isMatchOver = true),
                            diceRoll = null,
                            wallStructure = null,
                            dealOrderHandTileIdsBySeatIndex = emptyMap(),
                        )
                        // 對局已經結束：記下這個事實，讓 AiTurnDriver／ForcedAutoPlayDriver 之後都
                        // 跳過這場對局，不會對已經沒有牌可摸的桌況繼續重複嘗試、重複觸發流局結算。
                        game.copy(
                            tableState = finalState,
                            isMatchOver = true,
                            pendingTransition = PendingGameTransition.ReturnToRoom,
                            roundTransitionDirective = null,
                        ) to Outcome.Success(advanceOutcome)
                    } else {
                        val initializationResult = GameInitializer.startNextRound(
                            gameId = gameId,
                            roundAdvancement = roundAdvancement,
                            previousDynamicRuleState = state.dynamicRuleState,
                            module = module,
                        )
                        val dealtState = initializationResult.tableState
                        // 只用來讓發牌動畫本身維持原始時間軸（哪張牌在哪一批抵達），不是實際寫回權威
                        // 狀態的手牌順序——實際寫回的是下面整理過的 organizedState，理由同
                        // StartGameUseCase KDoc。
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
                        val newState = dealtState.copy(players = organizedPlayers)
                        val advanceOutcome = AdvanceRoundOutcome(
                            result = AdvanceRoundResult(newState, isMatchOver = false),
                            diceRoll = initializationResult.diceRoll,
                            wallStructure = initializationResult.wallStructure,
                            dealOrderHandTileIdsBySeatIndex = dealOrderHandTileIdsBySeatIndex,
                        )
                        game.copy(
                            tableState = newState,
                            pendingTransition = null,
                            roundTransitionDirective = null,
                        ) to Outcome.Success(advanceOutcome)
                    }
                }
            }
        }

        if (outcome is Outcome.Error) return outcome
        val advanceOutcome = (outcome as Outcome.Success).value
        val result = advanceOutcome.result
        val newState = result.tableState

        if (result.isMatchOver) {
            // 對局已結束：仍要同步最終快照、廣播 MatchEnded，讓客戶端能讀到最終分數組出排名畫面，
            // 但不做開下一局才需要的 RoundStarted 廣播或擲骰／牌牆呈現。
            snapshotSynchronizer.syncAll(gameId)
            val lastDealerId = newState.players.first { it.currentWind == Wind.EAST }.id
            newState.players.forEach { player ->
                eventPublisher.publish(gameId, player.id, lastDealerId, GameAction.MatchEnded)
            }
            val presentationModule = moduleRegistry.getModule(newState.config)
            val finalRankById = newState.players.sortedWith(presentationModule.compareForMatchRanking())
                .mapIndexed { index, player -> player.id to index + 1 }
                .toMap()
            presentationPublisher.publishMatchSettlement(
                gameId,
                MatchSettlementPresentationRequest(
                    players = newState.players.mapIndexed { seatIndex, player ->
                        MatchSettlementPlayerPresentation(
                            playerId = player.id,
                            seatIndex = seatIndex,
                            isAi = player.isAi,
                            initialSeat = player.initialSeat,
                            finalScore = player.score,
                            finalRank = finalRankById.getValue(player.id),
                        )
                    },
                ),
            )
            return Outcome.Success(result)
        }

        // 2. 同步快照給所有正在觀察的玩家
        snapshotSynchronizer.syncAll(gameId)

        // 3. 廣播「下一局已開始」事件；RoundStarted 沒有實際執行者，比照 GameStarted 的既有慣例，
        //    填入新莊家的 Uuid
        val newDealerId = newState.players.first { it.currentWind == Wind.EAST }.id
        newState.players.forEach { player ->
            eventPublisher.publish(gameId, player.id, newDealerId, GameAction.RoundStarted)
        }

        // 4. 觸發平台呈現層：規則不支援開門流程時皆為 null，直接跳過。牌牆先建、骰子後擲，理由同
        // StartGameUseCase，這裡不能對調呼叫順序。
        val dealerSeatIndex = newState.players.indexOfFirst { player -> player.id == newDealerId }
        advanceOutcome.wallStructure?.let { structure ->
            val deadWallTileIds = newState.initialDeadWall.map { tile -> tile.id }.toSet()
            val diceCount = advanceOutcome.diceRoll?.values?.size ?: 0
            val revealedTileIds = (newState.dynamicRuleState as? TileWallRevealable)?.getVisibleTileIds(newState) ?: emptySet()
            presentationPublisher.publishWallStructure(gameId, structure, dealerSeatIndex, deadWallTileIds, diceCount, revealedTileIds)
        }
        advanceOutcome.diceRoll?.let { diceRoll ->
            presentationPublisher.publishDiceRoll(
                gameId,
                diceRoll,
                dealerSeatIndex,
                newState.roundNumber,
                newState.comboCount,
            )
            // 廣播擲骰點數本身；跟第 3 步的 RoundStarted 是兩則獨立事件，理由同 StartGameUseCase。
            newState.players.forEach { player ->
                eventPublisher.publish(gameId, player.id, newDealerId, GameAction.DiceRolled(diceRoll))
            }
        }
        // 積棒跟牌牆同時生成，緊接在 publishWallStructure 之後呼叫；新局手牌一定沒有副露，只是靠
        // publishInitialDealAnimation 的 comboStickCount 讓手牌正確讓開積棒佔用的空間。
        presentationPublisher.publishScoringSticksUpdated(gameId, dealerSeatIndex, newState.comboCount)
        // 立直宣告本身每局歸零（新局還沒有人宣告），但延續自前局、尚未被收下的供託堆要跟著顯示出來，
        // 不是無條件清空——流局後沒被收走的立直棒延續到下一局，見 GamePresentationPublisher KDoc。
        val module = moduleRegistry.getModule(newState.config)
        presentationPublisher.publishRiichiSticksUpdated(
            gameId,
            riichiSeatIndices = emptySet(),
            dealerSeatIndex = dealerSeatIndex,
            comboStickCount = newState.comboCount,
            pooledStickCount = module.getStickPotCount(newState),
        )
        presentationPublisher.publishRoundInfoUpdated(gameId, module.getRoundInfoLines(newState))
        // 翻牌完成那一刻起的最終落地格位——newState 此時已經是整理過的順序，跟決定發牌動畫節奏本身的
        // advanceOutcome.dealOrderHandTileIdsBySeatIndex 分開，見 MahjongInitialDealPresentation KDoc。
        val postFlipHandTileIdsBySeatIndex = newState.players.withIndex().associate { (seatIndex, player) ->
            seatIndex to player.hand.tiles.map { tile -> tile.id }
        }
        presentationPublisher.publishInitialDealAnimation(
            gameId,
            advanceOutcome.dealOrderHandTileIdsBySeatIndex,
            postFlipHandTileIdsBySeatIndex,
            dealerSeatIndex,
            comboStickCount = newState.comboCount,
            dealBatchSizes = newState.config.dealBatchSizes(),
            diceCount = advanceOutcome.diceRoll?.values?.size ?: 0,
        )
        beginRoundPreparationUseCase?.invoke(gameId)

        return Outcome.Success(result)
    }

    /**
     * [AdvanceRoundUseCase] 的執行結果。
     *
     * @property tableState 套用後的桌況——整場對局已結束時，除了「桌上未被收下的供託歸給最終第一名」
     *           這個結算之外維持呼叫前的桌況不變（不會開新的一局）；否則為已經開始下一局的新桌況。
     * @property isMatchOver 整場對局是否已依規則的 `GameLength` 結束。
     */
    data class AdvanceRoundResult(val tableState: TableState, val isMatchOver: Boolean)

    /**
     * [invoke] 內部使用的中介結果，把只有平台呈現層需要的一次性擲骰／牌牆結構資料，跟對外公開的
     * [AdvanceRoundResult] 分開夾帶，避免污染既有呼叫端（例如 `GameFlowCoordinator`）只關心的
     * 對外回傳形狀。[dealOrderHandTileIdsBySeatIndex] 是發牌動畫本身該用的原始時間軸順序，在
     * [result] 的手牌已經被整理過之後就無法再從它反推出來，理由同 `StartGameUseCase` KDoc；整場對局
     * 已結束（沒有開新的一局）時固定為空 map，不會被用到。
     */
    private data class AdvanceRoundOutcome(
        val result: AdvanceRoundResult,
        val diceRoll: DiceRollResult?,
        val wallStructure: Map<Uuid, TileWallPosition>?,
        val dealOrderHandTileIdsBySeatIndex: Map<Int, List<Uuid>>,
    )
}
