package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomAdmissionState
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomParticipantIdentity
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/** 真人與外部角色共用回房順序，來源與排除資料保持同一個房間生命週期。 */
class GameRoomRestorationTest {
    /** 局間換位不改寫等待房間的原名冊順序，也不丟失角色來源。 */
    @Test
    fun `external participants restore the same lobby order as other members`() {
        val table = FakeTableStateFactory.create()
        val original = table.players.map { it.id }
        val finalPlayers = table.players.drop(1) + table.players.first()
        val actor = original[1]
        val admission = RoomAdmissionState(
            memberIdentities = mapOf(actor to RoomParticipantIdentity("example:actors", Uuid.random())),
        )
        val game = Game(
            tableState = table.copy(players = finalPlayers),
            flowConfig = GameFlowConfig(),
            hostId = original.first(),
            roomPlayerIds = original,
            aiPlayerStrategyKeys = mapOf(actor to "example:strategy"),
            roomAdmission = admission,
        )
        val room = GameRoomRestoration.roomFor(game)
        assertEquals(original, room.playerIds)
        assertEquals(admission, room.admission)
        assertEquals(original.first(), room.hostId)
        assertEquals(original, GameRoomRestoration.roomFor(game.copy(roomAdmission = RoomAdmissionState())).playerIds)
        assertFailsWith<IllegalArgumentException> {
            game.copy(tableState = game.tableState.copy(players = table.players.take(1)))
        }
    }
}
