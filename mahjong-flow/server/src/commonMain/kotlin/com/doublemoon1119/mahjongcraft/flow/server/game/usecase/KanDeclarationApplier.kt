package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawContext
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawDecision
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 原子套用暗槓／加槓與規則提供的後續補牌結果。
 *
 * 本物件只負責通用手牌變化、結果驗證及 action history；補牌來源、上限、牌牆變化與新增公開牌完全由
 * [MahjongRuleModule.createSupplementalDrawPolicy] 決定。
 */
internal object KanDeclarationApplier {
    /** 槓牌與補牌的套用結果。 */
    sealed interface Result {
        /**
         * 完整動作已成功套用。
         *
         * @property tableState 更新後桌況。
         * @property drawnTiles 規則實際補到的牌張。
         * @property newlyRevealedTileIds 本次新公開的牌張 ID。
         */
        data class Applied(
            val tableState: TableState,
            val drawnTiles: List<IdentifiedTile>,
            val newlyRevealedTileIds: Set<Uuid>,
        ) : Result

        /**
         * 規則拒絕補牌或回傳無法安全套用的結果。
         *
         * @property reasonId 完整 namespaced 原因 ID。
         */
        data class Rejected(val reasonId: String) : Result
    }

    /**
     * 套用本次暗槓或加槓，並將候選桌況交給規則補牌 policy。
     *
     * @param state 尚未套用本次槓牌的權威桌況。
     * @param declarerId 宣告槓牌的玩家 ID。
     * @param kanAction 本次暗槓或加槓動作。
     * @param incomingTile 觸發本次槓牌的牌張。
     * @param module 對局採用的規則模組。
     */
    fun apply(
        state: TableState,
        declarerId: Uuid,
        kanAction: GameAction.Kan,
        incomingTile: IdentifiedTile,
        module: MahjongRuleModule<*>,
    ): Result {
        val declarer = state.players.first { it.id == declarerId }
        val handAfterMeld = when (kanAction.type) {
            GameAction.KanType.CLOSED_KAN -> {
                val kanTiles = kanAction.withTiles.mapNotNull { id ->
                    declarer.hand.standingTiles.find { it.id == id }
                } + incomingTile
                declarer.hand.call(MeldType.CLOSED_KAN, kanTiles, source = null, direction = RelativeDirection.Self)
            }

            GameAction.KanType.ADDED_KAN -> {
                val tileInterpretation = module.createTileInterpretationPolicy()
                val targetMeldIndex = declarer.hand.exposedMelds.indexOfFirst {
                    it.type == MeldType.PON &&
                        tileInterpretation.canonicalize(it.tiles.first().tile) ==
                        tileInterpretation.canonicalize(incomingTile.tile)
                }
                declarer.hand.upgradeToAddedKan(incomingTile, targetMeldIndex)
            }

            GameAction.KanType.OPEN_KAN -> error("Unreachable: OPEN_KAN is applied by RespondToDiscardUseCase")
        }

        val handReadyForSupplement = handAfterMeld.lastDrawn?.let { drawnTile ->
            handAfterMeld.copy(tiles = handAfterMeld.tiles + drawnTile, lastDrawn = null)
        } ?: handAfterMeld
        val declarerAfterMeld = declarer.copy(hand = handReadyForSupplement).recordAction(kanAction)
        val playersAfterMeld = state.players.map { player ->
            if (player.id == declarerId) declarerAfterMeld else player
        }
        val candidateState = state.copy(players = module.onMeldClaimed(playersAfterMeld))
        return applySupplementalDraw(state, candidateState, declarerId, kanAction, module)
    }

    /**
     * 驗證並套用規則補牌結果；明槓與暗槓／加槓共用此入口。
     *
     * @param originalState 動作套用前的權威桌況。
     * @param candidateState 已套用副露、尚未補牌的候選桌況。
     * @param actorPlayerId 執行動作的玩家 ID。
     * @param action 已套用的動作。
     * @param module 對局採用的規則模組。
     */
    fun applySupplementalDraw(
        originalState: TableState,
        candidateState: TableState,
        actorPlayerId: Uuid,
        action: GameAction,
        module: MahjongRuleModule<*>,
    ): Result {
        val decision = module.createSupplementalDrawPolicy().resolve(
            SupplementalDrawContext(originalState, candidateState, actorPlayerId, action),
        )
        return when (decision) {
            SupplementalDrawDecision.NotRequired -> Result.Applied(candidateState, emptyList(), emptySet())
            is SupplementalDrawDecision.Rejected -> Result.Rejected(decision.reasonId)
            is SupplementalDrawDecision.Completed -> applyCompletedDecision(
                originalState,
                candidateState,
                actorPlayerId,
                decision,
            )
        }
    }

    /** 驗證牌張守恆與公開範圍，成功後把補牌寫入執行者手牌。 */
    internal fun applyCompletedDecision(
        originalState: TableState,
        candidateState: TableState,
        actorPlayerId: Uuid,
        decision: SupplementalDrawDecision.Completed,
    ): Result {
        if (decision.drawnTiles.isEmpty()) return Result.Rejected(SupplementalDrawReasonIds.INVALID_RESULT)
        val originalWallTiles = originalState.tileWall.getAllTiles() + originalState.reservedWallTiles
        val resultingWallTiles = decision.tileWall.getAllTiles() + decision.reservedWallTiles + decision.drawnTiles
        if (originalWallTiles.map { it.id }.toSet() != resultingWallTiles.map { it.id }.toSet() ||
            resultingWallTiles.map { it.id }.distinct().size != resultingWallTiles.size
        ) {
            return Result.Rejected(SupplementalDrawReasonIds.INVALID_RESULT)
        }
        val updatedWallIds = (decision.tileWall.getAllTiles() + decision.reservedWallTiles).mapTo(mutableSetOf()) { it.id }
        if (!updatedWallIds.containsAll(decision.newlyRevealedTileIds)) {
            return Result.Rejected(SupplementalDrawReasonIds.INVALID_RESULT)
        }

        val actorIndex = candidateState.players.indexOfFirst { it.id == actorPlayerId }
        if (actorIndex == -1) return Result.Rejected(SupplementalDrawReasonIds.INVALID_RESULT)
        val actor = candidateState.players[actorIndex]
        val handAfterDraw = actor.hand.copy(
            tiles = actor.hand.tiles + decision.drawnTiles.dropLast(1),
            lastDrawn = decision.drawnTiles.last(),
        )
        val actorAfterDraw = actor.copy(hand = handAfterDraw).clearPassedTiles().recordAction(GameAction.Draw)
        val updatedPlayers = candidateState.players.toMutableList().apply { this[actorIndex] = actorAfterDraw }
        val updatedState = candidateState.copy(
            players = updatedPlayers,
            tileWall = decision.tileWall,
            initialDeadWall = decision.reservedWallTiles,
            dynamicRuleState = decision.dynamicRuleState,
        )
        return Result.Applied(updatedState, decision.drawnTiles, decision.newlyRevealedTileIds)
    }
}
