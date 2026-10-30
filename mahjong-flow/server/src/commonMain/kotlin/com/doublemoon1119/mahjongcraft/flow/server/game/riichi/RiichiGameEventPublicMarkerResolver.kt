package com.doublemoon1119.mahjongcraft.flow.server.game.riichi

import com.doublemoon1119.mahjongcraft.flow.api.event.MatchCompletion
import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventPublicMarkerResolver
import com.doublemoon1119.mahjongcraft.flow.common.game.event.MatchEndPublicMarkerContext
import com.doublemoon1119.mahjongcraft.flow.common.game.event.RulePublicEventMarker
import com.doublemoon1119.mahjongcraft.flow.common.game.event.RulePublicEventQuantity
import com.doublemoon1119.mahjongcraft.flow.common.game.event.WinPublicMarkerContext
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.riichiDeclaration
import com.doublemoon1119.mahjongcraft.logic.table.BuiltInMatchEndReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/** 提供日麻和牌公開狀態與終局擊飛資訊的事件標記解析器。 */
object RiichiGameEventPublicMarkerResolver : GameEventPublicMarkerResolver {
    override fun resolvePrevalentWindId(state: TableState): String = RiichiGameEventPublicMarkerIds.prevalentWind(state.prevalentWind)

    override fun resolveWinMarkers(context: WinPublicMarkerContext): List<RulePublicEventMarker> {
        val state = context.previousState.players
            .firstOrNull { it.id == context.winnerId }
            ?.riichiDeclaration
            ?: return emptyList()

        return buildList {
            add(RulePublicEventMarker(RiichiGameEventPublicMarkerIds.RIICHI, context.winnerId))
            if (state.isIppatsu) add(RulePublicEventMarker(RiichiGameEventPublicMarkerIds.IPPATSU, context.winnerId))
        }
    }

    override fun resolveMatchEndMarkers(context: MatchEndPublicMarkerContext): List<RulePublicEventMarker> {
        val threshold = context.previousState.config.scoreConfig.bustThreshold ?: return emptyList()
        return buildList {
            add(
                RulePublicEventMarker(
                    id = RiichiGameEventPublicMarkerIds.BUST_THRESHOLD,
                    quantity = RulePublicEventQuantity(RiichiGameEventPublicMarkerIds.POINTS_UNIT, threshold),
                ),
            )
            if (context.completion == MatchCompletion.COMPLETED && context.reasonId == BuiltInMatchEndReasonIds.PLAYER_BUSTED) {
                context.finalScoresByPlayerId.forEach { (playerId, score) ->
                    if (score < threshold) add(RulePublicEventMarker(RiichiGameEventPublicMarkerIds.BUSTED, playerId))
                }
            }
        }
    }
}
