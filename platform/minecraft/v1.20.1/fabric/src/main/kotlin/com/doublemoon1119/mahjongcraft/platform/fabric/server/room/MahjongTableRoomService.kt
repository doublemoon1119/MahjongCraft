package com.doublemoon1119.mahjongcraft.platform.fabric.server.room

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomScreenActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.TableLobbyPayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.TableLobbyPhaseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.toDto
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.StartGameUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.SyncGameSnapshotUseCase
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.AddAiPlayerUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.ChangeAiStrategyUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.CreateRoomUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.JoinRoomUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.KickPlayerUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.LeaveRoomUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.SyncRoomSnapshotUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.ToggleReadyUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.UpdateConfigUseCase
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.MahjongAutoDrawService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.GameSnapshotSender
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.RoomSnapshotSender
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedbackPublisher
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.server.network.ServerPlayerEntity
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * 把 Minecraft 麻將桌互動路由到既有 Room／Game use case 的正式平台進場服務。
 *
 * 目前由右鍵／蹲下右鍵桌子（[interact]／[leave]）與 `/mahjongcraft` 玩家指令
 * （[ready]／[start]）共用；日後若互動方式改成懸浮 HUD 等其他觸發，只需要新增呼叫端，這裡的方法
 * 本身不需要更動。
 */
