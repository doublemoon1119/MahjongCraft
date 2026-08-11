package com.doublemoon1119.mahjongcraft.flow.server.game.policy

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatorHandVisibility
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** [GameVisibilityPolicy] 的預設實作。 */
@Single(binds = [GameVisibilityPolicy::class])
class GameVisibilityPolicyImpl : GameVisibilityPolicy {
    override fun snapshotFor(game: Game, observerId: Uuid): TableStateSnapshot {
        val playerIds = game.tableState.players.mapTo(linkedSetOf()) { it.id }
        val visibleHandPlayerIds = if (observerId in playerIds) {
            setOf(observerId)
        } else {
            val canRevealSpectatorHands =
                game.flowConfig.spectatingPolicy == SpectatingPolicy.ENABLED &&
                    game.flowConfig.spectatorHandVisibility == SpectatorHandVisibility.REVEALED
            if (canRevealSpectatorHands) playerIds else emptySet()
        }
        return game.tableState.toSnapshot(visibleHandPlayerIds)
    }
}
