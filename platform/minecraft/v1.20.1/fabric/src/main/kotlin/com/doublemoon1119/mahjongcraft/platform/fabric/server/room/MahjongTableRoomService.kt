package com.doublemoon1119.mahjongcraft.platform.fabric.server.room

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.service.DefaultGameConfigProvider
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.GameConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.RoomSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.toDto
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AutomatedAdvanceManager
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
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableStateDelivery
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableStateTicket
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.TableOccupancyDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.TableOccupancyPayloadDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedbackPublisher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * 把 Minecraft 麻將桌互動路由到既有 Room／Game use case 的正式平台進場服務。
 *
 * 目前由右鍵／蹲下右鍵桌子（[interact]／[leave]）與 `/mahjongcraft` 玩家指令
 * （[ready]／[start]）共用；日後若互動方式改成懸浮 HUD 等其他觸發，只需要新增呼叫端，這裡的方法
 * 本身不需要更動。
 *
 * @property scope 執行房間與遊戲操作的協程作用域。
 * @property roomRepository 查詢等待室狀態。
 * @property roomSnapshotRepository 管理等待室快照。
 * @property gameRepository 查詢對局狀態。
 * @property membershipRepository 查詢玩家桌子歸屬。
 * @property defaultGameConfigProvider 建立新房間預設設定。
 * @property createRoom 建立房間的權威操作。
 * @property joinRoom 加入房間的權威操作。
 * @property leaveRoom 離開房間的權威操作。
 * @property toggleReady 切換準備狀態的權威操作。
 * @property startGame 開始對局的權威操作。
 * @property addAiPlayer 新增 AI 成員的權威操作。
 * @property changeAiStrategy 變更 AI 策略的權威操作。
 * @property kickPlayer 移除房間成員的權威操作。
 * @property updateConfig 更新房間設定的權威操作。
 * @property syncRoom 寫入等待室快照。
 * @property syncGame 寫入對局快照。
 * @property feedbackPublisher 發布玩家回饋。
 * @property tableLocationRegistry 查詢麻將桌座標。
 * @property reachableTableResolver 驗證玩家能否互動桌子。
 * @property memberCandidateResolver 查詢房間成員候選。
 * @property advanceManager 請求自動推進對局。
 * @property serverHolder 查詢目前伺服器與線上玩家。
 * @property tableStateDelivery 桌況回覆的共同來源檢查及主執行緒交付序列。
 * @property authoritativeStateStore 用於回覆當下的已發布房間與對局狀態。
 * @property dispatchers 提供伺服器主執行緒調度器。
 * @property json 編碼網路訊息的 JSON 設定。
 * @property networkRegistries 轉換網路 DTO 所需的規則註冊表。
 */
