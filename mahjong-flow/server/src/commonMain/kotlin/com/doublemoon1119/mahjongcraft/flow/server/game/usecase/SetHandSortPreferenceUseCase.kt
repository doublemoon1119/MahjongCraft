package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import kotlin.uuid.Uuid

/**
 * 設定「是否自動整理手牌」偏好（[HandSortPreferenceStore]）；啟用時，若玩家目前正坐在進行中對局且沒有尚未決定的摸牌，會立即整理
 * 並重新呈現手牌，不用等到下一次摸牌／打牌才生效。停用時只更新偏好，不會立即改變目前手牌順序。
 *
 * 若玩家手上還有一張尚未決定的摸牌（`Hand.lastDrawn != null`），這次先不整理——`Hand.organize` 會把
 * `lastDrawn` 併入排序結果，在玩家還沒決定要不要留下這張牌之前這麼做會打斷現有「摸牌獨立顯示」的
 * 呈現設計，等下一次真正呼叫 `organize()` 的時機點（打牌／鳴牌後）自然套用即可。
 *
 * @property gameRepository 權威對局資料倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於取得手牌排序規則。
 * @property preferenceStore 玩家自動理牌偏好儲存。
 * @property presentationPublisher 對局呈現更新觸發器。
 */
class SetHandSortPreferenceUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val preferenceStore: HandSortPreferenceStore,
    private val presentationPublisher: GamePresentationPublisher,
) {
    /**
     * 依 [mode] 更新 [enabled] 偏好；重連恢復只更新暫存值。玩家主動啟用時，若目前手牌可安全整理才立即整理並重新發布；停用不會整理手牌。
     *
     * 重連可能發生在開局發牌尚未呈現完時；若誤當成 [HandSortPreferenceUpdateMode.USER_CHANGE]，重新發布手牌會打斷
     * 尚未完成的開局發牌呈現。
     *
     * @param playerId 更新偏好的玩家識別碼。
     * @param enabled 是否啟用自動理牌。
     * @param mode 更新來源及允許套用的方式。
     */
    suspend operator fun invoke(
        playerId: Uuid,
        enabled: Boolean,
        mode: HandSortPreferenceUpdateMode,
    ) {
        preferenceStore.set(playerId, enabled)
        if (mode == HandSortPreferenceUpdateMode.RESTORE) return
        if (!enabled) return

        val gameId = gameRepository.getAllGameIds().firstOrNull { id ->
            gameRepository.getTableState(id)?.players?.any { it.id == playerId } == true
        } ?: return

        val newState = gameRepository.update(gameId) { state ->
            val player = state?.players?.firstOrNull { it.id == playerId }
            if (state == null || player == null || player.hand.lastDrawn != null) {
                state to null
            } else {
                val module = moduleRegistry.getModule(state.config)
                val organizedPlayer = player.copy(hand = player.hand.organize(module.tileOrder))
                val updatedState = state.copy(players = state.players.map { if (it.id == playerId) organizedPlayer else it })
                updatedState to updatedState
            }
        } ?: return

        val seatIndex = newState.players.indexOfFirst { it.id == playerId }
        val player = newState.players[seatIndex]
        val module = moduleRegistry.getModule(newState.config)
        presentationPublisher.publishPlayerTilesUpdated(
            gameId,
            seatIndex,
            player.hand.tiles.map { it.id },
            player.hand.lastDrawn?.id,
            player.hand.melds.map { it.toPresentation(newState.config.revealsClosedKanTiles) },
            setAsideTileIds = module.setAsideTiles(player).map { it.id },
        )
    }
}
