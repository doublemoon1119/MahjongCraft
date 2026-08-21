package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 設定「是否自動整理手牌」偏好（[HandSortPreferenceStore]），並在玩家目前正坐在進行中對局時立即套用
 * 一次整理，讓切換開關這個動作本身就能看到手牌重新排序，不用等到下一次摸牌/打牌才生效。
 *
 * 若玩家手上還有一張尚未決定的摸牌（`Hand.lastDrawn != null`），這次先不整理——`Hand.organize` 會把
 * `lastDrawn` 併入排序結果，在玩家還沒決定要不要留下這張牌之前這麼做會打斷現有「摸牌獨立顯示」的
 * 呈現設計，等下一次真正呼叫 `organize()` 的時機點（打牌／鳴牌後）自然套用即可。
 */
@Factory
class SetHandSortPreferenceUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val preferenceStore: HandSortPreferenceStore,
    @Provided private val presentationPublisher: GamePresentationPublisher,
) {
    suspend operator fun invoke(playerId: Uuid, enabled: Boolean) {
        preferenceStore.set(playerId, enabled)
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
        val dealerSeatIndex = newState.players.indexOfFirst { it.currentWind == Wind.EAST }
        presentationPublisher.publishPlayerAreaUpdated(
            gameId,
            seatIndex,
            player.hand.tiles.map { it.id },
            player.hand.lastDrawn?.id,
            player.hand.melds.map { it.toPresentation(newState.config.revealsClosedKanTiles) },
            comboStickCount = if (seatIndex == dealerSeatIndex) newState.comboCount else 0,
        )
    }
}
