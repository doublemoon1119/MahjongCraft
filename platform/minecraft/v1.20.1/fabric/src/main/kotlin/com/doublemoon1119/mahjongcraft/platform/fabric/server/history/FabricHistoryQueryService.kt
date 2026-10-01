package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryAccess
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryScope
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistorySummaryRequest
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryListRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryListResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryErrorCodeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySummaryRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySummaryResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.decodeHistoryCursor
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.encode
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.toDto
import com.doublemoon1119.mahjongcraft.flow.server.game.history.GetHistorySummaryUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.history.ListHistoryUseCase
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.network.HistoryQueryLimits
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
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
 */
@Single
class FabricHistoryQueryService(
    private val writer: FabricHistoryOutboxWriter,
    @Provided private val configState: MinecraftServerConfigState,
    private val store: AuthoritativeStateStore,
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    private val json: Json,
) {
    /** 不向玩家傳送原始查詢例外的診斷 logger。 */
    private val logger = LoggerFactory.getLogger(FabricHistoryQueryService::class.java)

    /** 每連線最多一份待執行工作，不累積查詢佇列。 */
    private val admission = HistoryQueryAdmission()

    /** 註冊有界要求與斷線清理，不提供歷史寫入操作。 */
    fun register() {
        MahjongChannels.historyListRequest.registerServerReceiver(json, ::receiveList)
        MahjongChannels.historySummaryRequest.registerServerReceiver(json, ::receiveSummary)
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> admission.remove(handler.player.uuid.toKotlinUuid()) }
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
            MahjongChannels.historyListResponse.sendTo(player, json, HistoryListResponseDto(request.requestId, emptyList(), errorCode = accepted.errorCode()))
            return
        }
        scope.launch {
            try {
                var response = withTimeoutOrNull(HistoryQueryLimits.timeout) {
                    try {
                        require((request.cursor?.length ?: 0) <= HistoryQueryLimits.CURSOR_LENGTH) { "History cursor exceeds its length limit" }
                        val domain = request.toDomain { it?.decodeHistoryCursor(json) }
                        val repository = FabricHistoryQueryRepository(writer, sessionId)
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
                    MahjongChannels.historyListResponse.sendTo(player, json, response)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logger.error("History list query failed", error)
                withContext(dispatchers.main) {
                    if (sameConnection(server, player, sessionId)) MahjongChannels.historyListResponse.sendTo(player, json, HistoryListResponseDto(request.requestId, emptyList(), errorCode = HistoryQueryErrorCodeDto.NOT_AVAILABLE))
                }
            } finally {
                admission.release(access.principalId, accepted.token)
            }
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
            MahjongChannels.historySummaryResponse.sendTo(player, json, HistorySummaryResponseDto(request.requestId, errorCode = accepted.errorCode()))
            return
        }
        scope.launch {
            try {
                var response = withTimeoutOrNull(HistoryQueryLimits.timeout) {
                    try {
                        val domain = HistorySummaryRequest(Uuid.parse(request.matchId), request.scope.toDomain())
                        val repository = FabricHistoryQueryRepository(writer, sessionId)
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
}

/**
 * 只讀取連線身分，不接受 payload 宣稱的權限。
 *
 * @return 目前 UUID 與 OP 2 資格。
 */
private fun ServerPlayerEntity.queryAccess(): HistoryQueryAccess = HistoryQueryAccess(uuid.toKotlinUuid(), hasPermissionLevel(2))

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
