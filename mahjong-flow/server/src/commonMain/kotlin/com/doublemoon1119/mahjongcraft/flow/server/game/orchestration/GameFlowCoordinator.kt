package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingGameTransition
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationBusyGate
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.DecisionTimerSynchronizationService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.ExhaustiveDrawSettlementPresentationService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimerManager
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.AdvanceRoundUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareExhaustiveDrawUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareSuukanNagareUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.ReturnToRoomUseCase
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 在 [GameActionRouter] 之上，自動銜接 3 個系統觸發 use case（[DeclareExhaustiveDrawUseCase]、
 * [DeclareSuukanNagareUseCase]、[AdvanceRoundUseCase]）的呼叫時機，讓呼叫端只需要送出玩家的
 * [GameCommand]，不需要自己判斷「這個結果是不是代表本局已經結束、該推進到下一局了」。
 *
 * 未來真正的呼叫端（例如 Minecraft 平台層）應該呼叫這裡，而不是直接呼叫 [GameActionRouter]——
 * 後者只做單純分派，不含這裡的自動銜接邏輯。
 *
 * 三種銜接時機：
 * 1. **一般流局**：任一命令的結果為 [GameError.WallExhausted] 時，立即呼叫
 *    [declareExhaustiveDrawUseCase]（不開任何等待窗口——`WallExhausted` 代表玩家根本沒摸到牌，
 *    不存在「先讓玩家自摸」的中間狀態）。
 * 2. **槓後流局**：[GameCommand.Discard] 或明確宣告需要先結算槓後流局的 extension command，在送進
 *    [GameActionRouter] 前先詢問規則模組；若成立，原命令不會套用，改由規則既有流局流程收斂。
 * 3. **連莊/過莊**：任何造成本局結束的操作完成後，先將 [PendingGameTransition.AdvanceRound]
 *    寫入權威狀態；待呈現動畫結束後，再由 [resumePendingGameTransition] 呼叫 [advanceRoundUseCase]。
 *    任一命令成功後，統一從權威桌況的 `actionHistory` 判斷是否已留下胡牌或流局記錄，不依命令型別
 *    猜測規則結果，因此第三方 extension command 也能沿用相同收斂流程。
 *
 * 銜接呼叫皆為 best-effort：若銜接呼叫本身失敗，不會覆蓋原始命令的執行結果——玩家自己那次操作
 * 是否成功，跟後續系統銜接是否成功，是兩件事。
 *
 * 每次 [invoke] 執行完畢，還會額外透過 [aiTurnDriver] 讓所有輪到自己、或有資格回應且尚未回應的
 * AI 玩家依序自動行動，直到沒有任何 AI 需要行動為止——AI 背後沒有真人會主動送出命令，這一步
 * 讓加入房間的 AI 玩家真的能在牌局裡自動出手。詳見 [driveAutomatedPlayers]。
 *
 * @property gameActionRouter 玩家發起命令的路由入口。
 * @property gameRepository 權威對局數據倉庫，用於判斷是否需要銜接。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析四槓散了判定。
 * @property declareExhaustiveDrawUseCase 一般流局結算用例。
 * @property declareSuukanNagareUseCase 四槓散了結算用例。
 * @property advanceRoundUseCase 連莊/過莊/開下一局用例。
 * @property returnToRoomUseCase 對局結束後把桌子轉回房間用例；[advanceRoundUseCase] 判定對局結束時
 *   會先持久化 [PendingGameTransition.ReturnToRoom]，再於同一次待完成流程收斂中呼叫此用例。
 * @property aiTurnDriver 找出下一個該行動的 AI 玩家與其命令。
 * @property forcedAutoPlayDriver 找出下一個必須由伺服器固定操作的真人玩家與命令。
 * @property decisionTimerManager 在每次命令完成後結算並調整玩家決策計時器。
 * @property decisionTimerSynchronizationService 立即同步命令完成後的權威計時與停止狀態。
 * @property presentationBusyGate 查詢平台呈現層是否仍在播放動畫；[driveAutomatedPlayers] 與
 *   [resumePendingGameTransition] 都會在推進權威流程前檢查，忙碌時直接返回並留待後續心跳重試；
 *   實作由平台層提供，理由見 [GamePresentationBusyGate] KDoc。
 */
