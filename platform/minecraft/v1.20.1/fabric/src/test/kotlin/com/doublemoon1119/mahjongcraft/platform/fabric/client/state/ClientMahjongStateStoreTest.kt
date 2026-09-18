package com.doublemoon1119.mahjongcraft.platform.fabric.client.state

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.command.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.GameUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomUpdateEventDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.TableOccupancyDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.TableOccupancyPayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.LeaveReasonDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.registerBuiltInRuleConfigDtos
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.registerRiichiGameActionDtos
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.toDto
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * [ClientMahjongStateStore] 是依桌子 UUID 索引的，這裡驗證的正是它存在的理由：一張桌子的推播不會
 * 覆蓋或清掉另一張桌子已經存好的資料——這正是被動旁觀（entity tracking 觸發的自動同步）讓一個
 * client 同時收到好幾張不相干桌子推播時，之前會發生的問題。
 */
class ClientMahjongStateStoreTest {

    private val registries: NetworkDtoRegistries = DefaultNetworkDtoRegistries().apply {
        registerBuiltInRuleConfigDtos()
        registerRiichiGameActionDtos()
    }

    @Test
    fun `a game update for table B does not touch table A's already-stored lobby state`() {
        val store = ClientMahjongStateStore(registries)
        val tableAId = Uuid.random()
        val tableBId = Uuid.random()

        val occupancyA = TableOccupancyPayloadDto(tableId = tableAId.toString(), occupancy = TableOccupancyDto.ROOM)
        store.apply(occupancyA)

        val playerB = FakeMahjongPlayerFactory.create(discardPile = RiichiDiscardPile())
        val snapshotB = FakeTableStateFactory.create(id = tableBId, players = listOf(playerB), config = RiichiRuleConfig())
            .toSnapshot(visibleHandPlayerIds = emptySet())
        val gameUpdateB = GameUpdatePayloadDto(
            gameId = tableBId.toString(),
            actorId = playerB.id.toString(),
            action = GameAction.Draw.toDto(registries),
            snapshot = snapshotB.toDto(registries),
        )
        store.apply(gameUpdateB)

        assertEquals(occupancyA, store.tableOccupancy(tableAId), "Table B's game update must not affect table A's lobby state")
        assertEquals(snapshotB, store.gameSnapshot(tableBId))
        assertNull(store.gameSnapshot(tableAId))
    }

    @Test
    fun `findTableWhereSeated only matches the table the local player is actually a member of`() {
        val store = ClientMahjongStateStore(registries)
        val localPlayerId = Uuid.random()
        val seatedTableId = Uuid.random()
        val spectatedTableId = Uuid.random()

        store.applyRoomSnapshot(
            seatedTableId,
            RoomSnapshot(
                id = seatedTableId,
                hostId = localPlayerId,
                gameConfig = GameConfig(RiichiRuleConfig()),
                playerIds = listOf(localPlayerId),
                readyPlayerIds = emptyList(),
                aiPlayerIds = emptyList(),
                canStart = false,
                isHost = true,
                isInRoom = true,
            ),
        )
        val otherPlayer = FakeMahjongPlayerFactory.create()
        store.applyGameSnapshot(
            spectatedTableId,
            FakeTableStateFactory.create(id = spectatedTableId, players = listOf(otherPlayer), config = RiichiRuleConfig())
                .toSnapshot(visibleHandPlayerIds = emptySet()),
        )

        assertEquals(seatedTableId, store.findTableWhereSeated(localPlayerId))
    }

    /**
     * 解散事件附帶的是解散前的快照。保存它會讓畫面退回解散前的內容，因此這個事件必須清除已存的房間
     * 資料並把大廳狀態標為空桌。
     */
    @Test
    fun `a dissolved leave event clears the stored room instead of storing its stale snapshot`() {
        val store = ClientMahjongStateStore(registries)
        val tableId = Uuid.random()
        val hostId = Uuid.random()
        val memberId = Uuid.random()
        val snapshot = RoomSnapshot(
            id = tableId,
            hostId = hostId,
            gameConfig = GameConfig(RiichiRuleConfig()),
            playerIds = listOf(hostId, memberId),
            readyPlayerIds = emptyList(),
            aiPlayerIds = emptyList(),
            canStart = false,
            isHost = false,
            isInRoom = true,
        )
        store.apply(TableOccupancyPayloadDto(tableId.toString(), TableOccupancyDto.ROOM, snapshot.toDto(registries)))
        store.applyRoomSnapshot(tableId, snapshot)

        store.apply(
            RoomUpdatePayloadDto(
                roomId = tableId.toString(),
                event = RoomUpdateEventDto.Leave(memberId.toString(), LeaveReasonDto.Dissolved),
                snapshot = snapshot.toDto(registries),
            ),
        )

        assertNull(store.roomSnapshot(tableId))
        assertEquals(TableOccupancyDto.VACANT, store.tableOccupancy(tableId)?.occupancy)
    }
}
