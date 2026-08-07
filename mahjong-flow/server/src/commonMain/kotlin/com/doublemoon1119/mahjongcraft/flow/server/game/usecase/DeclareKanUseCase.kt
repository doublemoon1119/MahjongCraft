package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.logic.util.withoutRed
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 宣告暗槓（[GameAction.KanType.CLOSED_KAN]）或加槓（[GameAction.KanType.ADDED_KAN]）的實例化用例。
 *
 * 是否合法完全交給該規則模組自己的 `LegalActionValidator`——這裡不重新實作暗槓/加槓的偵測邏輯
 * （含立直後暗槓「不能改變聽牌」的限制），理由與 [DeclareRiichiUseCase]、
 * [DeclareKyuushuKyuuhaiUseCase] 相同。套用副露後依序記錄 [GameAction.Kan] → 從死牌區
 * （[com.doublemoon1119.mahjongcraft.logic.table.TileWall.drawLast]）補摸嶺上牌並記錄
 * [GameAction.Draw]，讓
 * [com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueContextCalculator] 既有的
 * 嶺上開花偵測邏輯（依賴 `actionHistory` 最後兩筆是否恰為 `[Kan, Draw]`）能真正被觸發。
 *
 * 不需要新增任何 [com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule] 規則鉤子——
 * 套用副露、補摸嶺上牌、清除全場一發皆為既有通用方法（`Hand.call`/`Hand.upgradeToAddedKan`/
 * `TileWall.drawLast`/`module.onMeldClaimed`）的組合。不涉及包牌（Pao）：加槓沿用
 * `Hand.upgradeToAddedKan` 保留的原碰副露來源方位，暗槓沒有鳴牌來源，兩者皆不構成包牌，不呼叫
 * `applyPaoLiabilityIfTriggered`。
 *
 * 明牌（[GameAction.KanType.OPEN_KAN]，反應別人的捨牌）不在本用例範圍內，走 [RespondToDiscardUseCase]。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析當前對局的合法動作判定器。
 * @property gameSnapshotRepository 對局快照數據倉庫。
 * @property eventPublisher 對局通知服務。
 */
