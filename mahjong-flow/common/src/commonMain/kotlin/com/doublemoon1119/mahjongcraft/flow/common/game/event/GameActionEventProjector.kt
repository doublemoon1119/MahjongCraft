package com.doublemoon1119.mahjongcraft.flow.common.game.event

import com.doublemoon1119.mahjongcraft.flow.api.event.PerformedAction
import com.doublemoon1119.mahjongcraft.flow.api.event.PerformedActionKind
import com.doublemoon1119.mahjongcraft.flow.api.event.PublicTileFace
import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/** 只投影已成立且公開的動作，不把反應選擇或一般摸牌當作公開通知。 */
internal object GameActionEventProjector {
    /**
     * 依來源順序建立同一交易中的動作，補牌只保留張數及觸發動作 ID。
     *
     * @param facts 已提交交易。
     * @param module 提供移出手牌公開區的規則。
     * @return 已成立的公開動作。
     */
    fun project(facts: CommittedGameFacts, module: MahjongRuleModule<*>): List<PerformedAction> {
        val previous = facts.previousGame?.tableState ?: return emptyList()
        val current = facts.game?.tableState ?: return emptyList()
        val result = mutableListOf<PerformedAction>()
        val reacting = previous.pendingReaction != null || previous.pendingRobbingReaction != null
        val privateDecision = facts.facts.any {
            it.fact is HistoryFact.RuleDecisionSubmitted || it.fact is HistoryFact.RuleDecisionAutomaticallyResolved
        }
        var supplementalActor: Uuid? = null
        var supplementalSource: String? = null
        facts.facts.forEach { draft ->
            when (val fact = draft.fact) {
                is HistoryFact.ReactionResolved -> {
                    val action = fact.resolvedAction ?: return@forEach
                    if (action is GameAction.Ron) {
                        facts.facts.mapNotNull { it.fact as? HistoryFact.WinSettled }
                            .flatMap { it.winDetails }.map { it.playerId }.distinct().forEach { winner ->
                                result += basic(action, winner, PerformedActionKind.WIN)
                            }
                    } else {
                        val actor = fact.actorPlayerId ?: return@forEach
                        applied(action, actor, previous, current)?.let { result += it }
                        supplementalActor = actor
                        supplementalSource = actionId(action)
                    }
                }
                is HistoryFact.ActionAccepted -> {
                    val actor = draft.actorPlayerId ?: return@forEach
                    val action = fact.action
                    if (action == GameAction.Draw) {
                        if (actor == supplementalActor && supplementalSource != null && fact.result.affectedTileIds.isNotEmpty()) {
                            result += PerformedAction.create(
                                actionId = PublicGameActionIds.SUPPLEMENTAL_DRAW,
                                actorPlayerId = actor,
                                kind = PerformedActionKind.SUPPLEMENTAL_DRAW,
                                supplementalTileCount = fact.result.affectedTileIds.size,
                                supplementalSourceId = supplementalSource,
                            )
                        }
                        supplementalActor = null
                        supplementalSource = null
                    } else {
                        // 反應提交不是裁定；其後的自動移出步驟以實際公開區變化辨識。
                        val isFollowUp = action is GameAction.Extension &&
                            fact.result.affectedTileIds.any { id ->
                                current.players.firstOrNull { it.id == actor }?.let(module::setAsideTiles)?.any { it.id == id } == true
                            }
                        if ((!reacting || isFollowUp) && !(privateDecision && action is GameAction.Extension)) {
                            applied(action, actor, previous, current)?.let {
                                result += it
                                supplementalActor = actor
                                supplementalSource = if (action is GameAction.Kan || isFollowUp) actionId(action) else null
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
        return result
    }

    /**
     * 依提交後的公開牌區確認動作已套用，並遮住未公開的牌面。
     *
     * @param action 來源動作。
     * @param actor 執行者。
     * @param previous 提交前桌況。
     * @param current 提交後桌況。
     * @return 公開動作；未成立或不應通知時為 null。
     */
    private fun applied(action: GameAction, actor: Uuid, previous: TableState, current: TableState): PerformedAction? {
        val player = current.players.firstOrNull { it.id == actor } ?: return null
        return when (action) {
            is GameAction.Discard -> {
                val tile = player.discardPile.entries.firstOrNull { it.tile.id == action.tileId }?.tile ?: return null
                PerformedAction.create(actionId(action), actor, PerformedActionKind.DISCARD, tiles = listOf(face(tile.tile)))
            }
            is GameAction.Chi, is GameAction.Pon, is GameAction.Kan -> {
                val tileId = when (action) {
                    is GameAction.Chi -> action.tileId
                    is GameAction.Pon -> action.tileId
                    is GameAction.Kan -> action.tileId
                }
                val oldMelds = previous.players.firstOrNull { it.id == actor }?.hand?.melds.orEmpty()
                val meld = player.hand.melds.firstOrNull { candidate ->
                    candidate.tiles.any { it.id == tileId } && candidate !in oldMelds
                } ?: return null
                PerformedAction.create(
                    actionId = actionId(action),
                    actorPlayerId = actor,
                    kind = PerformedActionKind.MELD,
                    tiles = meld.tiles.map { if (it.id in meld.publiclyRevealedTileIds) face(it.tile) else null },
                )
            }
            is GameAction.Extension -> {
                if (current.pendingRobbingReaction?.let { it.declarerId == actor && it.declaredAction == action } == true) return null
                previous.pendingReaction?.let { pending ->
                    val oldMelds = previous.players.firstOrNull { it.id == actor }?.hand?.melds.orEmpty()
                    val claimed = player.hand.melds.firstOrNull { candidate ->
                        candidate.tiles.any { it.id == pending.tileId } && candidate !in oldMelds
                    }
                    if (claimed != null) {
                        return PerformedAction.create(
                            actionId = actionId(action),
                            actorPlayerId = actor,
                            kind = PerformedActionKind.MELD,
                            tiles = claimed.tiles.map { if (it.id in claimed.publiclyRevealedTileIds) face(it.tile) else null },
                        )
                    }
                }
                val oldHistory = previous.players.firstOrNull { it.id == actor }?.actionHistory.orEmpty()
                if (player.actionHistory.drop(oldHistory.size).none { it == action }) return null
                basic(action, actor, PerformedActionKind.DECLARATION)
            }
            GameAction.Tsumo -> basic(action, actor, PerformedActionKind.WIN)
            else -> null
        }
    }

    /** 建立沒有額外牌面資料的動作。 */
    private fun basic(action: GameAction, actor: Uuid, kind: PerformedActionKind): PerformedAction = PerformedAction.create(actionId(action), actor, kind)

    /** 取得動作的完整公開 ID；擴充動作保留來源 ID。 */
    private fun actionId(action: GameAction): String = when (action) {
        is GameAction.Discard -> PublicGameActionIds.DISCARD
        is GameAction.Chi -> PublicGameActionIds.CHI
        is GameAction.Pon -> PublicGameActionIds.PON
        is GameAction.Kan -> when (action.type) {
            GameAction.KanType.OPEN_KAN -> PublicGameActionIds.KAN_OPEN
            GameAction.KanType.CLOSED_KAN -> PublicGameActionIds.KAN_CLOSED
            GameAction.KanType.ADDED_KAN -> PublicGameActionIds.KAN_ADDED
        }
        is GameAction.Extension -> action.value.id
        GameAction.Tsumo -> PublicGameActionIds.TSUMO
        is GameAction.Ron -> PublicGameActionIds.RON
        else -> error("Action has no public identity")
    }

    /** 建立牌面描述，不包含可追蹤實體牌的 UUID。 */
    private fun face(tile: Tile): PublicTileFace = PublicTileFace.create(
        when (tile) {
            is Tile.Extension -> tile.typeId.toString()
            is Tile.Numeric -> "mahjongcraft:tile/numeric/${tile.suit.name.lowercase()}/${tile.value}"
            is Tile.Honor -> "mahjongcraft:tile/honor/${when (tile) {
                Tile.Honor.East -> "east"
                Tile.Honor.South -> "south"
                Tile.Honor.West -> "west"
                Tile.Honor.North -> "north"
                Tile.Honor.Red -> "red"
                Tile.Honor.Green -> "green"
                Tile.Honor.White -> "white"
            }}"
        },
    )
}