class MahjongTableRoomService(
    private val scope: AppCoroutineScope,
    private val roomRepository: RoomRepository,
    private val roomSnapshotRepository: RoomSnapshotRepository,
    private val gameRepository: GameRepository,
    private val membershipRepository: PlayerMembershipRepository,
    private val defaultGameConfigProvider: DefaultGameConfigProvider,
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
    private val feedbackPublisher: MinecraftPlayerFeedbackPublisher,
    private val tableLocationRegistry: TableLocationRegistry,
    private val reachableTableResolver: ReachableMahjongTableResolver,
    private val memberCandidateResolver: RoomMemberCandidateResolver,
    private val advanceManager: AutomatedAdvanceManager,
    private val serverHolder: FabricServerHolder,
    private val tableStateDelivery: FabricTableStateDelivery,
    private val authoritativeStateStore: AuthoritativeStateStore,
    private val dispatchers: CoroutineDispatchers,
    private val json: NetworkJson,
    private val networkRegistries: NetworkDtoRegistries,
) {
    /**
     * 將 RoomScreen 的強型別操作路由到既有權威 use case；封包中的玩家身分一律忽略。
     *
     * @param player Fabric 驗證過的封包來源玩家。
     * @param action RoomScreen 傳來的操作 DTO。
     */
    fun handleRoomAction(player: ServerPlayerEntity, action: RoomActionDto) {
        val tableId = runCatching { Uuid.parse(action.venueId) }.getOrNull() ?: return
        when (action) {
            is RoomActionDto.Create -> createFromScreen(tableId, player)
            is RoomActionDto.Join -> joinFromScreen(tableId, player)
            is RoomActionDto.ToggleReady -> withMatchingMembership(player, tableId, ::ready)
            is RoomActionDto.Start -> withMatchingMembership(player, tableId, ::start)
            is RoomActionDto.Leave,
            is RoomActionDto.Disband,
            -> leaveFromScreen(tableId, player)
            is RoomActionDto.AddAi -> withMatchingMembership(player, tableId) { addAi(it, action.strategyKey) }
            is RoomActionDto.ChangeAiStrategy -> withMatchingMembership(player, tableId) {
                val targetId = runCatching { Uuid.parse(action.targetPlayerId) }.getOrNull() ?: return@withMatchingMembership
                changeAiStrategy(it, targetId, action.strategyKey)
            }
            is RoomActionDto.Kick -> withMatchingMembership(player, tableId) {
                val targetId = runCatching { Uuid.parse(action.targetPlayerId) }.getOrNull() ?: return@withMatchingMembership
                kick(it, targetId)
            }
            is RoomActionDto.UpdateConfig -> withMatchingMembership(player, tableId) {
                updateConfig(it, action.config.toDomain(networkRegistries))
            }
        }
    }

    /**
     * 防止客戶端以另一張桌子的 ID 操作自己目前所在的房間。
     *
     * @param player 經伺服器驗證的玩家。
     * @param tableId 操作要求指定的桌子 UUID。
     * @param action membership 相符時執行的操作。
     */
    private fun withMatchingMembership(player: ServerPlayerEntity, tableId: Uuid, action: (ServerPlayerEntity) -> Unit) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            if (membershipRepository.getVenueId(playerId) == tableId) {
                action(player)
            } else {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerNotInGame)
            }
        }
    }

    /**
     * 組出一份桌子佔用狀態 payload，一律附上桌子座標。
     *
     * 座標是客戶端判斷「玩家是否還在桌子附近」與「桌子是否還在」的依據；少帶座標的話，該關閉的畫面會
     * 一直留著。集中在這裡建立，就不會有呼叫端漏帶。
     *
     * @param tableId 桌子 UUID。
     * @param occupancy 桌子目前的空桌、等待室或對局狀態。
     * @param roomSnapshot 可選的等待室快照。
     * @param playingPlayerIds 對局中的真人 UUID 字串。
     * @param playingAiPlayerIds 對局中的 AI UUID 字串。
     * @param playingGameConfig 對局使用的遊戲設定 DTO。
     * @return 包含狀態、可選快照與桌子位置的 payload。
     */
    private fun tableOccupancyPayload(
        tableId: Uuid,
        occupancy: TableOccupancyDto,
        roomSnapshot: RoomSnapshotDto? = null,
        playingPlayerIds: List<String> = emptyList(),
        playingAiPlayerIds: List<String> = emptyList(),
        playingGameConfig: GameConfigDto? = null,
    ): TableOccupancyPayloadDto {
        val location = tableLocationRegistry.get(tableId)?.location
        return TableOccupancyPayloadDto(
            tableId = tableId.toString(),
            occupancy = occupancy,
            roomSnapshot = roomSnapshot,
            playingPlayerIds = playingPlayerIds,
            playingAiPlayerIds = playingAiPlayerIds,
            playingGameConfig = playingGameConfig,
            dimensionId = location?.dimensionId,
            tableX = location?.x,
            tableY = location?.y,
            tableZ = location?.z,
        )
    }

    /**
     * 只送出桌子目前的公開佔用狀態並開啟 RoomScreen，不建立、加入或離開房間。
     *
     * @param table 要顯示的麻將桌方塊實體。
     * @param player 接收桌子狀態的玩家。
     */
    fun openRoomScreen(table: MahjongTableBlockEntity, player: ServerPlayerEntity) {
        val tableId = table.tableId
        val ticket = tableStateDelivery.capture(tableId) ?: return
        scope.launch(dispatchers.main) { sendCurrentOccupancy(ticket, player) }
    }

    /**
     * 在交付序列內讀取當前占用內容，不使用工作排程前保存的舊房間或對局。
     *
     * @param ticket 排程前捕捉的桌子與 session 來源。
     * @param player 原請求的玩家；連線已被取代時不送出回覆。
     */
    private suspend fun sendCurrentOccupancy(ticket: FabricTableStateTicket, player: ServerPlayerEntity) {
        val playerId = player.uuid.toKotlinUuid()
        tableStateDelivery.prepareAndSend(
            ticket = ticket,
            prepare = {
                if (serverHolder.findPlayer(playerId) !== player) return@prepareAndSend null
                val state = authoritativeStateStore.state.value
                val game = state.games[ticket.tableId]
                val room = state.rooms[ticket.tableId]
                when {
                    game != null -> tableOccupancyPayload(
                        tableId = ticket.tableId,
                        occupancy = TableOccupancyDto.GAME,
                        playingPlayerIds = game.tableState.players.map { it.id.toString() },
                        playingAiPlayerIds = game.aiPlayerIds.map { it.toString() },
                        playingGameConfig = GameConfig(game.tableState.config, game.flowConfig).toDto(networkRegistries),
                    )
                    room != null -> tableOccupancyPayload(
                        tableId = ticket.tableId,
                        occupancy = TableOccupancyDto.ROOM,
                        roomSnapshot = room.toSnapshot(playerId).toDto(networkRegistries),
                    )
                    else -> tableOccupancyPayload(tableId = ticket.tableId, occupancy = TableOccupancyDto.VACANT)
                }
            },
            send = { payload -> MahjongChannels.tableOccupancy.sendTo(player, json, payload) },
        )
    }

    /**
     * RoomScreen 明確建立空桌房間。
     *
     * @param tableId 要建立房間的桌子 UUID。
     * @param player 發起建立操作的玩家。
     */
    fun createFromScreen(tableId: Uuid, player: ServerPlayerEntity) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            if (!isTableReachable(tableId, player)) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.TableNotReachable)
                return@launch
            }
            when (val result = createRoom(tableId, playerId, defaultGameConfigProvider.create())) {
                is Outcome.Success -> {
                    syncRoom(tableId, playerId)
                    feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.GameCreated(tableLocationRegistry.get(tableId)?.location))
                }
                is Outcome.Error -> publishRoomError(playerId, result.error)
            }
        }
    }

    /**
     * RoomScreen 明確加入既有等待房間。
     *
     * @param tableId 要加入的桌子 UUID。
     * @param player 發起加入操作的玩家。
     */
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

    /**
     * 建立與加入屬於桌旁操作，拒絕偽造的遠端 table ID。
     *
     * @param tableId 要確認位置的桌子 UUID。
     * @param player 執行桌旁操作的玩家。
     * @return 玩家與桌子位於同一維度且距離在設定範圍內時為 true。
     */
    private fun isTableReachable(tableId: Uuid, player: ServerPlayerEntity): Boolean {
        val location = tableLocationRegistry.get(tableId)?.location ?: return false
        if (player.serverWorld.registryKey.value.toString() != location.dimensionId) return false
        return player.squaredDistanceTo(location.x + 0.5, location.y + 0.5, location.z + 0.5) <= MAX_ROOM_SCREEN_DISTANCE_SQUARED
    }

    /**
     * RoomScreen 明確離開等待房間；房主會沿用既有語意解散房間。
     *
     * @param tableId 要離開的桌子 UUID。
     * @param player 發起離開操作的玩家。
     */
    private fun leaveFromScreen(tableId: Uuid, player: ServerPlayerEntity) {
        val playerId = player.uuid.toKotlinUuid()
        val ticket = tableStateDelivery.capture(tableId) ?: return
        scope.launch(dispatchers.main) {
            if (membershipRepository.getVenueId(playerId) != tableId) {
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
                    }
                    sendCurrentOccupancy(ticket, player)
                }
                is Outcome.Error -> feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.GameLeaveFailed)
            }
        }
    }

    /**
     * 依麻將桌目前處於空桌、等待室或遊戲階段，建立、加入或重新同步玩家狀態。
     *
     * @param table 玩家互動的麻將桌。
     * @param player 發起互動的玩家。
     */
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
                return@launch
            }

            val room = roomRepository.getRoom(tableId)
            if (room == null) {
                when (val result = createRoom(tableId, playerId, defaultGameConfigProvider.create())) {
                    is Outcome.Success -> {
                        syncRoom(tableId, playerId)
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

    /**
     * 讓玩家以蹲下右鍵離開目前麻將桌的等待階段；進行中的對局暫時禁止離開。
     *
     * @param table 玩家互動的麻將桌。
     * @param player 發起離開操作的玩家。
     */
    fun leave(table: MahjongTableBlockEntity, player: ServerPlayerEntity) {
        val tableId = table.tableId
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            if (membershipRepository.getVenueId(playerId) != tableId) {
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
     * 目標房間，並要求玩家目前實際在桌子的可互動範圍內（[isPlayerReachable]）；[start] 另外會在真正
     * 開局前，對所有已準備成員各自重新驗證一次同樣的條件，兩處合起來確保開局當下每個成員都確實在場。
     *
     * 房主不參與準備機制（開局用 [start]），這裡先擋在呼叫 [toggleReady] 之前給出專屬回饋，避免
     * [ToggleReadyUseCase] 內部對房主的無操作分支被誤讀成一次真正的狀態切換。
     *
     * @param player 要切換準備狀態的玩家。
     */
    fun ready(player: ServerPlayerEntity) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val tableId = membershipRepository.getVenueId(playerId)
            if (tableId == null) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerNotInGame)
                return@launch
            }
            if (!withContext(dispatchers.main) { isPlayerReachable(player, tableId) }) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.TableNotReachable)
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
     * 真正開始之前，會對房間內每一位非 AI 成員（含房主自己）各自檢查是否在線、以及在線的話是否在桌子
     * 的可互動範圍內（[isPlayerReachable]）；只要有任何一人離線或太遠，就整批擋下開局，並在回饋中列出
     * 是哪些人——不會只挑呼叫 [start] 的房主自己檢查，否則其他成員仍然可以先在附近按 [ready]、再走遠，
     * 靠房主開局把自己傳送過去。
     *
     * 開局後向 [AutomatedAdvanceManager] 請求推進：推進同時涵蓋第一位是 AI（驅動自動操作）與第一位是真人（推進最後的
     * 自動摸牌）兩種開局。
     *
     * @param player 發起開始操作的房主。
     */
    fun start(player: ServerPlayerEntity) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val tableId = membershipRepository.getVenueId(playerId)
            if (tableId == null) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerNotInGame)
                return@launch
            }
            val room = roomRepository.getRoom(tableId)
            if (room != null) {
                val offlinePlayerIds = mutableListOf<Uuid>()
                val distantPlayerIds = mutableListOf<Uuid>()
                withContext(dispatchers.main) {
                    room.humanPlayerIds.forEach { memberId ->
                        val member = serverHolder.findPlayer(memberId)
                        when {
                            member == null -> offlinePlayerIds.add(memberId)
                            !isPlayerReachable(member, tableId) -> distantPlayerIds.add(memberId)
                        }
                    }
                }
                if (offlinePlayerIds.isNotEmpty() || distantPlayerIds.isNotEmpty()) {
                    feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.StartBlockedByPlayers(offlinePlayerIds, distantPlayerIds))
                    return@launch
                }
            }
            when (val result = startGame(tableId, playerId)) {
                is Outcome.Success -> {
                    val gameId = result.value
                    advanceManager.request(gameId)
                }
                is Outcome.Error -> feedbackPublisher.publish(playerId, MinecraftRoomFeedbackResolver.startError(result.error))
            }
        }
    }

    /**
     * 該玩家目前是否在指定桌子的可互動範圍內，沿用一般右鍵桌子互動的距離／維度規則
     * （[ReachableMahjongTableResolver]），不另外維護一套獨立的距離常數。查不到桌子位置索引時視為
     * 不可達。
     *
     * 內部會讀取 [TableLocationRegistry] 與世界的 BlockEntity 狀態，兩者都只能在伺服器主執行緒存取；
     * 呼叫端必須先 `withContext(dispatchers.main)` 切回主執行緒，不能直接在 [scope] 的背景協程裡呼叫。
     *
     * @param player 要驗證的線上玩家。
     * @param tableId 玩家所屬桌子 UUID。
     * @return 玩家可在桌旁互動時為 true。
     */
    private fun isPlayerReachable(player: ServerPlayerEntity, tableId: Uuid): Boolean {
        val location = tableLocationRegistry.get(tableId)?.location ?: return false
        return reachableTableResolver.resolve(player, BlockPos(location.x, location.y, location.z)) != null
    }

    /**
     * 讓房主替房間新增一名 AI 玩家。同樣以玩家目前的房間歸屬解析目標房間。
     *
     * @param player 發起新增操作的房主。
     * @param strategyKey AI 使用的策略登記 key；不傳時使用 [AddAiPlayerUseCase] 的預設策略。
     */
    fun addAi(player: ServerPlayerEntity, strategyKey: String?) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val tableId = membershipRepository.getVenueId(playerId)
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
     *
     * @param player 發起踢除操作的房主。
     * @param targetPlayerId 要移出房間的成員 UUID。
     */
    fun kick(player: ServerPlayerEntity, targetPlayerId: Uuid) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch(dispatchers.main) {
            val tableId = membershipRepository.getVenueId(playerId)
            if (tableId == null) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerNotInGame)
                return@launch
            }
            val ticket = tableStateDelivery.capture(tableId) ?: return@launch
            when (val result = kickPlayer(tableId, playerId, targetPlayerId)) {
                is Outcome.Success -> {
                    feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerKicked)
                    feedbackPublisher.publish(targetPlayerId, MinecraftPlayerFeedback.KickedFromGame)
                    val target = serverHolder.findPlayer(targetPlayerId)
                    if (target != null) sendCurrentOccupancy(ticket, target)
                }
                is Outcome.Error -> feedbackPublisher.publish(playerId, MinecraftRoomFeedbackResolver.kickError(result.error))
            }
        }
    }

    /**
     * 讓房主替房間內既有的 AI 玩家更換策略。同樣以玩家目前的房間歸屬解析目標房間。
     *
     * @param player 發起策略變更操作的房主。
     * @param targetAiId 欲更換策略的 AI 玩家 Uuid，必須是房間內的 AI。
     * @param strategyKey 新的策略登記 key。
     */
    fun changeAiStrategy(player: ServerPlayerEntity, targetAiId: Uuid, strategyKey: String) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val tableId = membershipRepository.getVenueId(playerId)
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
     *
     * @param player 要查看房間設定的玩家。
     */
    fun showConfig(player: ServerPlayerEntity) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val tableId = membershipRepository.getVenueId(playerId)
            val room = tableId?.let { roomRepository.getRoom(it) }
            if (room == null) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.PlayerNotInAnyGame)
                return@launch
            }
            val configJson = json.encodeToString(room.gameConfig.toDto(networkRegistries))
            feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.ShowGameConfig(configJson))
        }
    }

    /**
     * 讓房主以強型別設定更新目前所在房間。同樣以玩家目前的房間歸屬解析目標房間。
     *
     * @param player 發起更新操作的房主。
     * @param newConfig 要套用的新設定。
     */
    fun updateConfig(player: ServerPlayerEntity, newConfig: GameConfig) {
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val tableId = membershipRepository.getVenueId(playerId)
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

    /**
     * 將可辨識的房間錯誤映射成結構化回饋，其餘錯誤使用通用加入失敗提示。
     *
     * @param playerId 收到回饋的玩家 UUID。
     * @param error 權威房間操作回傳的錯誤。
     */
    private fun publishRoomError(playerId: Uuid, error: RoomError) {
        feedbackPublisher.publish(playerId, MinecraftRoomFeedbackResolver.joinError(error))
    }

    private companion object {
        const val MAX_ROOM_SCREEN_DISTANCE_SQUARED: Double = 64.0
    }
}
