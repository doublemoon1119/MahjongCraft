package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiGameCommand
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RIICHI_GAME_ACTION
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.TablePresentationBusyTracker
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.MahjongTableRoomService
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
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
) {
    /** 對局命令與自動銜接失敗時的專用 logger。 */
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    /** 打出 [tileId] 這張牌。 */
    fun discard(player: ServerPlayerEntity, tileId: Uuid) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val gameId = resolveGameId(playerId) ?: return@launch
            val tile = findHandTile(gameId, playerId, tileId)
            execute(gameId, playerId, GameCommand.Discard(tileId), GameAction.Discard(tileId), tile)
        }
    }

    /** 宣告立直，同時打出 [tileId] 這張牌作為立直宣告牌。 */
    fun riichi(player: ServerPlayerEntity, tileId: Uuid) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val gameId = resolveGameId(playerId) ?: return@launch
            val tile = findHandTile(gameId, playerId, tileId)
            execute(gameId, playerId, GameCommand.Extension(RiichiGameCommand(tileId)), RIICHI_GAME_ACTION, tile)
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
            val command = when (GameActionCandidateResolver.resolvePendingMode(state, playerId)) {
                GamePendingMode.KAN_REACTION -> GameCommand.RespondToKan(candidate.action)
                GamePendingMode.DISCARD_REACTION -> GameCommand.RespondToDiscard(candidate.action)
                GamePendingMode.OWN_TURN -> toOwnTurnCommand(candidate.action)
                GamePendingMode.NONE -> null
            }
            if (command == null) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.IllegalGameAction)
                return@launch
            }
            execute(gameId, playerId, command, candidate.action, candidate.referenceTile)
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
                    turnStatus = GameActionCandidateResolver.resolvePendingMode(state, playerId).toTurnStatus(),
                    legalActions = legalActions,
                ),
            )
        }
    }

    /** 自己回合的合法動作清單只會出現 [GameAction.Tsumo]／[GameAction.Kan]／[GameAction.ExhaustiveDraw]。 */
    private fun toOwnTurnCommand(action: GameAction): GameCommand? = when (action) {
        GameAction.Tsumo -> GameCommand.Tsumo
        is GameAction.Kan -> GameCommand.Kan(action.type, action.tileId)
        is GameAction.ExhaustiveDraw -> GameCommand.DeclareExhaustiveDraw(action.reason)
        else -> null
    }

    /**
     * 分派 [command]，成功時先發布這次操作本身的成功回饋，才驅動 AI／強制自動操作連鎖與真人自動摸牌；
     * 失敗時發布對應錯誤回饋。順序很重要，且不只一處：
     * - [autoDrawService] 可能會在下一輪又輪回同一位玩家時發布 [MinecraftPlayerFeedback.YourTurn]，
     *   如果先呼叫它，「輪到你了」會搶在「已執行：打出 X」這句之前送達。
     * - 這次操作本身若觸發流局／連莊（例如打出的正好是最後一張牌，導致牌山耗盡），
     *   [GameFlowCoordinator.driveAutomatedPlayers] 內部會廣播回合結束事件；如果在驅動自動連鎖之前
     *   還沒發布這次操作的回饋，玩家會先看到「本局結束」才看到「已執行：打出 X」，讀起來像是這次
     *   操作發生在對局結束之後。
     *
     * 因此這裡刻意不用 [GameFlowCoordinator] 一次到位的 `invoke`，改拆成 [GameFlowCoordinator.dispatch]
     * （只分派這次操作，不驅動自動連鎖）→ 發布這次操作的回饋 → [GameFlowCoordinator.driveAutomatedPlayers]
     * （驅動 AI／強制自動操作直到輪到下一位真人為止）三個步驟依序執行。自動連鎖那段迴圈若拋出未預期
     * 例外，攔下來記錄，避免協程靜默死掉、對局卡在半途卻沒有任何 log 可查。
     */
    private suspend fun execute(gameId: Uuid, playerId: Uuid, command: GameCommand, action: GameAction, referenceTile: Tile?) {
        if (busyTracker.isBusy(gameId)) {
            feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.TableAnimationBusy)
            return
        }
        try {
            val result = gameFlowCoordinator.dispatch(gameId, playerId, command)
            when (result) {
                is Outcome.Success -> feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.GameActionPerformed(action, referenceTile))
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

    /** 從玩家目前手牌（含剛摸到的牌）中找出 [tileId] 對應的牌面，找不到時回傳 null。 */
    private suspend fun findHandTile(gameId: Uuid, playerId: Uuid, tileId: Uuid): Tile? {
        val state = gameRepository.getTableState(gameId) ?: return null
        val player = state.players.firstOrNull { it.id == playerId } ?: return null
        return player.hand.standingTiles.firstOrNull { it.id == tileId }?.tile
    }
}