@Factory
class GameFlowCoordinator(
    private val gameActionRouter: GameActionRouter,
    private val extensionCommandRegistry: ExtensionGameCommandExecutorRegistry,
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val declareExhaustiveDrawUseCase: DeclareExhaustiveDrawUseCase,
    private val declareSuukanNagareUseCase: DeclareSuukanNagareUseCase,
    private val advanceRoundUseCase: AdvanceRoundUseCase,
    private val returnToRoomUseCase: ReturnToRoomUseCase,
    private val aiTurnDriver: AiTurnDriver,
    private val forcedAutoPlayDriver: ForcedAutoPlayDriver,
    private val decisionTimerManager: GameDecisionTimerManager,
    private val decisionTimerSynchronizationService: DecisionTimerSynchronizationService,
    private val exhaustiveDrawSettlementPresentationService: ExhaustiveDrawSettlementPresentationService,
    @Provided private val presentationBusyGate: GamePresentationBusyGate,
) {
    /** 避免玩家命令與恢復心跳同時重複執行同一個待完成流程。 */
    private val pendingTransitionMutex = Mutex()

    /**
     * 分派 [command] 並自動銜接對應的系統觸發 use case，完成後接著驅動所有需要行動的 AI 玩家
     * （見 [driveAutomatedPlayers]）。
     *
     * 大多數呼叫端應該用這個一次到位的入口；已進入強制自動操作的玩家一律拒絕，且完全不觸發自動連鎖
     * ——被拒絕的手動命令視為完全沒發生過，不應該有任何副作用。只有在呼叫端需要在「這次操作本身的
     * 結果」與「隨之而來的自動連鎖」之間插入自己的動作時（例如先發布這次操作成功的回饋訊息，再讓
     * 自動連鎖繼續跑，避免訊息順序看起來顛倒），才需要改拆成 [dispatch] + [driveAutomatedPlayers]
     * 分開呼叫；這種情況下呼叫端要自行決定拒絕時是否仍要驅動自動連鎖。
     *
     * @param gameId 對局 Uuid。
     * @param playerId 發起操作的玩家 Uuid。
     * @param command 欲執行的操作。
     * @return [GameActionRouter]（或四槓散了攔截時改呼叫的 [declareSuukanNagareUseCase]）的執行結果。
     */
    suspend operator fun invoke(
        gameId: Uuid,
        playerId: Uuid,
        command: GameCommand,
    ): Outcome<Unit, GameError> {
        val game = gameRepository.getGame(gameId)
            ?: return Outcome.Error(GameError.GameNotFound(gameId))
        if (playerId in game.forcedAutoPlayPlayerIds) {
            return Outcome.Error(GameError.ForcedAutoPlayActive(playerId, gameId))
        }
        val result = dispatchAndReconcile(gameId, playerId, command)
        driveAutomatedPlayers(gameId)
        return result
    }

    /**
     * 分派 [command] 並自動銜接對應的系統觸發 use case，**不會**接著驅動自動連鎖（見 [invoke] 與
     * [driveAutomatedPlayers]）——只有需要在兩者之間插入自己動作的呼叫端才需要直接呼叫這個方法，
     * 呼叫完後仍須自行接著呼叫 [driveAutomatedPlayers]，否則 AI／強制自動操作玩家不會被推進。
     *
     * 已進入強制自動操作（[Game.forcedAutoPlayPlayerIds]）
     * 的玩家一律拒絕——跟 [invoke] 共用同一條守門檢查；[driveAutomatedPlayers] 內部呼叫的是私有的
     * `dispatchAndReconcile`，替 AI／強制自動操作玩家送出命令時不會經過這裡、不會撞到這條檢查。
     *
     * @param gameId 對局 Uuid。
     * @param playerId 發起操作的玩家 Uuid。
     * @param command 欲執行的操作。
     * @return [GameActionRouter]（或四槓散了攔截時改呼叫的 [declareSuukanNagareUseCase]）的執行結果。
     */
    suspend fun dispatch(
        gameId: Uuid,
        playerId: Uuid,
        command: GameCommand,
    ): Outcome<Unit, GameError> {
        val game = gameRepository.getGame(gameId)
            ?: return Outcome.Error(GameError.GameNotFound(gameId))
        if (playerId in game.forcedAutoPlayPlayerIds) {
            return Outcome.Error(GameError.ForcedAutoPlayActive(playerId, gameId))
        }
        return dispatchAndReconcile(gameId, playerId, command)
    }

    /**
     * 依序驅動強制自動操作玩家與 AI，直到目前沒有任何自動決策需要執行。
     *
     * 每次迭代前後比較 `TableState`；若命令未造成進展便立即停止。理論上這個迴圈一定會自然收斂
     * （沒有更多自動決策要做，或桌況沒有任何進展），但仍設 [MAX_ITERATIONS] 作為上限而非單純
     * `while (true)`——純粹是防呆：萬一未來出現尚未發現的收斂性 bug，讓這裡真的陷入無限迴圈，
     * `while (true)` 會讓呼叫這個函式的心跳 tick 永遠卡住、吃滿 CPU 卻不留下任何訊號，而心跳是
     * 依序遍歷所有對局的，一局卡住會連帶讓同一個 tick 裡其他對局的自動操作全部停擺。設上限後，
     * 卡住會直接拋出例外，能被立即看見、定位。可由開局與逾時流程主動呼叫，確保沒有真人送出封包時
     * 仍能推進自動操作。
     *
     * 由 [forcedAutoPlayDriver] 解析出的動作在送出前會先把該玩家從[Game.forcedAutoPlayPlayerIds] 移除——
     * 強制自動操作只鎖住逾時當下那一次決策，不是整場對局；提前移除也讓緊接著呼叫的
     * [GameDecisionTimerManager.reconcile] 能立刻看到這位玩家重新是一般決策者，替他下一次決策
     * （例如緊接著要捨牌）建立帶有完整 `baseSeconds` 的新計時器，而不是繼續被排除在外。
     *
     * 每次迭代開始前都會用 [presentationBusyGate] 確認這桌目前沒有正在播放呈現動畫——不是只在呼叫
     * 這個函式之前檢查一次：連莊/過莊本身就可能在迴圈中途觸發新一局的擲骰動畫（[advanceRoundUseCase]
     * 銜接呼叫），如果只在最外層檢查一次，迴圈仍會在同一次呼叫裡繼續驅動新莊家的自動摸牌，讓新一局的
     * 擲骰動畫還沒播完，遊戲流程就已經搶跑；改成每次迭代都檢查，動畫開始播放後迴圈會在下一次迭代前
     * 自然停下，等下次心跳或玩家操作再重新呼叫。
     *
     * @param gameId 欲推進的遊戲。
     * @throws IllegalStateException 跑滿 [MAX_ITERATIONS] 步仍未收斂，代表自動操作鏈路真的卡住了。
     */
    suspend fun driveAutomatedPlayers(gameId: Uuid) {
        repeat(MAX_ITERATIONS) {
            if (presentationBusyGate.isBusy(gameId)) return
            if (resumePendingGameTransition(gameId)) {
                val resumedGame = gameRepository.getGame(gameId) ?: return
                if (resumedGame.pendingTransition != null || presentationBusyGate.isBusy(gameId)) return
                return@repeat
            }
            val forcedAction = forcedAutoPlayDriver.resolveNextAction(gameId)
            val (playerId, command) = forcedAction ?: aiTurnDriver.resolveNextAction(gameId) ?: return
            if (forcedAction != null) clearForcedAutoPlay(gameId, playerId)
            val stateBefore = gameRepository.getTableState(gameId)
            dispatchAndReconcile(gameId, playerId, command)
            if (presentationBusyGate.isBusy(gameId)) return
            if (resumePendingGameTransition(gameId)) {
                val resumedGame = gameRepository.getGame(gameId) ?: return
                if (resumedGame.pendingTransition != null || presentationBusyGate.isBusy(gameId)) return
                return@repeat
            }
            val stateAfter = gameRepository.getTableState(gameId)
            if (stateBefore == stateAfter) return
        }
        error(
            "driveAutomatedPlayers did not converge for game $gameId after $MAX_ITERATIONS iterations; " +
                "automated player chain is likely stuck",
        )
    }

    /**
     * 收斂權威狀態中尚待完成的遊戲流程。
     *
     * [Game.pendingTransition] 會持久化，因此正常執行期間與 server 重啟後都可由平台心跳重複呼叫
     * 本方法：根據明確的 [PendingGameTransition] 補做 [advanceRoundUseCase] 或
     * [returnToRoomUseCase]，不從桌況或呈現實作反推權威意圖。
     *
     * 方法只在呈現已經閒置時收斂，不會自行掛起等待，適合每次心跳輪詢。回傳 `true`
     * 代表偵測到待完成的流程（即使這次 best-effort use case 失敗），呼叫端這一輪不應再驅動
     * 任何自動操作，留待下次心跳重試。
     *
     * @param gameId 欲收斂待完成流程的對局。
     * @return 是否偵測到待完成的遊戲流程。
     */
    suspend fun resumePendingGameTransition(gameId: Uuid): Boolean {
        if (presentationBusyGate.isBusy(gameId)) return false
        pendingTransitionMutex.lock()
        try {
            val transition = gameRepository.getGame(gameId)?.pendingTransition ?: return false
            when (transition) {
                PendingGameTransition.AdvanceRound -> {
                    val result = advanceRoundUseCase(gameId)
                    if (result is Outcome.Success && result.value.isMatchOver) {
                        // TODO: 導入 MatchSettlementPresentationEntity 後，流程應為：
                        // ExhaustiveDrawSettlementPresentationEntity → AdvanceRound → 判定 Match Over
                        // → MatchSettlementPresentationEntity → ReturnToRoom。整場結算展示結束前不可立即回房。
                        returnToRoomUseCase(gameId)
                    }
                }

                PendingGameTransition.ReturnToRoom -> {
                    // TODO: 未來須先等待 MatchSettlementPresentationEntity 完成，再執行 ReturnToRoom。
                    returnToRoomUseCase(gameId)
                }
            }
            return true
        } finally {
            pendingTransitionMutex.unlock()
        }
    }

    /**
     * 分派命令與系統銜接完成後，依最終權威桌況調整決策計時器。
     *
     * 成功命令會將 [playerId] 視為完成一次決策，結算舊 timer；失敗命令只進行狀態校正，不重設該玩家
     * 已存在的基本思考時間。機械摸牌成功前沒有 timer，但完成後仍會透過相同流程建立自己回合的新 timer。
     */
    private suspend fun dispatchAndReconcile(
        gameId: Uuid,
        playerId: Uuid,
        command: GameCommand,
    ): Outcome<Unit, GameError> {
        val result = dispatchAndChain(gameId, playerId, command)
        val statuses = decisionTimerManager.reconcile(
            gameId = gameId,
            completedPlayerId = playerId.takeIf { result is Outcome.Success },
        )
        decisionTimerSynchronizationService.synchronize(gameId, statuses)
        return result
    }

    /**
     * 把 [playerId] 從強制自動操作名單移除，讓他下一次決策恢復由自己（或再次逾時後重新進入名單）
     * 決定，而不是繼續被伺服器代打。
     */
    private suspend fun clearForcedAutoPlay(gameId: Uuid, playerId: Uuid) {
        gameRepository.updateGame(gameId) { game ->
            (game?.copy(forcedAutoPlayPlayerIds = game.forcedAutoPlayPlayerIds - playerId) ?: game) to Unit
        }
    }

    /**
     * 分派 [command] 並自動銜接對應的系統觸發 use case。抽出成獨立方法讓 [driveAutomatedPlayers] 能
     * 直接呼叫——AI 送出的命令也必須經過同一套四槓散了攔截與系統銜接邏輯，不能繞過去。
     */
    private suspend fun dispatchAndChain(gameId: Uuid, playerId: Uuid, command: GameCommand): Outcome<Unit, GameError> {
        if (
            command is GameCommand.Discard ||
            command is GameCommand.Extension &&
            extensionCommandRegistry.resolvesPendingKanDrawBeforeExecution(command.value)
        ) {
            redirectToSuukanNagareIfPending(gameId, playerId)?.let { return it }
        }

        val previousState = gameRepository.getTableState(gameId)
            ?: return Outcome.Error(GameError.GameNotFound(gameId))
        val result = gameActionRouter(gameId, playerId, command)

        if (result is Outcome.Error && result.error is GameError.WallExhausted) {
            if (declareExhaustiveDrawUseCase(gameId) is Outcome.Success) {
                val currentState = gameRepository.getTableState(gameId)
                val module = moduleRegistry.getModule(previousState.config)
                val settlement = module.declareExhaustiveDraw(previousState)
                if (currentState != null && settlement != null) {
                    exhaustiveDrawSettlementPresentationService.publish(
                        gameId,
                        previousState,
                        currentState,
                        module,
                        settlement.reason,
                        settlement.tenpaiPlayerIds.takeIf { settlement.stickPotCollectorPlayerIds.isEmpty() },
                        settlement.revealedHands,
                    )
                }
                chainAdvanceRound(gameId)
            }
            return result
        }

        if (result is Outcome.Success && hasCommandEndedHand(gameId)) {
            publishNewAbortiveDrawIfPresent(gameId, playerId, previousState)
            chainAdvanceRound(gameId)
        }

        return result
    }

    /**
     * 持久化「待進入下一局」意圖後立即返回；真正的 [advanceRoundUseCase] 由平台心跳在呈現結束後
     * 透過 [resumePendingGameTransition] 收斂。三個呼叫點（一般流局、四槓散了、一般結束本局判斷）
     * 共用這個方法，正常遊玩與 server 重啟恢復走完全相同的路徑。
     */
    private suspend fun chainAdvanceRound(gameId: Uuid) {
        gameRepository.updateGame(gameId) { game ->
            game?.copy(pendingTransition = PendingGameTransition.AdvanceRound) to Unit
        }
    }

    /**
     * 若 [playerId] 正輪到自己回合、沒有任何反應視窗開著、且四槓散了已成立，改呼叫
     * [declareSuukanNagareUseCase] 並嘗試銜接 [advanceRoundUseCase]，回傳這次改呼叫的結果；
     * 否則回傳 null，代表呼叫端應照原命令正常分派給 [gameActionRouter]。
     */
    private suspend fun redirectToSuukanNagareIfPending(gameId: Uuid, playerId: Uuid): Outcome<Unit, GameError>? {
        val state = gameRepository.getTableState(gameId) ?: return null
        if (state.currentPlayer.id != playerId) return null
        if (state.pendingReaction != null || state.pendingKanReaction != null) return null

        val module = moduleRegistry.getModule(state.config)
        if (module.resolveSuukanNagare(state) == null) return null

        val result = declareSuukanNagareUseCase(gameId)
        if (result is Outcome.Success) {
            publishNewAbortiveDrawIfPresent(gameId, null, state)
            chainAdvanceRound(gameId)
        }
        return result
    }

    /** 若這次狀態變更新增途中流局記錄，建立統一回合結算呈現。 */
    private suspend fun publishNewAbortiveDrawIfPresent(
        gameId: Uuid,
        declarerId: Uuid?,
        previousState: TableState,
    ) {
        val currentState = gameRepository.getTableState(gameId) ?: return
        val previousReasons = previousState.players.flatMap { player ->
            player.actionHistory.filterIsInstance<GameAction.ExhaustiveDraw>().map { it.reason }
        }.toSet()
        val reason = currentState.players.asSequence()
            .flatMap { it.actionHistory.asSequence() }
            .filterIsInstance<GameAction.ExhaustiveDraw>()
            .map { it.reason }
            .firstOrNull { it !in previousReasons }
            ?: return
        val module = moduleRegistry.getModule(currentState.config)
        exhaustiveDrawSettlementPresentationService.publish(
            gameId = gameId,
            previousState = previousState,
            currentState = currentState,
            module = module,
            reason = reason,
            tenpaiPlayerIds = null,
            revealedHands = module.resolveAbortiveDrawRevealedHands(currentState, declarerId, reason),
        )
    }

    /**
     * [command] 成功執行後，是否可能已經結束本局，需要銜接 [advanceRoundUseCase]。
     */
    private suspend fun hasCommandEndedHand(gameId: Uuid): Boolean {
        val state = gameRepository.getTableState(gameId)
        return state?.hasEndedHand() == true
    }

    /** 本局是否已留下可持久化的結束記錄，可供重啟後重建尚未完成的推進。 */
    private fun TableState.hasEndedHand(): Boolean = players.any { player ->
        player.actionHistory.any { action ->
            action is GameAction.Tsumo || action is GameAction.Ron || action is GameAction.ExhaustiveDraw
        }
    }

    private companion object {
        /** [driveAutomatedPlayers] 的最大迭代次數，避免收斂性 bug 讓自動操作鏈路無限跑下去。 */
        const val MAX_ITERATIONS = 5000
    }
}
