package com.doublemoon1119.mahjongcraft.flow.server.game.policy

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatorHandVisibility
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** [GameVisibilityPolicyImpl] 的觀看權限與手牌可見範圍測試。 */
class GameVisibilityPolicyTest {
    /** 參與玩家只能看見自己的手牌，不受旁觀者設定影響。 */
    @Test
    fun `player sees only own hand`() {
        val playerId = Uuid.random()
        val otherPlayerId = Uuid.random()
        val game = createGame(playerId, otherPlayerId)

        val snapshot = GameVisibilityPolicyImpl().snapshotFor(game, playerId)

        assertNotNull(snapshot.players.single { it.id == playerId }.hand.standingTiles.single().tile)
        assertNull(snapshot.players.single { it.id == otherPlayerId }.hand.standingTiles.single().tile)
    }

    /** 公開旁觀手牌時，外部觀看者可以看見所有玩家手牌。 */
    @Test
    fun `spectator sees all hands when configured as revealed`() {
        val playerIds = listOf(Uuid.random(), Uuid.random())
        val game = createGame(*playerIds.toTypedArray())

        assertEquals(
            playerIds.toSet(),
            GameVisibilityPolicyImpl().snapshotFor(game, Uuid.random()).players
                .filter { it.hand.standingTiles.single().tile != null }
                .mapTo(mutableSetOf()) { it.id },
        )
    }

    /** 隱藏旁觀手牌時，外部觀看者仍可取得只含公開資料的快照。 */
    @Test
    fun `spectator sees no hands when configured as hidden`() {
        val game = createGame(
            Uuid.random(),
            flowConfig = GameFlowConfig(spectatorHandVisibility = SpectatorHandVisibility.HIDDEN),
        )

        assertEquals(
            emptyList(),
            GameVisibilityPolicyImpl().snapshotFor(game, Uuid.random()).players
                .filter { it.hand.standingTiles.single().tile != null },
        )
    }

    /** 禁止旁觀時，外部讀取者仍可取得隱藏所有手牌的公開快照。 */
    @Test
    fun `external observer sees no hands when spectating is disabled`() {
        val game = createGame(
            Uuid.random(),
            flowConfig = GameFlowConfig(spectatingPolicy = SpectatingPolicy.DISABLED),
        )

        assertEquals(
            emptyList(),
            GameVisibilityPolicyImpl().snapshotFor(game, Uuid.random()).players
                .filter { it.hand.standingTiles.single().tile != null },
        )
    }

    /** 建立指定玩家與流程設定的測試遊戲。 */
    private fun createGame(
        vararg playerIds: Uuid,
        flowConfig: GameFlowConfig = GameFlowConfig(),
    ): Game = Game(
        tableState = FakeTableStateFactory.create(
            players = playerIds.map {
                FakeMahjongPlayerFactory.create(
                    id = it,
                    hand = FakeHandFactory.create(listOf(Tile.Honor.East)),
                )
            },
        ),
        flowConfig = flowConfig,
    )
}
