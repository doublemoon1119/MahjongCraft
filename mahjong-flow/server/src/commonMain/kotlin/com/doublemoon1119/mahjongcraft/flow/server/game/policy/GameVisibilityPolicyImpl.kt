package com.doublemoon1119.mahjongcraft.flow.server.game.policy

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatorHandVisibility
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** [GameVisibilityPolicy] 的預設實作。 */
@Single(binds = [GameVisibilityPolicy::class])
class GameVisibilityPolicyImpl : GameVisibilityPolicy {
    override fun resolveVisibleHandPlayerIds(game: Game, observerId: Uuid): Set<Uuid> {
        val playerIds = game.tableState.players.mapTo(linkedSetOf()) { it.id }
        if (observerId in playerIds) return setOf(observerId)

        val canRevealSpectatorHands =
            game.flowConfig.spectatingPolicy == SpectatingPolicy.ENABLED &&
                game.flowConfig.spectatorHandVisibility == SpectatorHandVisibility.REVEALED
        return if (canRevealSpectatorHands) playerIds else emptySet()
    }
}
