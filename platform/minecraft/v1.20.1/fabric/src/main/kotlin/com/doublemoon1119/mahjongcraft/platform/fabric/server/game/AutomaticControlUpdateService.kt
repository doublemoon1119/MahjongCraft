package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultKindDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.toDto
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.UpdatePlayerAutomaticControlsResult
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.UpdatePlayerAutomaticControlsUseCase
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 驗證 Fabric 更新請求、套用本人本局自動操作集合，並建立可配對的權威回覆。 */
@Single
class AutomaticControlUpdateService(
    private val updateControls: UpdatePlayerAutomaticControlsUseCase,
    private val gameFlowCoordinator: GameFlowCoordinator,
) {
    /** 處理 [request]；[playerId] 必須來自已驗證的網路連線，不能來自 payload。 */
    suspend operator fun invoke(
        playerId: Uuid,
        request: AutomaticControlUpdateRequestDto,
    ): AutomaticControlUpdateResultDto {
        val gameId = runCatching { Uuid.parse(request.gameId) }.getOrNull()
            ?: return request.result(AutomaticControlUpdateResultKindDto.UNAVAILABLE)
        val result = updateControls(
            gameId = gameId,
            playerId = playerId,
            expectedRevision = request.expectedRevision,
            enabledControlIds = request.enabledControlIds,
        )
        if (result is UpdatePlayerAutomaticControlsResult.Accepted && result.changed) {
            gameFlowCoordinator.driveAutomatedPlayers(gameId)
        }
        return result.toDto(request)
    }
}

/** 將 Flow 權威結果映射成保留原 request 配對欄位的線路結果。 */
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

/** 建立保留原 request 配對欄位的更新結果。 */
private fun AutomaticControlUpdateRequestDto.result(
    result: AutomaticControlUpdateResultKindDto,
    snapshot: AutomaticControlSnapshotDto? = null,
): AutomaticControlUpdateResultDto = AutomaticControlUpdateResultDto(
    requestId = requestId,
    gameId = gameId,
    result = result,
    snapshot = snapshot,
)
