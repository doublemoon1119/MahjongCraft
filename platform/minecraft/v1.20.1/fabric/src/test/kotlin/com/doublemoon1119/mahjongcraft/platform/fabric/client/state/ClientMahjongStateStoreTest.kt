package com.doublemoon1119.mahjongcraft.platform.fabric.client.state

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.command.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.GameUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HandReadinessAnalysisDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomUpdateEventDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.LeaveReasonDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.toDto
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.TableOccupancyDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.TableOccupancyPayloadDto
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledNetworkDtos
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
        registerBundledNetworkDtos()
    }

    @Test
    fun `a game update for table B does not touch table A's already-stored lobby state`() {
        val store = ClientMahjongStateStore(registries)
        val tableAId = Uuid.random()
        val tableBId = Uuid.random()

        val occupancyA = TableOccupancyPayloadDto(tableId = tableAId.toString(), occupancy = TableOccupancyDto.ROOM, roomSnapshot = null, playingPlayerIds = emptyList(), playingAiPlayerIds = emptyList(), playingGameConfig = null, dimensionId = null, tableX = null, tableY = null, tableZ = null)
        store.apply(occupancyA)

        val playerB = FakeMahjongPlayerFactory.create(discardPile = RiichiDiscardPile())
        val snapshotB = FakeTableStateFactory.create(id = tableBId, players = listOf(playerB), config = RiichiRuleConfig())
            .toSnapshot(visibleHandPlayerIds = emptySet(), setAsideTiles = { emptyList() })
        val gameUpdateB = GameUpdatePayloadDto(
            gameId = tableBId.toString(),
            actorId = playerB.id.toString(),
            action = GameAction.Draw.toDto(registries),
            snapshot = snapshotB.toDto(registries),
            aiPlayerIds = emptyList(),
            historyMatchId = null,
        )
        store.apply(gameUpdateB)

        assertEquals(occupancyA, store.tableOccupancy(tableAId), "Table B's game update must not affect table A's lobby state")
        assertEquals(snapshotB, store.gameSnapshot(tableBId))
        assertNull(store.gameSnapshot(tableAId))
    }

    /** 遊戲更新附帶的 AI 玩家清單會跟著快照一起保存，下一次更新會整份取代。 */
    @Test
    fun `game updates store and replace the ai player list alongside the snapshot`() {
        val store = ClientMahjongStateStore(registries)
        val tableId = Uuid.random()
        val human = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST, discardPile = RiichiDiscardPile())
        val ai = FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH, discardPile = RiichiDiscardPile())
        val snapshot = FakeTableStateFactory.create(id = tableId, players = listOf(human, ai), config = RiichiRuleConfig())
            .toSnapshot(visibleHandPlayerIds = emptySet(), setAsideTiles = { emptyList() })

        store.apply(
            GameUpdatePayloadDto(
                gameId = tableId.toString(),
                actorId = human.id.toString(),
                action = GameAction.Draw.toDto(registries),
                snapshot = snapshot.toDto(registries),
                aiPlayerIds = listOf(ai.id.toString()),
                historyMatchId = null,
            ),
        )
        assertEquals(setOf(ai.id), store.gameAiPlayerIds(tableId))

        store.applyGameSnapshot(tableId, snapshot, aiPlayerIds = emptySet())
        assertEquals(emptySet(), store.gameAiPlayerIds(tableId))
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
            gameId = spectatedTableId,
            snapshot = FakeTableStateFactory.create(id = spectatedTableId, players = listOf(otherPlayer), config = RiichiRuleConfig())
                .toSnapshot(visibleHandPlayerIds = emptySet(), setAsideTiles = { emptyList() }),
            aiPlayerIds = emptySet(),
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
        store.apply(TableOccupancyPayloadDto(tableId.toString(), TableOccupancyDto.ROOM, snapshot.toDto(registries), playingPlayerIds = emptyList(), playingAiPlayerIds = emptyList(), playingGameConfig = null, dimensionId = null, tableX = null, tableY = null, tableZ = null))
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

    @Test
    fun `private hand analysis follows authoritative game snapshots and clears on newer action`() {
        val store = ClientMahjongStateStore(registries)
        val tableId = Uuid.random()
        val player = FakeMahjongPlayerFactory.create(discardPile = RiichiDiscardPile())
        val snapshot = FakeTableStateFactory.create(id = tableId, players = listOf(player), config = RiichiRuleConfig())
            .toSnapshot(visibleHandPlayerIds = setOf(player.id), setAsideTiles = { emptyList() })
        val analysis = HandReadinessAnalysisDto("mahjongcraft:riichi", emptyList(), statusIndicatorId = null)
        store.applyGameSnapshot(tableId, snapshot, aiPlayerIds = emptySet(), handReadinessAnalysis = analysis)

        assertEquals(analysis, store.handReadinessAnalysis(tableId))

        store.apply(
            GameUpdatePayloadDto(
                gameId = tableId.toString(),
                actorId = player.id.toString(),
                action = GameAction.Draw.toDto(registries),
                snapshot = snapshot.toDto(registries),
                aiPlayerIds = emptyList(),
                historyMatchId = null,
            ),
        )

        assertNull(store.handReadinessAnalysis(tableId))
    }

    /** 移出手牌、公開擺在桌上的牌也查得到牌面，不會畫成未知牌。 */
    @Test
    fun `managed tile index resolves set aside tiles`() {
        val store = ClientMahjongStateStore(registries)
        val tableId = Uuid.random()
        val north = IdentifiedTile(Uuid.random(), Tile.Honor.North)
        val player = FakeMahjongPlayerFactory.create(discardPile = RiichiDiscardPile())
        val snapshot = FakeTableStateFactory.create(id = tableId, players = listOf(player), config = RiichiRuleConfig())
            .toSnapshot(visibleHandPlayerIds = emptySet(), setAsideTiles = { listOf(north) })

        store.applyGameSnapshot(tableId, snapshot, aiPlayerIds = emptySet(), handReadinessAnalysis = null)

        assertEquals(Tile.Honor.North, store.findManagedTileSnapshot(tableId, north.id)?.tile)
    }

    /** 已公開但仍在手牌中的牌對其他觀察者是隱藏的手牌，查表時以公開牌面為準。 */
    @Test
    fun `managed tile index reveals revealed hand tiles`() {
        val store = ClientMahjongStateStore(registries)
        val tableId = Uuid.random()
        val north = IdentifiedTile(Uuid.random(), Tile.Honor.North)
        val puller = FakeMahjongPlayerFactory.create(hand = Hand(lastDrawn = north), discardPile = RiichiDiscardPile())
        val observer = FakeMahjongPlayerFactory.create(discardPile = RiichiDiscardPile())
        val snapshot = FakeTableStateFactory.create(id = tableId, players = listOf(puller, observer), config = RiichiRuleConfig())
            .copy(revealedHandTileIds = setOf(north.id))
            .toSnapshot(visibleHandPlayerIds = setOf(observer.id), setAsideTiles = { emptyList() })

        store.applyGameSnapshot(tableId, snapshot, aiPlayerIds = emptySet(), handReadinessAnalysis = null)

        assertEquals(Tile.Honor.North, store.findManagedTileSnapshot(tableId, north.id)?.tile)
    }
}
