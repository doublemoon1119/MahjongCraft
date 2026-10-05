package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryAccess
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryScope
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistorySummaryRequest
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryArchiveStatusDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryArchiveStatusRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryArchiveStatusResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryListRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryListResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryErrorCodeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRuleSettingsRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRuleSettingsResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySummaryRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySummaryResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.decodeHistoryCursor
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.encode
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.history.GetHistoryRoundEventsUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.history.GetHistoryRoundStateUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.history.GetHistoryRuleSettingsUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.history.GetHistorySummaryUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.history.ListHistoryUseCase
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.network.HistoryQueryLimits
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.PlayerIdentitySender
import com.doublemoon1119.mahjongcraft.platform.fabric.server.player.ServerPlayerIdentityStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryQuerySettingsPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * 接收連線身分的歷史查詢，限制頻率並於回覆前重新驗證權限與世界。
 *
 * @property writer 正式資料庫的唯一生命週期。
 * @property configState 目前有效的查詢政策。
 * @property store 檢查尚在進行及轉移中的權威對局。
 * @property scope 執行非同步查詢的應用作用域；世界有效性由 writer session 另行驗證。
 * @property dispatchers 確保資料讀取及回覆分別使用 I/O 與伺服器主執行緒。
 * @property json 線路序列化設定。
 * @property networkRegistries 將已保存的規則設定映射為網路 DTO 的正式註冊表。
 * @property identityStore 伺服器已知的玩家名稱索引。
 * @property playerIdentities 向已授權玩家同步參與者名稱及外觀身分。
 */
