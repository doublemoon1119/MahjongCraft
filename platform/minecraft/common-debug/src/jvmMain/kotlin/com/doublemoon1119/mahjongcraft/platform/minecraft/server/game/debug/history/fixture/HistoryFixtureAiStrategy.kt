package com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.history.fixture

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.AiDecisionPhase
import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategy
import com.doublemoon1119.mahjongcraft.ai.RuleDecisionAiContext
import com.doublemoon1119.mahjongcraft.ai.defaultSubmission
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTileSnapshot
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlin.uuid.Uuid

/**
 * 照 [HistoryFixtureScript] 做決定的 AI：腳本有寫的照做，沒寫的時候輪到自己就打出剛摸到的牌，回應一律放過。
 *
 * 不使用任何亂數，同樣的對局一定做出同樣的決定。腳本要求的動作此時不合法時記下原因，改用預設的決定讓對局繼續。
 *
 * @property script 這次產生的腳本進度。
 * @property extensionActions 把立直等規則擴充動作轉成命令。
 */
class HistoryFixtureAiStrategy(
    private val script: HistoryFixtureScript,
    private val extensionActions: ExtensionGameActionAiRegistry,
) : MahjongAiStrategy {
    override suspend fun decideGameCommand(context: AiDecisionContext): GameCommand {
        val self = context.snapshot.players.first { it.id == context.selfId }
        return when (context.phase) {
            AiDecisionPhase.OwnTurn -> ownTurn(context, self.seatWind)
            AiDecisionPhase.RespondingToDiscard -> respondToDiscard(context, self.seatWind)
            AiDecisionPhase.RespondingToRobbing -> {
                val ron = context.legalActions.firstOrNull { it is GameAction.Ron }
                if (ron != null && script.next(self.seatWind) == HistoryFixtureAction.Ron) {
                    script.consume(self.seatWind)
                    GameCommand.RespondToRobbing(ron)
                } else {
                    GameCommand.RespondToRobbing(GameAction.Pass)
                }
            }
        }
    }

    override suspend fun decideRuleDecision(context: RuleDecisionAiContext) = context.defaultSubmission()

    /**
     * 輪到自己時的決定。
     *
     * @param context 決策情境。
     * @param wind 這一家的自風。
     * @return 要執行的命令。
     */
    private fun ownTurn(context: AiDecisionContext, wind: Wind): GameCommand {
        val action = script.next(wind)?.takeIf(HistoryFixtureAction::ownTurn) ?: return discardDrawn(context, wind)
        val command = when (action) {
            is HistoryFixtureAction.Discard -> handTiles(context).firstOrNull { it.tile == action.tile.tile && discardable(context, it.id) }?.let { GameCommand.Discard(it.id) }
            is HistoryFixtureAction.Riichi -> context.legalActions.filterIsInstance<GameAction.Extension>()
                .filter { it.value.id == RiichiGameAction.Riichi.id }
                .flatMap { extensionActions.createCandidates(it.value, context) }
                .firstOrNull { candidate -> handTiles(context).any { it.id == candidate.discardTileId && it.tile == action.tile.tile } }
                ?.command
            HistoryFixtureAction.Tsumo -> GameCommand.Tsumo.takeIf { GameAction.Tsumo in context.legalActions }
            is HistoryFixtureAction.ClosedKan -> kan(context, GameAction.KanType.CLOSED_KAN, action.tile.tile)
            is HistoryFixtureAction.AddedKan -> kan(context, GameAction.KanType.ADDED_KAN, action.tile.tile)
            else -> null
        }
        if (command == null) {
            script.fail("$wind cannot $action now")
            script.consume(wind)
            return discardDrawn(context, wind)
        }
        script.consume(wind)
        return command
    }

    /**
     * 回應捨牌：腳本下一個決定是這次可以做的回應時照做，否則放過。
     *
     * @param context 決策情境。
     * @param wind 這一家的自風。
     * @return 回應命令。
     */
    private fun respondToDiscard(context: AiDecisionContext, wind: Wind): GameCommand {
        val reactionId = checkNotNull(context.discardReactionId)
        val hand = handTiles(context).associate { it.id to it.tile }
        val action = script.next(wind)?.takeUnless(HistoryFixtureAction::ownTurn)
        val chosen = when (action) {
            HistoryFixtureAction.Ron -> context.legalActions.firstOrNull { it is GameAction.Ron }
            is HistoryFixtureAction.Pon -> context.legalActions.filterIsInstance<GameAction.Pon>().firstOrNull { pon -> pon.withTiles.all { hand[it] == action.tile.tile } }
            is HistoryFixtureAction.OpenKan -> context.legalActions.filterIsInstance<GameAction.Kan>()
                .firstOrNull { kan -> kan.type == GameAction.KanType.OPEN_KAN && kan.withTiles.all { hand[it] == action.tile.tile } }
            is HistoryFixtureAction.Chi -> context.legalActions.filterIsInstance<GameAction.Chi>()
                .firstOrNull { chi -> chi.withTiles.mapNotNull { hand[it] }.sortedBy(Tile::toString) == action.handTiles.map { it.tile }.sortedBy(Tile::toString) }
            else -> null
        } ?: return GameCommand.RespondToDiscard(GameAction.Pass, reactionId)
        script.consume(wind)
        val withTiles = when (chosen) {
            is GameAction.Pon -> chosen.withTiles
            is GameAction.Chi -> chosen.withTiles
            is GameAction.Kan -> chosen.withTiles
            else -> null
        }
        return if (withTiles == null) GameCommand.RespondToDiscard(chosen, reactionId) else GameCommand.RespondToDiscard(chosen, reactionId, withTiles)
    }

    /**
     * 打出剛摸到的牌；剛摸到的牌不能打或沒有剛摸到的牌時，記下腳本少寫了這一步，改打第一張可以打的牌。
     *
     * @param context 決策情境。
     * @param wind 這一家的自風。
     * @return 捨牌命令。
     */
    private fun discardDrawn(context: AiDecisionContext, wind: Wind): GameCommand {
        val drawn = context.snapshot.players.first { it.id == context.selfId }.hand.lastDrawn
        if (drawn != null && discardable(context, drawn.id)) return GameCommand.Discard(drawn.id)
        script.fail("$wind has no scripted discard and no drawn tile to discard")
        return GameCommand.Discard(handTiles(context).first { discardable(context, it.id) }.id)
    }

    /**
     * 指定種類與牌面的槓。
     *
     * @param context 決策情境。
     * @param type 槓的種類。
     * @param tile 槓的牌面。
     * @return 槓的命令；此時不能這樣槓時為 null。
     */
    private fun kan(context: AiDecisionContext, type: GameAction.KanType, tile: Tile): GameCommand? {
        val hand = handTiles(context).associate { it.id to it.tile }
        val kan = context.legalActions.filterIsInstance<GameAction.Kan>().firstOrNull { it.type == type && hand[it.tileId] == tile } ?: return null
        return GameCommand.Kan(kan.type, kan.tileId)
    }

    /**
     * 自己手上的牌，包含剛摸到的牌，剛摸到的排在最前面。
     *
     * @param context 決策情境。
     * @return 手上的牌。
     */
    private fun handTiles(context: AiDecisionContext): List<IdentifiedTileSnapshot> {
        val hand = context.snapshot.players.first { it.id == context.selfId }.hand
        return (listOfNotNull(hand.lastDrawn) + hand.standingTiles).distinctBy(IdentifiedTileSnapshot::id)
    }

    /**
     * 規則是否允許打出這張牌。
     *
     * @param context 決策情境。
     * @param tileId 牌的識別碼。
     * @return 可以打出時為 true。
     */
    private fun discardable(context: AiDecisionContext, tileId: Uuid): Boolean = context.discardableTileIds?.contains(tileId) ?: true
}
