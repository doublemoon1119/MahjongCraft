package com.doublemoon1119.mahjongcraft.flow.server.lifecycle

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** [ServerSessionStateRestorer] 的 membership 與 observer snapshot 恢復測試。 */
class ServerSessionStateRestorerTest {
    /** 已載入的 Room／Game 應完整重建玩家歸屬與各自視角快照。 */
    @Test
    fun `test restore rebuilds memberships and observer snapshots`() = runTest {
        val roomSnapshots = RoomSnapshotRepositoryImpl()
        val gameSnapshots = GameSnapshotRepositoryImpl()
        val memberships = PlayerMembershipRepositoryImpl()
        val restorer = ServerSessionStateRestorer(
            roomSnapshots,
            gameSnapshots,
            memberships,
            GameVisibilityPolicyImpl(),
        )
        val roomPlayerId = Uuid.random()
        val gamePlayerId = Uuid.random()
        val otherGamePlayerId = Uuid.random()
        val room = Room(
            id = Uuid.random(),
            hostId = roomPlayerId,
            gameConfig = GameConfig(FakeMahjongRuleConfig()),
            playerIds = setOf(roomPlayerId),
        )
        val game = FakeTableStateFactory.create(
            players = listOf(gamePlayerId, otherGamePlayerId).map { playerId ->
                FakeMahjongPlayerFactory.create(
                    id = playerId,
                    hand = FakeHandFactory.create(listOf(Tile.Honor.East)),
                )
            },
        )

        restorer.restore(
            AuthoritativeStateSnapshot(
                rooms = mapOf(room.id to room),
                games = mapOf(game.id to Game(game, GameFlowConfig())),
            ),
        )

        assertEquals(room.id, memberships.getTableId(roomPlayerId))
        assertEquals(game.id, memberships.getTableId(gamePlayerId))
        assertEquals(game.id, memberships.getTableId(otherGamePlayerId))
        assertNotNull(roomSnapshots.getSnapshot(room.id, roomPlayerId))
        val gamePlayerSnapshot = assertNotNull(gameSnapshots.getSnapshot(game.id, gamePlayerId))
        assertNotNull(gamePlayerSnapshot.players.single { it.id == gamePlayerId }.hand.standingTiles.single().tile)
        assertNull(gamePlayerSnapshot.players.single { it.id == otherGamePlayerId }.hand.standingTiles.single().tile)
    }

    /** 同一玩家跨桌重複時應回報衝突、略過 membership，並保留各桌 observer snapshot。 */
    @Test
    fun `test restore reports conflicting membership without discarding saved tables`() = runTest {
        val roomSnapshots = RoomSnapshotRepositoryImpl()
        val gameSnapshots = GameSnapshotRepositoryImpl()
        val memberships = PlayerMembershipRepositoryImpl()
        val restorer = ServerSessionStateRestorer(
            roomSnapshots,
            gameSnapshots,
            memberships,
            GameVisibilityPolicyImpl(),
        )
        val playerId = Uuid.random()
        val existingTableId = Uuid.random()
        memberships.claim(playerId, existingTableId)
        val firstRoom = Room(Uuid.random(), playerId, GameConfig(FakeMahjongRuleConfig()), setOf(playerId))
        val secondRoom = Room(Uuid.random(), playerId, GameConfig(FakeMahjongRuleConfig()), setOf(playerId))

        val result = restorer.restore(
            AuthoritativeStateSnapshot(
                rooms = mapOf(firstRoom.id to firstRoom, secondRoom.id to secondRoom),
            ),
        )

        assertEquals(
            listOf(PlayerMembershipConflict(playerId, setOf(firstRoom.id, secondRoom.id))),
            result.membershipConflicts,
        )
        assertNull(memberships.getTableId(playerId))
        assertNotNull(roomSnapshots.getSnapshot(firstRoom.id, playerId))
        assertNotNull(roomSnapshots.getSnapshot(secondRoom.id, playerId))
    }
}