@Single
class FabricHistoryQueryService(
    private val writer: FabricHistoryOutboxWriter,
    @Provided private val configState: MinecraftServerConfigState,
    private val store: AuthoritativeStateStore,
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    private val json: Json,
    @Provided private val networkRegistries: NetworkDtoRegistries,
    private val playerIdentities: PlayerIdentitySender,
    private val identityStore: ServerPlayerIdentityStore,
) {
    /** 不向玩家傳送原始查詢例外的診斷 logger。 */
    private val logger = mahjongCraftLogger(FabricHistoryQueryService::class)

    /** 每連線最多一份待執行工作，不累積查詢佇列。 */
    private val admission = HistoryQueryAdmission(limits = {
        configState.current.history.let { HistoryQueryAdmissionLimits(it.queryMinimumInterval, it.queryMaxOutstanding, it.queryRejectionReplyInterval) }
    })

    /** 伺服器生命週期發布的同步目標；背景設定通知只在主執行緒操作其玩家。 */
    @Volatile private var settingsServer: MinecraftServer? = null

    /** 註冊有界要求與斷線清理，不提供歷史寫入操作。 */
    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server -> settingsServer = server }
        ServerLifecycleEvents.SERVER_STOPPED.register { server -> if (settingsServer === server) settingsServer = null }
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> sendQuerySettings(handler.player) }
        scope.launch {
            configState.updates.map { it.history.queryMinimumIntervalMilliseconds }.distinctUntilChanged().collect {
                withContext(dispatchers.main) {
                    settingsServer?.playerManager?.playerList?.forEach(::sendQuerySettings)
                }
            }
        }
        MahjongChannels.historyListRequest.registerServerReceiver(json, ::receiveList)
        MahjongChannels.historySummaryRequest.registerServerReceiver(json, ::receiveSummary)
        MahjongChannels.historyRuleSettingsRequest.registerServerReceiver(json, ::receiveRuleSettings)
        MahjongChannels.historyArchiveStatusRequest.registerServerReceiver(json, ::receiveArchiveStatus)
        MahjongChannels.historyRoundEventsRequest.registerServerReceiver(json, ::receiveRoundEvents)
        MahjongChannels.historyRoundStateRequest.registerServerReceiver(json, ::receiveRoundState)
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> admission.remove(handler.player.uuid.toKotlinUuid()) }
    }

    /** 同步目前有效的間隔；不將工作上限或拒絕回覆政策交由客戶端決定。
     *
     * @param player 已建立連線的玩家。
     */
    private fun sendQuerySettings(player: ServerPlayerEntity) {
        MahjongChannels.historyQuerySettings.sendTo(player, json, HistoryQuerySettingsPayload(configState.current.history.queryMinimumIntervalMilliseconds))
    }

    /**
     * 接收單局事件要求，共用正式查詢准入與回覆驗證。
     * @param server 目前伺服器。
     * @param player 已驗證連線玩家。
     * @param request 有界事件要求。
     */
    private fun receiveRoundEvents(server: MinecraftServer, player: ServerPlayerEntity, request: HistoryRoundEventsRequestDto) {
        receiveRoundQuery(
            server, player, request.requestId, request.matchId, request.scope.toDomain(),
            failure = { HistoryRoundEventsResponseDto(request.requestId, request.matchId, request.roundNumber, request.startTransactionIndex, errorCode = it) },
            query = { access, repository ->
                when (val result = GetHistoryRoundEventsUseCase(repository, ::policy)(access, request.toDomain())) {
                    is HistoryQueryResult.Failure -> HistoryRoundEventsResponseDto(request.requestId, request.matchId, request.roundNumber, request.startTransactionIndex, errorCode = result.error.code.toDto())
                    is HistoryQueryResult.Success -> boundedHistoryRoundEvents(request, result.value, json)
                }
            },
            hasContent = { it.events != null },
            participants = { it.events?.identity?.players.orEmpty().filter { identity -> identity.aiStrategyId == null }.mapNotNull { identity -> identity.playerId } },
            send = { MahjongChannels.historyRoundEventsResponse.sendTo(player, json, it) },
        )
    }

    /**
     * 接收指定交易後的桌況要求，不建立可操作的遊戲狀態。
     * @param server 目前伺服器。
     * @param player 已驗證連線玩家。
     * @param request 有界狀態要求。
     */
    private fun receiveRoundState(server: MinecraftServer, player: ServerPlayerEntity, request: HistoryRoundStateRequestDto) {
        receiveRoundQuery(
            server, player, request.requestId, request.matchId, request.scope.toDomain(),
            failure = { HistoryRoundStateResponseDto(request.requestId, request.matchId, request.roundNumber, request.position, errorCode = it) },
            query = { access, repository ->
                when (val result = GetHistoryRoundStateUseCase(repository, ::policy)(access, request.toDomain())) {
                    is HistoryQueryResult.Failure -> HistoryRoundStateResponseDto(request.requestId, request.matchId, request.roundNumber, request.position, errorCode = result.error.code.toDto())
                    is HistoryQueryResult.Success -> boundedHistoryRoundState(request, result.value, json)
                }
            },
            hasContent = { it.state != null },
            participants = { it.state?.identity?.players.orEmpty().filter { identity -> identity.aiStrategyId == null }.mapNotNull { identity -> identity.playerId } },
            send = { MahjongChannels.historyRoundStateResponse.sendTo(player, json, it) },
        )
    }

    /**
     * 執行單局查詢的共同生命週期，同一 deadline 內完成讀取、預算與公開確認。
     * @param R 線路回覆型別。
     * @param server 原伺服器。
     * @param player 原連線玩家。
     * @param requestId 原要求配對鍵。
     * @param matchId 原要求對局識別碼。
     * @param queryScope 原要求範圍。
     * @param failure 不含內容的錯誤回覆建構器。
     * @param query 有界且已授權的背景查詢。
     * @param hasContent 判斷是否需再次確認公開性。
     * @param participants 只取成功內容中的真人身分。
     * @param send 主執行緒送出回覆。
     */
    private fun <R> receiveRoundQuery(
        server: MinecraftServer,
        player: ServerPlayerEntity,
        requestId: String,
        matchId: String,
        queryScope: HistoryQueryScope,
        failure: (HistoryQueryErrorCodeDto) -> R,
        query: suspend (HistoryQueryAccess, FabricHistoryQueryRepository) -> R,
        hasContent: (R) -> Boolean,
        participants: (R) -> Collection<String>,
        send: (R) -> Unit,
    ) {
        if (!validRequestId(requestId)) return
        val access = player.queryAccess()
        val sessionId = writer.currentSessionId
        val accepted = admission.acquire(access.principalId)
        if (accepted !is HistoryQueryAdmission.Admission.Accepted) {
            if (admission.shouldSendRejection(access.principalId)) send(failure(accepted.errorCode()))
            return
        }
        scope.launch {
            try {
                var response = withTimeoutOrNull(HistoryQueryLimits.timeout) {
                    try {
                        val id = Uuid.parse(matchId)
                        var result = query(access, FabricHistoryQueryRepository(writer, sessionId, identityStore))
                        if (hasContent(result)) {
                            val confirmed = writer.confirmRoundPublication(access, id, queryScope, sessionId)
                            val invalid = when (confirmed) {
                                is HistoryManagementResult.Success -> (confirmed.value as? HistoryQueryResult.Failure)?.error?.code?.toDto()
                                is HistoryManagementResult.Busy -> HistoryQueryErrorCodeDto.BUSY
                                is HistoryManagementResult.Disconnected, HistoryManagementResult.SessionChanged -> HistoryQueryErrorCodeDto.DISCONNECTED
                                is HistoryManagementResult.Failed -> HistoryQueryErrorCodeDto.NOT_AVAILABLE
                            }
                            if (invalid != null) result = failure(invalid)
                        }
                        result
                    } catch (_: IllegalArgumentException) {
                        failure(HistoryQueryErrorCodeDto.INVALID_REQUEST)
                    }
                } ?: failure(HistoryQueryErrorCodeDto.TIMEOUT)
                withContext(dispatchers.main) {
                    if (!sameConnection(server, player, sessionId)) return@withContext
                    val invalid = replyError(player.queryAccess(), policy(), queryScope)
                    if (invalid != null) response = failure(invalid)
                    val canonicalMatchId = runCatching { Uuid.parse(matchId).toString() }.getOrNull()
                    if (canonicalMatchId in activeMatches()) response = failure(HistoryQueryErrorCodeDto.NOT_AVAILABLE)
                    sendIdentities(player, participants(response))
                    send(response)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logger.error("History round query failed", error)
                withContext(dispatchers.main) {
                    if (sameConnection(server, player, sessionId)) send(failure(HistoryQueryErrorCodeDto.NOT_AVAILABLE))
                }
            } finally {
                admission.release(access.principalId, accepted.token)
            }
        }.invokeOnCompletion {
            // 尚未開始便取消時仍解除名額，重複解除為無操作。
            admission.release(access.principalId, accepted.token)
        }
    }

    /**
     * 接收單場保存狀態要求；狀態查詢與摘要查詢共用身分、session 與頻率限制。
     *
     * @param server 目前伺服器。
     * @param player 已驗證連線玩家。
     * @param request 有界線路要求。
     */
    private fun receiveArchiveStatus(server: MinecraftServer, player: ServerPlayerEntity, request: HistoryArchiveStatusRequestDto) {
        if (!validRequestId(request.requestId)) return
        val access = player.queryAccess()
        val initialPolicy = policy()
        val sessionId = writer.currentSessionId
        val accepted = admission.acquire(access.principalId)
        if (accepted !is HistoryQueryAdmission.Admission.Accepted) {
            if (admission.shouldSendRejection(access.principalId)) {
                MahjongChannels.historyArchiveStatusResponse.sendTo(
                    player,
                    json,
                    HistoryArchiveStatusResponseDto(request.requestId, HistoryArchiveStatusDto.MISSING, accepted.errorCode()),
                )
            }
            return
        }
        scope.launch {
            try {
                var response = withTimeoutOrNull(HistoryQueryLimits.timeout) {
                    try {
                        val matchId = Uuid.parse(request.matchId)
                        when (val result = writer.archiveStatus(access, matchId, sessionId)) {
                            is HistoryManagementResult.Success -> HistoryArchiveStatusResponseDto(request.requestId, result.value)
                            is HistoryManagementResult.Busy -> HistoryArchiveStatusResponseDto(request.requestId, HistoryArchiveStatusDto.MISSING, HistoryQueryErrorCodeDto.BUSY)
                            is HistoryManagementResult.Disconnected,
                            is HistoryManagementResult.SessionChanged,
                            -> HistoryArchiveStatusResponseDto(request.requestId, HistoryArchiveStatusDto.MISSING, HistoryQueryErrorCodeDto.DISCONNECTED)
                            is HistoryManagementResult.Failed -> HistoryArchiveStatusResponseDto(request.requestId, HistoryArchiveStatusDto.MISSING, HistoryQueryErrorCodeDto.NOT_AVAILABLE)
                        }
                    } catch (_: IllegalArgumentException) {
                        HistoryArchiveStatusResponseDto(request.requestId, HistoryArchiveStatusDto.MISSING, HistoryQueryErrorCodeDto.INVALID_REQUEST)
                    }
                } ?: HistoryArchiveStatusResponseDto(request.requestId, HistoryArchiveStatusDto.MISSING, HistoryQueryErrorCodeDto.TIMEOUT)
                withContext(dispatchers.main) {
                    if (!sameConnection(server, player, sessionId)) return@withContext
                    val currentPolicy = policy()
                    if (!currentPolicy.queryEnabled) {
                        response = HistoryArchiveStatusResponseDto(request.requestId, HistoryArchiveStatusDto.DISABLED)
                    } else if (access.isAdministrator &&
                        (!player.queryAccess().isAdministrator || initialPolicy.allowAdministratorQuery && !currentPolicy.allowAdministratorQuery)
                    ) {
                        response = HistoryArchiveStatusResponseDto(request.requestId, HistoryArchiveStatusDto.DENIED, HistoryQueryErrorCodeDto.ACCESS_DENIED)
                    }
                    MahjongChannels.historyArchiveStatusResponse.sendTo(player, json, response)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logger.error("History archive status query failed", error)
                withContext(dispatchers.main) {
                    if (sameConnection(server, player, sessionId)) {
                        MahjongChannels.historyArchiveStatusResponse.sendTo(
                            player,
                            json,
                            HistoryArchiveStatusResponseDto(request.requestId, HistoryArchiveStatusDto.MISSING, HistoryQueryErrorCodeDto.NOT_AVAILABLE),
                        )
                    }
                }
            } finally {
                admission.release(access.principalId, accepted.token)
            }
        }.invokeOnCompletion {
            // 未開始執行便取消的工作不會進入 finally；完成通知仍須解除名額，重複解除為無操作。
            admission.release(access.principalId, accepted.token)
        }
    }

    /**
     * 接收清單要求，主執行緒取得可信身分與固定 session。
     *
     * @param server 目前伺服器。
     * @param player 已驗證連線玩家。
     * @param request 有界線路要求。
     */
    private fun receiveList(server: MinecraftServer, player: ServerPlayerEntity, request: HistoryListRequestDto) {
        if (!validRequestId(request.requestId)) return
        val access = player.queryAccess()
        val sessionId = writer.currentSessionId
        val accepted = admission.acquire(access.principalId)
        if (accepted !is HistoryQueryAdmission.Admission.Accepted) {
            if (admission.shouldSendRejection(access.principalId)) {
                MahjongChannels.historyListResponse.sendTo(player, json, HistoryListResponseDto(request.requestId, emptyList(), errorCode = accepted.errorCode(), allowAll = canQueryAll(access, policy())))
            }
            return
        }
        scope.launch {
            try {
                var response = withTimeoutOrNull(HistoryQueryLimits.timeout) {
                    try {
                        require((request.cursor?.length ?: 0) <= HistoryQueryLimits.CURSOR_LENGTH) { "History cursor exceeds its length limit" }
                        val domain = request.toDomain { it?.decodeHistoryCursor(json) }
                        val repository = FabricHistoryQueryRepository(writer, sessionId, identityStore)
                        when (val result = ListHistoryUseCase(repository, ::policy)(access, domain)) {
                            is HistoryQueryResult.Failure -> HistoryListResponseDto(request.requestId, emptyList(), errorCode = result.error.code.toDto())
                            is HistoryQueryResult.Success -> boundedHistoryPage(request.requestId, result.value, domain, access.principalId, json, { it.encode(json) })
                        }
                    } catch (_: IllegalArgumentException) {
                        HistoryListResponseDto(request.requestId, emptyList(), errorCode = HistoryQueryErrorCodeDto.INVALID_REQUEST)
                    }
                } ?: HistoryListResponseDto(request.requestId, emptyList(), errorCode = HistoryQueryErrorCodeDto.TIMEOUT)
                withContext(dispatchers.main) {
                    if (!sameConnection(server, player, sessionId)) return@withContext
                    val invalid = replyError(player.queryAccess(), policy(), request.scope.toDomain())
                    if (invalid != null) response = HistoryListResponseDto(request.requestId, emptyList(), errorCode = invalid)
                    val active = activeMatches()
                    if (response.entries.any { it.matchId in active }) response = HistoryListResponseDto(request.requestId, emptyList(), errorCode = HistoryQueryErrorCodeDto.NOT_AVAILABLE)
                    response = response.copy(allowAll = canQueryAll(player.queryAccess(), policy()))
                    sendIdentities(player, response.entries.flatMap { entry -> entry.participants.filter { it.aiStrategyId == null }.map { it.playerId } })
                    MahjongChannels.historyListResponse.sendTo(player, json, response)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logger.error("History list query failed", error)
                withContext(dispatchers.main) {
                    if (sameConnection(server, player, sessionId)) MahjongChannels.historyListResponse.sendTo(player, json, HistoryListResponseDto(request.requestId, emptyList(), errorCode = HistoryQueryErrorCodeDto.NOT_AVAILABLE, allowAll = canQueryAll(player.queryAccess(), policy())))
                }
            } finally {
                admission.release(access.principalId, accepted.token)
            }
        }.invokeOnCompletion {
            // 未開始執行便取消的工作不會進入 finally；完成通知仍須解除名額，重複解除為無操作。
            admission.release(access.principalId, accepted.token)
        }
    }

    /**
     * 接收單場要求，不因任意 match ID 揭露存在與否。
     *
     * @param server 目前伺服器。
     * @param player 已驗證連線玩家。
     * @param request 有界線路要求。
     */
    private fun receiveSummary(server: MinecraftServer, player: ServerPlayerEntity, request: HistorySummaryRequestDto) {
        if (!validRequestId(request.requestId)) return
        val access = player.queryAccess()
        val sessionId = writer.currentSessionId
        val accepted = admission.acquire(access.principalId)
        if (accepted !is HistoryQueryAdmission.Admission.Accepted) {
            if (admission.shouldSendRejection(access.principalId)) {
                MahjongChannels.historySummaryResponse.sendTo(player, json, HistorySummaryResponseDto(request.requestId, errorCode = accepted.errorCode()))
            }
            return
        }
        scope.launch {
            try {
                var response = withTimeoutOrNull(HistoryQueryLimits.timeout) {
                    try {
                        val domain = HistorySummaryRequest(Uuid.parse(request.matchId), request.scope.toDomain())
                        val repository = FabricHistoryQueryRepository(writer, sessionId, identityStore)
                        when (val result = GetHistorySummaryUseCase(repository, ::policy)(access, domain)) {
                            is HistoryQueryResult.Failure -> HistorySummaryResponseDto(request.requestId, errorCode = result.error.code.toDto())
                            is HistoryQueryResult.Success -> HistorySummaryResponseDto(request.requestId, result.value.toDto())
                        }
                    } catch (_: IllegalArgumentException) {
                        HistorySummaryResponseDto(request.requestId, errorCode = HistoryQueryErrorCodeDto.INVALID_REQUEST)
                    }
                } ?: HistorySummaryResponseDto(request.requestId, errorCode = HistoryQueryErrorCodeDto.TIMEOUT)
                if (json.encodeToString(HistorySummaryResponseDto.serializer(), response).toByteArray(Charsets.UTF_8).size > HistoryQueryLimits.RESPONSE_BYTES) {
                    response = HistorySummaryResponseDto(request.requestId, errorCode = HistoryQueryErrorCodeDto.CONTENT_TOO_LARGE)
                }
                withContext(dispatchers.main) {
                    if (!sameConnection(server, player, sessionId)) return@withContext
                    val invalid = replyError(player.queryAccess(), policy(), request.scope.toDomain())
                    if (invalid != null) response = HistorySummaryResponseDto(request.requestId, errorCode = invalid)
                    if (response.detail?.summary?.matchId in activeMatches()) response = HistorySummaryResponseDto(request.requestId, errorCode = HistoryQueryErrorCodeDto.NOT_AVAILABLE)
                    response.detail?.let { detail ->
                        sendIdentities(player, detail.summary.participants.filter { it.aiStrategyId == null }.map { it.playerId })
                    }
                    MahjongChannels.historySummaryResponse.sendTo(player, json, response)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logger.error("History summary query failed", error)
                withContext(dispatchers.main) {
                    if (sameConnection(server, player, sessionId)) MahjongChannels.historySummaryResponse.sendTo(player, json, HistorySummaryResponseDto(request.requestId, errorCode = HistoryQueryErrorCodeDto.NOT_AVAILABLE))
                }
            } finally {
                admission.release(access.principalId, accepted.token)
            }
        }.invokeOnCompletion {
            // 未開始執行便取消的工作不會進入 finally；完成通知仍須解除名額，重複解除為無操作。
            admission.release(access.principalId, accepted.token)
        }
    }

    /**
     * 接收單場歷史規則設定要求；只回覆 Replay 開局 header 的設定。
     *
     * @param server 目前伺服器。
     * @param player 已驗證連線玩家。
     * @param request 有界線路要求。
     */
    private fun receiveRuleSettings(server: MinecraftServer, player: ServerPlayerEntity, request: HistoryRuleSettingsRequestDto) {
        if (!validRequestId(request.requestId)) return
        val access = player.queryAccess()
        val sessionId = writer.currentSessionId
        val accepted = admission.acquire(access.principalId)
        if (accepted !is HistoryQueryAdmission.Admission.Accepted) {
            if (admission.shouldSendRejection(access.principalId)) {
                MahjongChannels.historyRuleSettingsResponse.sendTo(
                    player,
                    json,
                    HistoryRuleSettingsResponseDto(request.requestId, errorCode = accepted.errorCode()),
                )
            }
            return
        }
        scope.launch {
            try {
                var response = withTimeoutOrNull(HistoryQueryLimits.timeout) {
                    try {
                        val domain = request.toDomain()
                        val repository = FabricHistoryQueryRepository(writer, sessionId, identityStore)
                        when (val result = GetHistoryRuleSettingsUseCase(repository, ::policy)(access, domain)) {
                            is HistoryQueryResult.Failure -> HistoryRuleSettingsResponseDto(request.requestId, errorCode = result.error.code.toDto())
                            is HistoryQueryResult.Success -> boundedHistoryRuleSettings(request.requestId, result.value, networkRegistries, json)
                        }
                    } catch (_: IllegalArgumentException) {
                        HistoryRuleSettingsResponseDto(request.requestId, errorCode = HistoryQueryErrorCodeDto.INVALID_REQUEST)
                    }
                } ?: HistoryRuleSettingsResponseDto(request.requestId, errorCode = HistoryQueryErrorCodeDto.TIMEOUT)
                withContext(dispatchers.main) {
                    if (!sameConnection(server, player, sessionId)) return@withContext
                    val invalid = replyError(player.queryAccess(), policy(), request.scope.toDomain())
                    if (invalid != null) response = HistoryRuleSettingsResponseDto(request.requestId, errorCode = invalid)
                    val requestedMatchId = runCatching { Uuid.parse(request.matchId).toString() }.getOrNull()
                    if (requestedMatchId != null && requestedMatchId in activeMatches()) {
                        response = HistoryRuleSettingsResponseDto(
                            request.requestId,
                            errorCode = HistoryQueryErrorCodeDto.NOT_AVAILABLE,
                        )
                    }
                    MahjongChannels.historyRuleSettingsResponse.sendTo(player, json, response)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logger.error("History rule settings query failed", error)
                withContext(dispatchers.main) {
                    if (sameConnection(server, player, sessionId)) {
                        MahjongChannels.historyRuleSettingsResponse.sendTo(
                            player,
                            json,
                            HistoryRuleSettingsResponseDto(request.requestId, errorCode = HistoryQueryErrorCodeDto.NOT_AVAILABLE),
                        )
                    }
                }
            } finally {
                admission.release(access.principalId, accepted.token)
            }
        }.invokeOnCompletion {
            admission.release(access.principalId, accepted.token)
        }
    }

    /**
     * 讀取目前政策，不使用要求開始時的舊設定。
     *
     * @return 獨立於記錄開關的查詢政策。
     */
    private fun policy(): HistoryQueryPolicy = configState.current.history.let { HistoryQueryPolicy(it.queryEnabled, it.allowAdminQuery) }

    /**
     * 確認回覆仍屬於原玩家連線與世界。
     *
     * @param server 原伺服器。
     * @param player 原連線玩家實例。
     * @param sessionId 原世界 session。
     * @return 仍可安全回覆原要求。
     */
    private fun sameConnection(server: MinecraftServer, player: ServerPlayerEntity, sessionId: Uuid?): Boolean = server.playerManager.getPlayer(player.uuid) === player &&
        (writer.isCurrentSession(sessionId) || sessionId == null && writer.currentSessionId == null)

    /**
     * 在回覆前取得仍可接續的場次。
     *
     * @return 必須排除的對局 ID。
     */
    private suspend fun activeMatches(): Set<String> {
        val snapshot = store.snapshot()
        return snapshot.games.values.mapTo(mutableSetOf()) { it.matchId.toString() } +
            snapshot.historyRecordingState.transfersByMatchId.keys.map { it.toString() }
    }

    /**
     * 只同步本次已授權回應涉及的真人身分，不建立全伺服器名單。
     *
     * @param player 已驗證的收件玩家。
     * @param playerIds 回應中涉及的玩家 UUID 字串。
     */
    private suspend fun sendIdentities(player: ServerPlayerEntity, playerIds: Collection<String>) {
        playerIdentities.send(
            player.uuid.toKotlinUuid(),
            playerIds.mapNotNull { runCatching { Uuid.parse(it) }.getOrNull() },
        )
    }
}

/**
 * 只讀取連線身分，不接受 payload 宣稱的權限。
 *
 * @return 目前 UUID 與 OP 2 資格。
 */
private fun ServerPlayerEntity.queryAccess(): HistoryQueryAccess = HistoryQueryAccess(uuid.toKotlinUuid(), hasPermissionLevel(2))

/** 判斷目前連線是否可使用全部對局查詢範圍。 */
private fun canQueryAll(access: HistoryQueryAccess, policy: HistoryQueryPolicy): Boolean = policy.queryEnabled && access.isAdministrator && policy.allowAdministratorQuery

/**
 * 驗證有界且非空的回應配對鍵。
 *
 * @param id 原要求識別字串。
 * @return 是否可安全保留並回覆。
 */
private fun validRequestId(id: String): Boolean = id.isNotBlank() && id.length <= HistoryQueryLimits.REQUEST_ID_LENGTH

/**
 * 將非排隊准入結果映射為線路錯誤。
 *
 * @return 忙碌或頻率過高。
 */
private fun HistoryQueryAdmission.Admission.errorCode(): HistoryQueryErrorCodeDto = when (this) {
    HistoryQueryAdmission.Admission.Busy -> HistoryQueryErrorCodeDto.BUSY
    HistoryQueryAdmission.Admission.RateLimited -> HistoryQueryErrorCodeDto.RATE_LIMITED
    is HistoryQueryAdmission.Admission.Accepted -> error("Accepted admission has no error code")
}

/**
 * 重新載入與降權後，回覆前的最後授權檢查。
 *
 * @param access 目前可信連線身分。
 * @param policy 目前有效政策。
 * @param scope 原要求範圍。
 * @return 無效資格的穩定代碼；null 表示仍可回覆。
 */
internal fun replyError(access: HistoryQueryAccess, policy: HistoryQueryPolicy, scope: HistoryQueryScope): HistoryQueryErrorCodeDto? = when {
    !policy.queryEnabled -> HistoryQueryErrorCodeDto.QUERY_DISABLED
    scope == HistoryQueryScope.ALL && (!access.isAdministrator || !policy.allowAdministratorQuery) -> HistoryQueryErrorCodeDto.ACCESS_DENIED
    else -> null
}