@Single
class MahjongTableRoomService(
    private val scope: AppCoroutineScope,
    private val roomRepository: RoomRepository,
    private val roomSnapshotRepository: RoomSnapshotRepository,
    private val gameRepository: GameRepository,
    private val membershipRepository: PlayerMembershipRepository,
    private val createRoom: CreateRoomUseCase,
    private val joinRoom: JoinRoomUseCase,
    private val leaveRoom: LeaveRoomUseCase,
    private val toggleReady: ToggleReadyUseCase,
    private val startGame: StartGameUseCase,
    private val addAiPlayer: AddAiPlayerUseCase,
    private val changeAiStrategy: ChangeAiStrategyUseCase,
    private val kickPlayer: KickPlayerUseCase,
    private val updateConfig: UpdateConfigUseCase,
    private val syncRoom: SyncRoomSnapshotUseCase,
    private val syncGame: SyncGameSnapshotUseCase,
    private val roomSnapshotSender: RoomSnapshotSender,
    private val gameSnapshotSender: GameSnapshotSender,
    private val feedbackPublisher: MinecraftPlayerFeedbackPublisher,
    private val tableLocationRegistry: TableLocationRegistry,
    private val memberCandidateResolver: RoomMemberCandidateResolver,
    private val gameFlowCoordinator: GameFlowCoordinator,
    private val autoDrawService: MahjongAutoDrawService,
    private val serverHolder: FabricServerHolder,
    @Provided private val json: Json,
    @Provided private val networkRegistries: NetworkDtoRegistries,
) {
    /** 將 RoomScreen 的強型別操作路由到既有權威 use case；封包中的玩家身分一律忽略。 */
    fun handleRoomScreenAction(player: ServerPlayerEntity, action: RoomScreenActionDto) {
        val tableId = runCatching { Uuid.parse(action.tableId) }.getOrNull() ?: return
        when (action) {
            is RoomScreenActionDto.Create -> createFromScreen(tableId, player)
            is RoomScreenActionDto.Join -> joinFromScreen(tableId, player)
            is RoomScreenActionDto.ToggleReady -> withMatchingMembership(player, tableId, ::ready)
            is RoomScreenActionDto.Start -> withMatchingMembership(player, tableId, ::start)
            is RoomScreenActionDto.Leave,
            is RoomScreenActionDto.Disband,
            -> leaveFromScreen(tableId, player)
            is RoomScreenActionDto.Close -> closeRoomScreen(tableId, player)
            is RoomScreenActionDto.AddAi -> withMatchingMembership(player, tableId) { addAi(it, action.strategyKey) }
            is RoomScreenActionDto.ChangeAiStrategy -> withMatchingMembership(player, tableId) {
                val targetId = runCatching { Uuid.parse(action.targetPlayerId) }.getOrNull() ?: return@withMatchingMembership
                changeAiStrategy(it, targetId, action.strategyKey)
            }
            is RoomScreenActionDto.Kick -> withMatchingMembership(player, tableId) {
                val targetId = runCatching { Uuid.parse(action.targetPlayerId) }.getOrNull() ?: return@withMatchingMembership
                kick(it, targetId)
            }
            is RoomScreenActionDto.UpdateConfig -> withMatchingMembership(player, tableId) {
                updateConfig(it, action.config.toDomain(networkRegistries))
            }
        }
    }

    /** 關閉畫面時只清除非成員建立的暫時 observer。 */
    private fun closeRoomScreen(tableId: Uuid, player: ServerPlayerEntity) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val room = roomRepository.getRoom(tableId)
            if (room == null || playerId !in room.playerIds) {
                roomSnapshotRepository.removeSnapshot(tableId, playerId)
            }
        }
    }

    /** 防止客戶端以另一張桌子的 ID 操作自己目前所在的房間。 */
    private fun withMatchingMembership(player: ServerPlayerEntity, tableId: Uuid, action: (ServerPlayerEntity) -> Unit) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            if (membershipRepository.getTableId(playerId) == tableId) {
                action(player)
            } else {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerNotInGame)
            }
        }
    }

    /** 只同步桌子的公開 lobby 狀態並開啟 RoomScreen，不建立、加入或離開房間。 */
    fun openRoomScreen(table: MahjongTableBlockEntity, player: ServerPlayerEntity) {
        val tableId = table.tableId
        val playerId = player.uuid.toKotlinUuid()
        val location = tableLocationRegistry.get(tableId)?.location
        scope.launch {
            val runtimeGame = gameRepository.getGame(tableId)
            val game = runtimeGame?.tableState
            if (game != null) {
                MahjongChannels.tableLobby.sendTo(
                    player,
                    json,
                    TableLobbyPayloadDto(
                        tableId = tableId.toString(),
                        phase = TableLobbyPhaseDto.PLAYING,
                        playingPlayerIds = game.players.map { it.id.toString() },
                        playingAiPlayerIds = game.players.filter { it.isAi }.map { it.id.toString() },
                        playingGameConfig = GameConfig(game.config, runtimeGame.flowConfig).toDto(networkRegistries),
                        dimensionId = location?.dimensionId,
                        tableX = location?.x,
                        tableY = location?.y,
                        tableZ = location?.z,
                    ),
                )
                return@launch
            }

            val room = roomRepository.getRoom(tableId)
            if (room == null) {
                MahjongChannels.tableLobby.sendTo(
                    player,
                    json,
                    TableLobbyPayloadDto(
                        tableId.toString(),
                        TableLobbyPhaseDto.EMPTY,
                        dimensionId = location?.dimensionId,
                        tableX = location?.x,
                        tableY = location?.y,
                        tableZ = location?.z,
                    ),
                )
                return@launch
            }

            syncRoom(tableId, playerId)
            roomSnapshotSender.send(tableId, playerId)
            MahjongChannels.tableLobby.sendTo(
                player,
                json,
                TableLobbyPayloadDto(
                    tableId.toString(),
                    TableLobbyPhaseDto.WAITING,
                    room.toSnapshot(playerId).toDto(networkRegistries),
                    dimensionId = location?.dimensionId,
                    tableX = location?.x,
                    tableY = location?.y,
                    tableZ = location?.z,
                ),
            )
        }
    }

    /** RoomScreen 明確建立空桌房間。 */
    fun createFromScreen(tableId: Uuid, player: ServerPlayerEntity) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            if (!isTableReachable(tableId, player)) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.TableNotReachable)
                return@launch
            }
            when (val result = createRoom(tableId, playerId, GameConfig(RiichiRuleConfig()))) {
                is Outcome.Success -> {
                    syncRoom(tableId, playerId)
                    roomSnapshotSender.send(tableId, playerId)
                    feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.GameCreated(tableLocationRegistry.get(tableId)?.location))
                }
                is Outcome.Error -> publishRoomError(playerId, result.error)
            }
        }
    }

    /** RoomScreen 明確加入既有等待房間。 */
    fun joinFromScreen(tableId: Uuid, player: ServerPlayerEntity) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            if (!isTableReachable(tableId, player)) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.TableNotReachable)
                return@launch
            }
            syncRoom(tableId, playerId)
            when (val result = joinRoom(tableId, playerId)) {
                is Outcome.Success -> feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.GameJoined)
                is Outcome.Error -> publishRoomError(playerId, result.error)
            }
        }
    }

    /** 建立與加入屬於桌旁操作，拒絕偽造的遠端 table ID。 */
    private fun isTableReachable(tableId: Uuid, player: ServerPlayerEntity): Boolean {
        val location = tableLocationRegistry.get(tableId)?.location ?: return false
        if (player.serverWorld.registryKey.value.toString() != location.dimensionId) return false
        return player.squaredDistanceTo(location.x + 0.5, location.y + 0.5, location.z + 0.5) <= MAX_ROOM_SCREEN_DISTANCE_SQUARED
    }

    /** RoomScreen 明確離開等待房間；房主會沿用既有語意解散房間。 */
    private fun leaveFromScreen(tableId: Uuid, player: ServerPlayerEntity) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            if (membershipRepository.getTableId(playerId) != tableId) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerNotInGame)
                return@launch
            }
            val room = roomRepository.getRoom(tableId)
            if (room == null) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.GameLeaveDeniedWhilePlaying)
                return@launch
            }
            val wasHost = room.hostId == playerId
            when (leaveRoom(tableId, playerId)) {
                is Outcome.Success -> {
                    feedbackPublisher.publish(playerId, MinecraftRoomFeedbackResolver.successfulLeave(wasHost))
                    if (wasHost) {
                        roomSnapshotRepository.removeSnapshot(tableId, playerId)
                        MahjongChannels.tableLobby.sendTo(
                            player,
                            json,
                            TableLobbyPayloadDto(tableId.toString(), TableLobbyPhaseDto.EMPTY),
                        )
                    } else {
                        val updatedRoom = roomRepository.getRoom(tableId)
                        if (updatedRoom != null) {
                            syncRoom(tableId, playerId)
                            roomSnapshotSender.send(tableId, playerId)
                            MahjongChannels.tableLobby.sendTo(
                                player,
                                json,
                                TableLobbyPayloadDto(
                                    tableId.toString(),
                                    TableLobbyPhaseDto.WAITING,
                                    updatedRoom.toSnapshot(playerId).toDto(networkRegistries),
                                ),
                            )
                        }
                    }
                }
                is Outcome.Error -> feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.GameLeaveFailed)
            }
        }
    }

    /** 依麻將桌目前處於空桌、等待室或遊戲階段，建立、加入或重新同步玩家狀態。 */
    fun interact(table: MahjongTableBlockEntity, player: ServerPlayerEntity) {
        val tableId = table.tableId
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val game = gameRepository.getTableState(tableId)
            if (game != null) {
                if (game.players.none { it.id == playerId }) {
                    feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.GameAlreadyStarted)
                    return@launch
                }
                if (!membershipRepository.claim(playerId, tableId)) {
                    feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerAlreadyInGame)
                    return@launch
                }
                syncGame(tableId, playerId)
                gameSnapshotSender.send(tableId, playerId)
                return@launch
            }

            val room = roomRepository.getRoom(tableId)
            if (room == null) {
                when (val result = createRoom(tableId, playerId, GameConfig(RiichiRuleConfig()))) {
                    is Outcome.Success -> {
                        syncRoom(tableId, playerId)
                        roomSnapshotSender.send(tableId, playerId)
                        val location = tableLocationRegistry.get(tableId)?.location
                        feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.GameCreated(location))
                    }
                    is Outcome.Error -> publishRoomError(playerId, result.error)
                }
                return@launch
            }

            if (playerId in room.playerIds) {
                if (!membershipRepository.claim(playerId, tableId)) {
                    feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerAlreadyInGame)
                    return@launch
                }
                syncRoom(tableId, playerId)
                roomSnapshotSender.send(tableId, playerId)
                return@launch
            }

            // 先建立 observer snapshot，讓 JoinRoomUseCase 更新 observer 後，RoomEventPublisher 能把
            // 加入事件與正確的 isInRoom=true 快照一併送給新玩家。
            syncRoom(tableId, playerId)
            when (val result = joinRoom(tableId, playerId)) {
                is Outcome.Success -> feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.GameJoined)
                is Outcome.Error -> publishRoomError(playerId, result.error)
            }
        }
    }

    /** 讓玩家以蹲下右鍵離開目前麻將桌的等待階段；進行中的對局暫時禁止離開。 */
    fun leave(table: MahjongTableBlockEntity, player: ServerPlayerEntity) {
        val tableId = table.tableId
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            if (membershipRepository.getTableId(playerId) != tableId) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerNotInGame)
                return@launch
            }
            if (gameRepository.getTableState(tableId) != null) {
                feedbackPublisher.publish(
                    playerId,
                    MinecraftPlayerFeedback.GameLeaveDeniedWhilePlaying,
                )
                return@launch
            }

            val room = roomRepository.getRoom(tableId)
            if (room == null || playerId !in room.playerIds) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerNotInGame)
                return@launch
            }
            val wasHost = room.hostId == playerId
            when (leaveRoom(tableId, playerId)) {
                is Outcome.Success -> feedbackPublisher.publish(
                    playerId,
                    MinecraftRoomFeedbackResolver.successfulLeave(wasHost),
                )
                is Outcome.Error -> feedbackPublisher.publish(
                    playerId,
                    MinecraftPlayerFeedback.GameLeaveFailed,
                )
            }
        }
    }

    /**
     * 切換玩家在房間等待階段的準備狀態。以玩家目前的房間歸屬（[PlayerMembershipRepository]）解析
     * 目標房間，不需要玩家實際站在桌子附近——呼叫端（指令或未來的 HUD）自行決定要不要額外檢查距離。
     *
     * 房主不參與準備機制（開局用 [start]），這裡先擋在呼叫 [toggleReady] 之前給出專屬回饋，避免
     * [ToggleReadyUseCase] 內部對房主的無操作分支被誤讀成一次真正的狀態切換。
     */
    fun ready(player: ServerPlayerEntity) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val tableId = membershipRepository.getTableId(playerId)
            if (tableId == null) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerNotInGame)
                return@launch
            }
            val room = roomRepository.getRoom(tableId)
            if (room != null && room.hostId == playerId) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.HostReadyNotRequired)
                return@launch
            }
            when (val result = toggleReady(tableId, playerId)) {
                is Outcome.Success -> feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.ReadyToggled(result.value))
                is Outcome.Error -> feedbackPublisher.publish(playerId, MinecraftRoomFeedbackResolver.readyError(result.error))
            }
        }
    }

    /**
     * 讓房主在所有人皆已準備完成時開始遊戲。同樣以玩家目前的房間歸屬解析目標房間；座位傳送與其他
     * 呈現已經由 `StartGameUseCase` 內部觸發，這裡不需要額外處理。
     *
     * 開局後第一位玩家的摸牌需要另外補：[GameFlowCoordinator.driveAutomatedPlayers] 只涵蓋第一位是
     * AI 的情況，[MahjongAutoDrawService.checkAndAutoDraw] 只涵蓋第一位是真人的情況，兩者依
     * `current.isAi` 互斥，只呼叫其中一個會漏掉另一種開局，因此都要呼叫。
     */
    fun start(player: ServerPlayerEntity) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val tableId = membershipRepository.getTableId(playerId)
            if (tableId == null) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerNotInGame)
                return@launch
            }
            when (val result = startGame(tableId, playerId)) {
                is Outcome.Success -> {
                    val gameId = result.value
                    gameFlowCoordinator.driveAutomatedPlayers(gameId)
                    autoDrawService.checkAndAutoDraw(gameId)
                }
                is Outcome.Error -> feedbackPublisher.publish(playerId, MinecraftRoomFeedbackResolver.startError(result.error))
            }
        }
    }

    /**
     * 讓房主替房間新增一名 AI 玩家。同樣以玩家目前的房間歸屬解析目標房間。
     *
     * @param strategyKey AI 使用的策略登記 key；不傳時使用 [AddAiPlayerUseCase] 的預設策略。
     */
    fun addAi(player: ServerPlayerEntity, strategyKey: String?) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val tableId = membershipRepository.getTableId(playerId)
            if (tableId == null) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerNotInGame)
                return@launch
            }
            when (val result = addAiPlayer(tableId, playerId, strategyKey)) {
                is Outcome.Success -> feedbackPublisher.publish(
                    playerId,
                    MinecraftPlayerFeedback.AiAdded(result.value.strategyKey),
                )
                is Outcome.Error -> feedbackPublisher.publish(playerId, MinecraftRoomFeedbackResolver.addAiError(result.error))
            }
        }
    }

    /**
     * 讓房主將指定玩家（含 AI）移出房間。同樣以玩家目前的房間歸屬解析目標房間。成功時被踢者與房主
     * 各自收到專屬回饋。
     */
    fun kick(player: ServerPlayerEntity, targetPlayerId: Uuid) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val tableId = membershipRepository.getTableId(playerId)
            if (tableId == null) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerNotInGame)
                return@launch
            }
            when (val result = kickPlayer(tableId, playerId, targetPlayerId)) {
                is Outcome.Success -> {
                    feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerKicked)
                    feedbackPublisher.publish(targetPlayerId, MinecraftPlayerFeedback.KickedFromGame)
                    val target = serverHolder.findPlayer(targetPlayerId)
                    val updatedRoom = roomRepository.getRoom(tableId)
                    if (target != null && updatedRoom != null) {
                        syncRoom(tableId, targetPlayerId)
                        roomSnapshotSender.send(tableId, targetPlayerId)
                        MahjongChannels.tableLobby.sendTo(
                            target,
                            json,
                            TableLobbyPayloadDto(
                                tableId.toString(),
                                TableLobbyPhaseDto.WAITING,
                                updatedRoom.toSnapshot(targetPlayerId).toDto(networkRegistries),
                            ),
                        )
                    }
                }
                is Outcome.Error -> feedbackPublisher.publish(playerId, MinecraftRoomFeedbackResolver.kickError(result.error))
            }
        }
    }

    /**
     * 讓房主替房間內既有的 AI 玩家更換策略。同樣以玩家目前的房間歸屬解析目標房間。
     *
     * @param targetAiId 欲更換策略的 AI 玩家 Uuid，必須是房間內的 AI。
     * @param strategyKey 新的策略登記 key。
     */
    fun changeAiStrategy(player: ServerPlayerEntity, targetAiId: Uuid, strategyKey: String) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val tableId = membershipRepository.getTableId(playerId)
            if (tableId == null) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerNotInGame)
                return@launch
            }
            when (val result = changeAiStrategy(tableId, playerId, targetAiId, strategyKey)) {
                is Outcome.Success -> {
                    // 換策略成功後 targetAiId 一定還在房間裡，序號查不到理論上不會發生，仍優雅退回 0。
                    val aiSequence = memberCandidateResolver.listAiCandidates(playerId)
                        .firstOrNull { it.playerId == targetAiId }
                        ?.aiSequence
                        ?: 0
                    feedbackPublisher.publish(
                        playerId,
                        MinecraftPlayerFeedback.AiStrategyChanged(aiSequence, result.value, strategyKey),
                    )
                }
                is Outcome.Error ->
                    feedbackPublisher.publish(playerId, MinecraftRoomFeedbackResolver.changeAiStrategyError(result.error))
            }
        }
    }

    /**
     * 顯示玩家目前所在房間的完整遊戲設定，供指令印出可 hover／可點擊開啟編輯畫面的訊息。同樣以玩家
     * 目前的房間歸屬解析目標房間；沒有房主限制，任何在房間內的玩家都能查看目前設定。
     */
    fun showConfig(player: ServerPlayerEntity) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val tableId = membershipRepository.getTableId(playerId)
            val room = tableId?.let { roomRepository.getRoom(it) }
            if (room == null) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerNotInGame)
                return@launch
            }
            val configJson = json.encodeToString(room.gameConfig.toDto(networkRegistries))
            feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.ShowGameConfig(configJson))
        }
    }

    /**
     * 讓房主以強型別設定更新目前所在房間。同樣以玩家目前的房間歸屬解析目標房間。
     */
    fun updateConfig(player: ServerPlayerEntity, newConfig: GameConfig) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val tableId = membershipRepository.getTableId(playerId)
            if (tableId == null) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerNotInGame)
                return@launch
            }
            val room = roomRepository.getRoom(tableId)
            if (room == null) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerNotInGame)
                return@launch
            }
            val oldConfig = room.gameConfig
            when (val result = updateConfig(tableId, playerId, newConfig)) {
                is Outcome.Success -> {
                    val newConfigJson = json.encodeToString(result.value.toDto(networkRegistries))
                    if (oldConfig == result.value) {
                        feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.GameConfigUnchanged(newConfigJson))
                    } else {
                        val oldConfigJson = json.encodeToString(oldConfig.toDto(networkRegistries))
                        feedbackPublisher.publish(
                            playerId,
                            MinecraftPlayerFeedback.GameConfigChanged(oldConfigJson, newConfigJson),
                        )
                    }
                }
                is Outcome.Error ->
                    feedbackPublisher.publish(playerId, MinecraftRoomFeedbackResolver.updateConfigError(result.error))
            }
        }
    }

    /** 將可辨識的房間錯誤映射成結構化回饋，其餘錯誤使用通用加入失敗提示。 */
    private fun publishRoomError(playerId: Uuid, error: RoomError) {
        feedbackPublisher.publish(playerId, MinecraftRoomFeedbackResolver.joinError(error))
    }

    private companion object {
        const val MAX_ROOM_SCREEN_DISTANCE_SQUARED: Double = 64.0
    }
}
