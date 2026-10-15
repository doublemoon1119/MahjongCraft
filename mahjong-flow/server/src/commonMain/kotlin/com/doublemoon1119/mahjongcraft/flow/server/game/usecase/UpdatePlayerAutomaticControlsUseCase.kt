package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerAutomaticControlSnapshot
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.requireValidAutomaticControlIds
import kotlin.uuid.Uuid

/**
 * 以 revision 保護的原子操作替換單一玩家在目前這一局啟用的自動操作集合。
 *
 * 本用例只接受完整集合，並在同一次 [GameRepository.updateGame] 中完成成員、規則支援清單及 revision
 * 驗證。呼叫端必須以可信任的連線身分提供 [playerId]，不得採用提交內容自行宣稱的玩家 ID。
 *
 * @property gameRepository 權威對局資料倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於取得支援的自動操作 ID。
 */
class UpdatePlayerAutomaticControlsUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
) {
    /**
     * 嘗試將 [playerId] 的本局控制替換成 [enabledControlIds]。
     *
     * @param gameId 對局識別碼。
     * @param playerId 由可信任呼叫邊界確認的玩家識別碼。
     * @param expectedRevision 提交草稿所依據的權威 revision。
     * @param enabledControlIds 欲啟用的完整控制 ID 集合。
     * @return 權威更新結果，包含接受、版本過期、拒絕或無法提供狀態的情況。
     */
    suspend operator fun invoke(
        gameId: Uuid,
        playerId: Uuid,
        expectedRevision: Long,
        enabledControlIds: Set<String>,
    ): UpdatePlayerAutomaticControlsResult = gameRepository.updateGame(gameId) { game ->
        if (game == null) {
            return@updateGame null to UpdatePlayerAutomaticControlsResult.Unavailable(
                AutomaticControlUpdateUnavailableReason.GAME_NOT_FOUND,
            )
        }
        if (game.isMatchOver) {
            return@updateGame game to UpdatePlayerAutomaticControlsResult.Unavailable(
                AutomaticControlUpdateUnavailableReason.MATCH_OVER,
            )
        }
        if (game.tableState.players.none { player -> player.id == playerId }) {
            return@updateGame game to UpdatePlayerAutomaticControlsResult.Rejected(
                AutomaticControlUpdateRejection.PLAYER_NOT_IN_GAME,
            )
        }

        val supportedControlIds = moduleRegistry.getModule(game.tableState.config).getSupportedAutomaticControlIds()
        requireValidAutomaticControlIds(supportedControlIds)
        val currentSnapshot = game.toAutomaticControlSnapshot(playerId, supportedControlIds)

        if (enabledControlIds.any { controlId -> !NamespacedId.isValid(controlId) }) {
            return@updateGame game to UpdatePlayerAutomaticControlsResult.Rejected(
                AutomaticControlUpdateRejection.INVALID_CONTROL_ID,
                currentSnapshot,
            )
        }
        if (enabledControlIds.any { controlId -> controlId !in supportedControlIds }) {
            return@updateGame game to UpdatePlayerAutomaticControlsResult.Rejected(
                AutomaticControlUpdateRejection.UNSUPPORTED_CONTROL_ID,
                currentSnapshot,
            )
        }
        if (expectedRevision != game.automaticControlRevision) {
            return@updateGame game to UpdatePlayerAutomaticControlsResult.Stale(currentSnapshot)
        }

        val currentEnabledIds = game.enabledAutomaticControlIdsByPlayerId[playerId].orEmpty()
        if (currentEnabledIds == enabledControlIds) {
            return@updateGame game to UpdatePlayerAutomaticControlsResult.Accepted(currentSnapshot, changed = false)
        }

        val updatedByPlayerId = game.enabledAutomaticControlIdsByPlayerId.toMutableMap().apply {
            if (enabledControlIds.isEmpty()) remove(playerId) else put(playerId, enabledControlIds.toSet())
        }
        val updatedGame = game.copy(
            enabledAutomaticControlIdsByPlayerId = updatedByPlayerId,
            automaticControlRevision = game.automaticControlRevision + 1L,
        )
        updatedGame to UpdatePlayerAutomaticControlsResult.Accepted(
            updatedGame.toAutomaticControlSnapshot(playerId, supportedControlIds),
            changed = true,
        )
    }
}

/**
 * 建立只包含 [playerId] 自身控制集合的權威快照。
 *
 * @param playerId 快照所屬玩家的識別碼。
 * @param supportedControlIds 規則支援的自動操作 ID 集合。
 * @return 僅包含指定玩家已啟用控制的權威快照。
 */
private fun Game.toAutomaticControlSnapshot(
    playerId: Uuid,
    supportedControlIds: Set<String>,
): PlayerAutomaticControlSnapshot = PlayerAutomaticControlSnapshot(
    gameId = id,
    revision = automaticControlRevision,
    supportedControlIds = supportedControlIds,
    enabledControlIds = enabledAutomaticControlIdsByPlayerId[playerId].orEmpty(),
)
