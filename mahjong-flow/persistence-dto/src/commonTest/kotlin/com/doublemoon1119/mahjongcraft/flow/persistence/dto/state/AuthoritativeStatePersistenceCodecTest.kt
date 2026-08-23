package com.doublemoon1119.mahjongcraft.flow.persistence.dto.state

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingGameTransition
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.PersistenceDtoRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.PersistenceEnvelopeDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.migration.InvalidPersistenceSchemaVersionException
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.migration.UnsupportedPersistenceSchemaVersionException
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.buildBuiltInPersistenceRegistries
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證平台無關的伺服器權威狀態 persistence codec。 */
class AuthoritativeStatePersistenceCodecTest {
    /** persistence 測試使用的 JSON 編解碼器。 */
    private val json = Json

    /** 使用所有內建 mapper 的待測 codec。 */
    private val codec = AuthoritativeStatePersistenceCodec(buildBuiltInPersistenceRegistries(), json = json)

    /** 驗證空狀態能以版本化 envelope 完整 round-trip。 */
    @Test
    fun `empty state round-trips through versioned JSON`() {
        val decoded = codec.decode(codec.encode(emptyList(), emptyList()))

        assertTrue(decoded.rooms.isEmpty())
        assertTrue(decoded.games.isEmpty())
    }

    /** 驗證混合 Room 與 Game 的狀態能完整 round-trip。 */
    @Test
    fun `mixed rooms and games round-trip through codec`() {
        val rooms = listOf(createRoom(), createRoom())
        val games = listOf(Game(createGame(), GameFlowConfig()), Game(createGame(), GameFlowConfig())).mapIndexed { index, game ->
            game.copy(
                remainingReserveMillisByPlayerId = game.tableState.players.associate { player ->
                    player.id to 12_345L
                },
                pendingTransition = if (index == 0) {
                    PendingGameTransition.AdvanceRound
                } else {
                    PendingGameTransition.ReturnToRoom
                },
            )
        }

        val decoded = codec.decode(codec.encode(rooms, games))

        assertEquals(rooms.associateBy(Room::id), decoded.rooms)
        assertEquals(games.associateBy(Game::id), decoded.games)
    }

    /** 驗證 Room → Game 後的 payload 只包含同 ID Game。 */
    @Test
    fun `room to game transition encodes only game state`() {
        val tableId = Uuid.random()
        val game = Game(createGame(tableId), GameFlowConfig())

        val decoded = codec.decode(codec.encode(emptyList(), listOf(game)))

        assertTrue(decoded.rooms.isEmpty())
        assertEquals(mapOf(tableId to game), decoded.games)
    }

    /** 驗證損壞 JSON 由 codec 在進入 adapter 狀態載入前拒絕。 */
    @Test
    fun `malformed JSON is rejected`() {
        assertFailsWith<SerializationException> { codec.decode("{not-json") }
    }

    /** 驗證無效 schema version 會經 migration registry 拒絕。 */
    @Test
    fun `invalid schema version is rejected by migration registry`() {
        val envelope = json.decodeFromString(
            PersistenceEnvelopeDto.serializer(),
            codec.encode(emptyList(), emptyList()),
        ).copy(schemaVersion = 0)

        assertFailsWith<InvalidPersistenceSchemaVersionException> {
            codec.decode(json.encodeToString(PersistenceEnvelopeDto.serializer(), envelope))
        }
    }

    /** 驗證較新的未知 schema version 會經 migration registry 拒絕。 */
    @Test
    fun `newer schema version is rejected by migration registry`() {
        val envelope = json.decodeFromString(
            PersistenceEnvelopeDto.serializer(),
            codec.encode(emptyList(), emptyList()),
        ).copy(schemaVersion = Int.MAX_VALUE)

        assertFailsWith<UnsupportedPersistenceSchemaVersionException> {
            codec.decode(json.encodeToString(PersistenceEnvelopeDto.serializer(), envelope))
        }
    }

    /** 驗證缺少規則 mapper 時不會產生無法恢復的存檔。 */
    @Test
    fun `unregistered persistence type is rejected`() {
        val registries = buildBuiltInPersistenceRegistries().copy(
            ruleConfigs = PersistenceDtoRegistry<MahjongRuleConfig>(),
        )
        val codecWithoutRules = AuthoritativeStatePersistenceCodec(registries)

        assertFailsWith<IllegalStateException> { codecWithoutRules.encode(listOf(createRoom()), emptyList()) }
    }

    /** 建立使用台麻規則的等待階段 Room。 */
    private fun createRoom(id: Uuid = Uuid.random()): Room {
        val hostId = Uuid.random()
        return Room(
            id = id,
            hostId = hostId,
            gameConfig = GameConfig(TaiwanRuleConfig()),
            playerIds = listOf(hostId),
        )
    }

    /** 建立使用台麻規則的最小 Game。 */
    private fun createGame(id: Uuid = Uuid.random()): TableState = TableState(
        id = id,
        players = listOf(
            MahjongPlayer(
                id = Uuid.random(),
                initialSeat = Wind.EAST,
                discardPile = TaiwanDiscardPile(),
            ),
        ),
        config = TaiwanRuleConfig(),
        tileWall = TileWall(emptyList()),
    )
}
