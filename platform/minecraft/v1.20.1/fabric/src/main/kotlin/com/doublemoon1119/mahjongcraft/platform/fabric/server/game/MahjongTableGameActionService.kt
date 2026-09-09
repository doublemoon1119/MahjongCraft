package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSubmission
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiGameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionSelectionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionSelectionKindDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoundPreparationPromptDto
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerActionContext
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerActionContextResolver
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.TablePresentationBusyTracker
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.MahjongTableRoomService
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.GameTurnStatus
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedbackPublisher
import kotlinx.coroutines.launch
import net.minecraft.server.network.ServerPlayerEntity
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * 把 Minecraft 玩家的對局階段操作（出牌／立直／吃碰槓胡過／九種九牌）路由到既有的
 * [GameFlowCoordinator]。
 * 比照 [MahjongTableRoomService] 的既有風格：直接呼叫 use case／coordinator，
 * 不透過 [MahjongChannels.gameCommand]——那個頻道
 * 是給以後真的有 client 端 GUI 時使用，指令執行緒本身就在伺服器上，不需要繞經它。
 *
 * 摸牌（[GameCommand.Draw]）不在這裡開放——已經由 [MahjongAutoDrawService]／`AiTurnDriver` 全自動
 * 觸發，玩家不需要、也不能手動摸牌。
 *
 * @property scope 承接指令觸發的 suspend 呼叫。
 * @property gameRepository 權威對局數據倉庫，用於解析手牌／桌況。
 * @property membershipRepository 玩家目前桌子（房間／對局共用同一個 Uuid）的歸屬查詢。
 * @property gameFlowCoordinator 對局命令的分派與自動銜接入口。
 * @property autoDrawService 命令成功後補做真人玩家的自動摸牌檢查（`driveAutomatedPlayers` 已經包在
 *   [GameFlowCoordinator.invoke] 內部，不需要另外呼叫）。
 * @property candidateResolver 查詢目前合法動作清單，供 [showHand] 使用；跟 `action` 指令 Tab 補全共用
 *   同一份查詢，確保手牌畫面顯示的候選跟玩家實際能輸入的候選一致。
 * @property feedbackPublisher 操作結果的一次性回饋。
 * @property busyTracker 查詢該桌是否正在播放呈現動畫（例如擲骰），播放期間擋下玩家操作，避免畫面出現
 *   跟動畫不一致的桌況。
 * @property presentationPublisher 選牌需要多選（`maxCount > 1`）時，通知平台呈現層生成／清除選牌確認
 *   面板 entity。
 * @property actionContextResolver 玩家目前操作情境的權威解析器。
 */