@Factory
class DeclareKanUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val gameSnapshotRepository: GameSnapshotRepository,
    @Provided private val eventPublisher: GameEventPublisher,
) {
    /**
     * 執行暗槓/加槓宣告邏輯。
     *
     * @param gameId 對局 Uuid。
     * @param playerId 發起宣告的玩家 Uuid。
     * @param kanType 欲宣告的槓牌種類（僅支援 [GameAction.KanType.CLOSED_KAN]／[GameAction.KanType.ADDED_KAN]）。
     * @param tileId 觸發槓牌的牌的唯一識別碼，必須等於玩家目前的 `hand.lastDrawn.id`。
     * @return 宣告結果，成功時為 [Unit]，失敗時為 [GameError]。
     */
    suspend operator fun invoke(gameId: Uuid, playerId: Uuid, kanType: GameAction.KanType, tileId: Uuid): Outcome<Unit, GameError> {
        // 1. 以原子方式讀取桌況、驗證業務規則並寫回
        val outcome = gameRepository.update(gameId) { state ->
            when {
                state == null -> state to Outcome.Error(GameError.GameNotFound(gameId))
                state.players.none { it.id == playerId } ->
                    state to Outcome.Error(GameError.PlayerNotInGame(playerId, gameId))
                state.currentPlayer.id != playerId ->
                    state to Outcome.Error(GameError.NotPlayersTurn(playerId, gameId))
                else -> {
                    val incomingTile = state.currentPlayer.hand.lastDrawn?.takeIf { it.id == tileId }
                        ?: return@update state to Outcome.Error(
                            GameError.IllegalAction(playerId, gameId, GameAction.Kan(kanType, tileId, emptyList())),
                        )
                    val module = moduleRegistry.getModule(state.config)

                    // LegalActionValidator 的既有慣例是傳入的手牌「不含胡牌張」，胡牌張只透過
                    // incomingTile 參數單獨傳入；剝離手法與 DeclareTsumoUseCase/
                    // DeclareKyuushuKyuuhaiUseCase 相同（見規劃紀錄：暗槓判定 closedKanCount 若不
                    // 剝離 lastDrawn 會多算一張）。
                    val playerForCheck = state.currentPlayer.copy(hand = state.currentPlayer.hand.copy(lastDrawn = null))
                    val legalActions = module.createLegalActionValidator().getLegalActions(
                        tableState = state,
                        player = playerForCheck,
                        sourceAction = GameAction.Draw,
                        sourceDirection = RelativeDirection.Self,
                        incomingTile = incomingTile,
                    )
                    val kanAction = legalActions.filterIsInstance<GameAction.Kan>().firstOrNull { it.type == kanType }
                        ?: return@update state to Outcome.Error(
                            GameError.IllegalAction(playerId, gameId, GameAction.Kan(kanType, tileId, emptyList())),
                        )

                    val handAfterMeld = when (kanType) {
                        GameAction.KanType.CLOSED_KAN -> {
                            val kanTiles = kanAction.withTiles.mapNotNull { id ->
                                state.currentPlayer.hand.standingTiles.find { it.id == id }
                            } + incomingTile
                            state.currentPlayer.hand.call(MeldType.CLOSED_KAN, kanTiles, source = null, direction = RelativeDirection.Self)
                        }

                        GameAction.KanType.ADDED_KAN -> {
                            val targetMeldIndex = state.currentPlayer.hand.exposedMelds.indexOfFirst {
                                it.type == MeldType.PON && it.tiles.first().tile.withoutRed == incomingTile.tile.withoutRed
                            }
                            state.currentPlayer.hand.upgradeToAddedKan(incomingTile, targetMeldIndex)
                        }

                        GameAction.KanType.OPEN_KAN ->
                            return@update state to Outcome.Error(
                                GameError.IllegalAction(playerId, gameId, GameAction.Kan(kanType, tileId, emptyList())),
                            )
                    }

                    val (rinshanTile, newWall) = state.tileWall.drawLast()
                    if (rinshanTile == null) {
                        return@update state to Outcome.Error(GameError.WallExhausted(gameId))
                    }

                    val playerAfterMeld = state.currentPlayer.copy(hand = handAfterMeld).recordAction(kanAction)
                    val playerAfterDraw = playerAfterMeld
                        .copy(hand = playerAfterMeld.hand.copy(lastDrawn = rinshanTile))
                        .clearPassedTiles()
                        .recordAction(GameAction.Draw)
                    val playersAfterMeld = state.players.map { if (it.id == playerId) playerAfterDraw else it }
                    val playersAfterMeldClaimed = module.onMeldClaimed(playersAfterMeld)

                    val newState = state.copy(players = playersAfterMeldClaimed, tileWall = newWall)
                    newState to Outcome.Success(KanResult(newState, kanAction))
                }
            }
        }

        if (outcome is Outcome.Error) return outcome
        val result = (outcome as Outcome.Success).value
        val newState = result.tableState

        // 2. 同步快照給所有正在觀察的玩家
        val observers = gameSnapshotRepository.getAllObservers(gameId)
        observers.forEach { observerId ->
            gameSnapshotRepository.setSnapshot(observerId, newState.toSnapshot(observerId))
        }

        // 3. 依序廣播槓牌與補摸嶺上牌事件
        newState.players.forEach { player ->
            eventPublisher.publish(gameId, player.id, playerId, result.kanAction)
            eventPublisher.publish(gameId, player.id, playerId, GameAction.Draw)
        }

        return Outcome.Success(Unit)
    }

    /**
     * `update` 區塊內部使用的中繼結果，讓 [kanAction] 能跟著 [tableState] 一起帶出
     * `gameRepository.update` 的作用域，供廣播事件時使用。
     */
    private data class KanResult(val tableState: TableState, val kanAction: GameAction.Kan)
}
