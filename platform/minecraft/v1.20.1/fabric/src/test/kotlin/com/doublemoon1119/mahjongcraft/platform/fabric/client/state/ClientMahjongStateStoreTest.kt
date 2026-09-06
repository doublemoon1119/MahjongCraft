package com.doublemoon1119.mahjongcraft.platform.fabric.client.state

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.command.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.GameUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.TableLobbyPayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.TableLobbyPhaseDto
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

        val lobbyA = TableLobbyPayloadDto(tableId = tableAId.toString(), phase = TableLobbyPhaseDto.WAITING)
        store.apply(lobbyA)

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

        assertEquals(lobbyA, store.tableLobby(tableAId), "Table B's game update must not affect table A's lobby state")
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
}