@Single
class MahjongTableGameActionService(
    private val scope: AppCoroutineScope,
    private val gameRepository: GameRepository,
    private val membershipRepository: PlayerMembershipRepository,
    private val gameFlowCoordinator: GameFlowCoordinator,
    private val autoDrawService: MahjongAutoDrawService,
    private val candidateResolver: GameActionCandidateResolver,
    private val feedbackPublisher: MinecraftPlayerFeedbackPublisher,
    private val busyTracker: TablePresentationBusyTracker,
    private val promptFactory: PlayerDecisionPromptFactory,
    private val presentationPublisher: GamePresentationPublisher,
    private val actionContextResolver: PlayerActionContextResolver,
) {
    /** 對局命令與自動銜接失敗時的專用 logger。 */
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    /**
     * 處理實體手牌右鍵；立直等「宣告 + 選牌」動作由操作 HUD 端在進入選牌模式後直接送出
     * [PlayerDecisionSelectionKindDto.ACTION]（見 [select]），不會經過這裡，故一律視為一般捨牌。
     */
    fun interactWithHandTile(player: ServerPlayerEntity, tileId: Uuid) {
        discard(player, tileId)
    }

    /** 驗證 decision key 後執行操作 HUD 提交的受控選擇。 */
    fun select(player: ServerPlayerEntity, selection: PlayerDecisionSelectionDto) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val gameId = runCatching { Uuid.parse(selection.gameId) }.getOrNull() ?: return@launch
            val game = gameRepository.getGame(gameId) ?: return@launch
            val state = game.tableState
            val preparation = game.pendingRoundPreparation
            val phase = if (
                preparation != null &&
                playerId in preparation.participantPlayerIds &&
                playerId !in preparation.completedPlayerIds
            ) {
                PlayerDecisionPhase.ROUND_PREPARATION
            } else {
                actionContextResolver.resolveFor(state, playerId)?.phase ?: return@launch
            }
            val prompt = promptFactory.create(gameId, playerId, phase) ?: return@launch
            if (prompt.decisionKey != selection.decisionKey) return@launch
            when (selection.kind) {
                PlayerDecisionSelectionKindDto.ACTION -> {
                    if (selection.token == RIICHI_ACTION_TOKEN) {
                        // 立直目前是唯一「宣告 + 額外選牌」的動作，直接在這裡處理；若未來有第二個規則
                        // 模組也需要類似流程，再抽成通用 dispatch 機制。實際合法性（含選牌是否合法）
                        // 由 DeclareRiichiUseCase 權威驗證，這裡不重複判斷。
                        val tileId = selection.tileIds.singleOrNull()?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                            ?: return@launch
                        riichi(player, tileId)
                    } else {
                        val candidate = candidateResolver.listActionCandidates(playerId).firstOrNull { it.token == selection.token }
                            ?: return@launch
                        act(player, candidate)
                    }
                    presentationPublisher.publishTileSelectionEnded(gameId, playerId)
                }

                PlayerDecisionSelectionKindDto.PREPARATION_CONFIRM -> gameFlowCoordinator(
                    gameId,
                    playerId,
                    GameCommand.SubmitRoundPreparation(RoundPreparationSubmission.Confirmed),
                )

                PlayerDecisionSelectionKindDto.PREPARATION_CHOICE -> {
                    val option = selection.token ?: return@launch
                    gameFlowCoordinator(
                        gameId,
                        playerId,
                        GameCommand.SubmitRoundPreparation(RoundPreparationSubmission.Choice(option)),
                    )
                }

                PlayerDecisionSelectionKindDto.PREPARATION_TILES -> {
                    val tileIds = selection.tileIds.mapNotNull { runCatching { Uuid.parse(it) }.getOrNull() }.toSet()
                    gameFlowCoordinator(
                        gameId,
                        playerId,
                        GameCommand.SubmitRoundPreparation(RoundPreparationSubmission.Tiles(tileIds)),
                    )
                    presentationPublisher.publishTileSelectionEnded(gameId, playerId)
                }

                PlayerDecisionSelectionKindDto.BEGIN_TILE_SELECTION -> {
                    // 只有 maxCount > 1 才需要生成確認面板；maxCount == 1 維持右鍵合法牌直接自動送出，
                    // 伺服器端重新驗證 client 宣稱的情境是否真的需要選超過一張牌，不盲信 client。
                    val maxCount = if (selection.token == null) {
                        (prompt.preparation as? RoundPreparationPromptDto.TileSelection)?.maxCount
                    } else {
                        prompt.actions.firstOrNull { it.token == selection.token }?.tileSelection?.maxCount
                    }
                    if (maxCount != null && maxCount > 1) {
                        presentationPublisher.publishTileSelectionStarted(gameId, playerId)
                    }
                }
            }
        }
    }

    /** 打出 [tileId] 這張牌。 */
    fun discard(player: ServerPlayerEntity, tileId: Uuid) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val gameId = resolveGameId(playerId) ?: return@launch
            execute(gameId, playerId, GameCommand.Discard(tileId))
        }
    }

    /** 宣告立直，同時打出 [tileId] 這張牌作為立直宣告牌。 */
    fun riichi(player: ServerPlayerEntity, tileId: Uuid) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val gameId = resolveGameId(playerId) ?: return@launch
            execute(gameId, playerId, GameCommand.Extension(RiichiGameCommand(tileId)))
        }
    }

    /** 執行玩家從 [GameActionCandidateResolver.listActionCandidates] 選出的候選動作。 */
    fun act(player: ServerPlayerEntity, candidate: GameActionCandidate) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val gameId = resolveGameId(playerId) ?: return@launch
            val state = gameRepository.getTableState(gameId)
            if (state == null) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerNotInGame)
                return@launch
            }
            val command = actionContextResolver.resolveFor(state, playerId)?.toGameCommand(candidate.action)
            if (command == null) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.IllegalGameAction)
                return@launch
            }
            execute(gameId, playerId, command)
        }
    }

    /** 顯示玩家目前的手牌、副露與可執行的特殊動作。 */
    fun showHand(player: ServerPlayerEntity) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val gameId = resolveGameId(playerId) ?: return@launch
            val state = gameRepository.getTableState(gameId)
            if (state == null) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerNotInGame)
                return@launch
            }
            val playerState = state.players.firstOrNull { it.id == playerId }
            if (playerState == null) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerNotInGame)
                return@launch
            }
            val legalActions = candidateResolver.listActionCandidates(playerId).map { it.action to it.referenceTile }
            feedbackPublisher.publish(
                playerId,
                MinecraftPlayerFeedback.ShowHand(
                    standingTiles = playerState.hand.standingTiles.map { it.tile },
                    melds = playerState.hand.exposedMelds,
                    turnStatus = actionContextResolver.resolveFor(state, playerId).toTurnStatus(),
                    legalActions = legalActions,
                ),
            )
        }
    }

    /** 分派動作並驅動後續流程；實體捨牌本身已提供明確回饋，因此不再額外傳送成功聊天訊息。 */
    private suspend fun execute(gameId: Uuid, playerId: Uuid, command: GameCommand) {
        if (busyTracker.isBusy(gameId)) {
            feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.TableAnimationBusy)
            return
        }
        try {
            val result = gameFlowCoordinator.dispatch(gameId, playerId, command)
            when (result) {
                is Outcome.Success -> Unit
                is Outcome.Error -> feedbackPublisher.publish(playerId, MinecraftGameFeedbackResolver.actionError(result.error))
            }
            // 被拒絕的命令視為完全沒發生過，不驅動自動連鎖——跟 GameFlowCoordinator.invoke() 對
            // ForcedAutoPlayActive 的既有處理一致。其餘成功／失敗結果都要驅動，即使這位玩家的命令
            // 失敗，其他 AI／強制自動操作玩家仍然可能有動作要做。
            if (result !is Outcome.Error || result.error !is GameError.ForcedAutoPlayActive) {
                gameFlowCoordinator.driveAutomatedPlayers(gameId)
            }
            if (result is Outcome.Success) autoDrawService.checkAndAutoDraw(gameId)
        } catch (throwable: Throwable) {
            logger.error("Failed to execute game command {} for player {} in game {}", command, playerId, gameId, throwable)
        }
    }

    /** 以玩家目前的房間歸屬解析目標對局；不在任何桌子或桌況不是對局時發布 [MinecraftPlayerFeedback.PlayerNotInGame] 並回傳 null。 */
    private suspend fun resolveGameId(playerId: Uuid): Uuid? {
        val tableId = membershipRepository.getTableId(playerId)
        if (tableId == null || gameRepository.getTableState(tableId) == null) {
            feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerNotInGame)
            return null
        }
        return tableId
    }
}

