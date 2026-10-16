package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultKindDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.toDto
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AutomatedAdvanceManager
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.UpdatePlayerAutomaticControlsResult
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.UpdatePlayerAutomaticControlsUseCase
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.network.ClientRequestIds
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.FabricMahjongDataStatus
import kotlinx.coroutines.CancellationException
import kotlin.uuid.Uuid

/**
 * 驗證 Fabric 更新請求、套用本人本局自動操作集合，並建立可配對的權威回覆。
 *
 * @property updateControls 套用自動操作集合的用例。
 * @property advanceManager 集合實際改變時請求推進，讓新啟用的自動操作立即生效。
 * @property dataStatus 麻將資料無法讀取時回覆無法使用。
 * @property failurePolicy 判斷套用時丟出的失敗能否收斂為無法使用；協程取消與無法隔離的失敗原樣往外傳。
 */
class AutomaticControlUpdateService(
    private val updateControls: UpdatePlayerAutomaticControlsUseCase,
    private val advanceManager: AutomatedAdvanceManager,
    private val dataStatus: FabricMahjongDataStatus,
    private val failurePolicy: ExtensionFailurePolicy,
) {
    /** 記錄套用自動操作失敗的 logger。 */
    private val logger = mahjongCraftLogger(AutomaticControlUpdateService::class)

    /**
     * 處理 [request]；[playerId] 必須來自已驗證的網路連線，不能來自 payload。
     *
     * 回覆會原樣帶回請求識別字串，因此識別字串不符合 [ClientRequestIds] 的要求直接丟棄，不回覆。
     * 套用時發生可以隔離的失敗時回覆無法使用。
     *
     * @param playerId 已驗證連線的玩家識別碼。
     * @param request 客戶端自動操作更新要求。
     * @return 與要求配對的更新結果；請求識別字串無效時為 null。
     */
    suspend operator fun invoke(
        playerId: Uuid,
        request: AutomaticControlUpdateRequestDto,
    ): AutomaticControlUpdateResultDto? {
        if (!ClientRequestIds.isValid(request.requestId)) return null
        if (!dataStatus.isMahjongAvailable) return request.result(AutomaticControlUpdateResultKindDto.UNAVAILABLE)
        val gameId = runCatching { Uuid.parse(request.gameId) }.getOrNull()
            ?: return request.result(AutomaticControlUpdateResultKindDto.UNAVAILABLE)
        val result = updateIsolatingFailures(
            failurePolicy = failurePolicy,
            onFailure = { throwable -> logger.error("Failed to update automatic controls for player {}", playerId, throwable) },
        ) {
            updateControls(
                gameId = gameId,
                playerId = playerId,
                expectedRevision = request.expectedRevision,
                enabledControlIds = request.enabledControlIds,
            )
        } ?: return request.result(AutomaticControlUpdateResultKindDto.UNAVAILABLE)
        if (result is UpdatePlayerAutomaticControlsResult.Accepted && result.changed) {
            advanceManager.request(gameId)
        }
        return result.toDto(request)
    }
}

/**
 * 套用自動操作更新，把可以隔離的失敗收斂成 null。
 *
 * @param failurePolicy 判斷失敗能否隔離；協程取消與無法隔離的失敗原樣往外傳。
 * @param onFailure 可以隔離的失敗發生時的通知回呼。
 * @param update 套用更新並取得權威結果的掛起操作。
 * @return 權威結果；發生可以隔離的失敗時為 null。
 */
internal suspend fun updateIsolatingFailures(
    failurePolicy: ExtensionFailurePolicy,
    onFailure: (Throwable) -> Unit,
    update: suspend () -> UpdatePlayerAutomaticControlsResult,
): UpdatePlayerAutomaticControlsResult? = try {
    update()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    if (!failurePolicy.isIsolatable(throwable)) throw throwable
    onFailure(throwable)
    null
}

/**
 * 將 Flow 權威結果映射成保留原 request 配對欄位的線路結果。
 *
 * @param request 原始請求，用於保留配對欄位。
 * @return 對應的線路結果。
 */
internal fun UpdatePlayerAutomaticControlsResult.toDto(
    request: AutomaticControlUpdateRequestDto,
): AutomaticControlUpdateResultDto = when (this) {
    is UpdatePlayerAutomaticControlsResult.Accepted -> request.result(
        AutomaticControlUpdateResultKindDto.ACCEPTED,
        snapshot.toDto(),
    )
    is UpdatePlayerAutomaticControlsResult.Stale -> request.result(
        AutomaticControlUpdateResultKindDto.STALE,
        snapshot.toDto(),
    )
    is UpdatePlayerAutomaticControlsResult.Rejected -> request.result(
        AutomaticControlUpdateResultKindDto.REJECTED,
        snapshot?.toDto(),
    )
    is UpdatePlayerAutomaticControlsResult.Unavailable -> request.result(
        AutomaticControlUpdateResultKindDto.UNAVAILABLE,
    )
}

/**
 * 建立保留原 request 配對欄位的更新結果。
 *
 * @param result 權威更新結果分類。
 * @param snapshot 可選的最新自動操作快照。
 * @return 沿用此要求識別欄位的回覆 DTO。
 */
private fun AutomaticControlUpdateRequestDto.result(
    result: AutomaticControlUpdateResultKindDto,
    snapshot: AutomaticControlSnapshotDto? = null,
): AutomaticControlUpdateResultDto = AutomaticControlUpdateResultDto(
    requestId = requestId,
    gameId = gameId,
    result = result,
    snapshot = snapshot,
)