/** 對應到手牌查詢顯示使用的粗粒度回合狀態。 */
internal fun PlayerActionContext?.toTurnStatus(): GameTurnStatus = when (this) {
    is PlayerActionContext.KanReaction, is PlayerActionContext.DiscardReaction -> GameTurnStatus.AWAITING_RESPONSE
    is PlayerActionContext.OwnTurn -> GameTurnStatus.OWN_TURN
    null -> GameTurnStatus.WAITING
}

/** 依玩家操作情境把合法動作包裝成對應的對局命令。 */
internal fun PlayerActionContext.toGameCommand(action: GameAction): GameCommand? = when (this) {
    is PlayerActionContext.KanReaction -> GameCommand.RespondToKan(action)
    is PlayerActionContext.DiscardReaction -> GameCommand.RespondToDiscard(action)
    is PlayerActionContext.OwnTurn -> action.toOwnTurnCommand()
}

/** 將自己回合的額外合法動作轉換成對應命令，不支援的動作回傳 null。 */
private fun GameAction.toOwnTurnCommand(): GameCommand? = when (this) {
    GameAction.Tsumo -> GameCommand.Tsumo
    is GameAction.Kan -> GameCommand.Kan(type, tileId)
    is GameAction.ExhaustiveDraw -> GameCommand.DeclareExhaustiveDraw(reason)
    else -> null
}
