package com.doublemoon1119.mahjongcraft.flow.persistence.dto.state

import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.PersistenceEnvelopeDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule.buildDiscardPilePersistenceRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule.buildDynamicRuleStatePersistenceRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule.buildExhaustiveDrawReasonPersistenceRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule.buildPlayerRuleStatePersistenceRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule.buildRuleConfigPersistenceRegistry
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證 Room 與 Game 同批保存的伺服器權威狀態。 */
class AuthoritativeStatePersistenceDtoTest {
    /** persistence 測試使用的 JSON 編解碼器。 */
    private val json = Json

    /** 內建規則配置的 persistence registry。 */
    private val ruleConfigRegistry = buildRuleConfigPersistenceRegistry()

    /** 內建牌河的 persistence registry。 */
    private val discardPileRegistry = buildDiscardPilePersistenceRegistry()

    /** 內建玩家規則狀態的 persistence registry。 */
    private val playerRuleStateRegistry = buildPlayerRuleStatePersistenceRegistry()

    /** 內建動態牌桌狀態的 persistence registry。 */
    private val dynamicRuleStateRegistry = buildDynamicRuleStatePersistenceRegistry()

    /** 內建流局原因的 persistence registry。 */
    private val exhaustiveDrawReasonRegistry = buildExhaustiveDrawReasonPersistenceRegistry()

    /** 驗證多個 Room 與 Game 能在同一 envelope 中完整編解碼。 */
    @Test
    fun `mixed rooms and games round-trip in one envelope`() {
        val rooms = listOf(createRoom(), createRoom())
        val games = listOf(createGame(), createGame())
        val expected = createState(rooms, games)

        val envelope = expected.toEnvelope(json)
        val encoded = json.encodeToString(PersistenceEnvelopeDto.serializer(), envelope)
        val decodedEnvelope = json.decodeFromString(
            PersistenceEnvelopeDto.serializer(),
            encoded,
        )
        val restored = decodedEnvelope.decodeCurrentAuthoritativeState(json)

        assertEquals(expected, restored)
        assertEquals(rooms.associateBy(Room::id), restored.toRooms(ruleConfigRegistry, json))
        assertEquals(games.map(TableState::id).toSet(), restored.toGames().keys)
    }

    /** 驗證 Room 轉成 Game 後，相同桌子 ID 只存在於 Game 集合。 */
    @Test
    fun `room to game transition leaves only game state`() {
        val tableId = Uuid.random()
        val state = createState(emptyList(), listOf(createGame(tableId)))

        assertTrue(state.rooms.isEmpty())
        assertEquals(setOf(tableId.toString()), state.games.keys)
    }

    /** 驗證輸入集合中的重複 Room ID 不會被 map 轉換靜默覆蓋。 */
    @Test
    fun `duplicate room IDs are rejected before indexing`() {
        val room = createRoom()

        assertFailsWith<IllegalArgumentException> { createState(listOf(room, room.copy()), emptyList()) }
    }

    /** 驗證輸入集合中的重複 Game ID 不會被 map 轉換靜默覆蓋。 */
    @Test
    fun `duplicate game IDs are rejected before indexing`() {
        val game = createGame()

        assertFailsWith<IllegalArgumentException> { createState(emptyList(), listOf(game, game.copy())) }
    }

    /** 驗證索引 key 與 Room DTO 內部 ID 不一致時拒絕資料。 */
    @Test
    fun `mismatched room index is rejected`() {
        val roomDto = createState(listOf(createRoom()), emptyList()).rooms.values.single()

        assertFailsWith<IllegalArgumentException> {
            AuthoritativeStatePersistenceDto(mapOf(Uuid.random().toString() to roomDto), emptyMap())
        }
    }

    /** 驗證索引 key 與 Game DTO 內部 ID 不一致時拒絕資料。 */
    @Test
    fun `mismatched game index is rejected`() {
        val gameDto = createState(emptyList(), listOf(createGame())).games.values.single()

        assertFailsWith<IllegalArgumentException> {
            AuthoritativeStatePersistenceDto(emptyMap(), mapOf(Uuid.random().toString() to gameDto))
        }
    }

    /** 驗證相同桌子 ID 不可同時存在於 Room 與 Game。 */
    @Test
    fun `same table ID cannot be both room and game`() {
        val tableId = Uuid.random()
        val roomDto = createState(listOf(createRoom(tableId)), emptyList()).rooms.getValue(tableId.toString())
        val gameDto = createState(emptyList(), listOf(createGame(tableId))).games.getValue(tableId.toString())

        assertFailsWith<IllegalArgumentException> {
            AuthoritativeStatePersistenceDto(
                rooms = mapOf(tableId.toString() to roomDto),
                games = mapOf(tableId.toString() to gameDto),
            )
        }
    }

    /** 建立使用台麻規則的等待階段 Room。 */
    private fun createRoom(id: Uuid = Uuid.random()): Room {
        val hostId = Uuid.random()
        return Room(
            id = id,
            hostId = hostId,
            config = TaiwanRuleConfig(),
            playerIds = setOf(hostId),
        )
    }

    /** 建立使用台麻規則的最小進行中 Game。 */
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

    /** 使用所有內建 registry 建立伺服器權威狀態 DTO。 */
    private fun createState(
        rooms: Collection<Room>,
        games: Collection<TableState>,
    ): AuthoritativeStatePersistenceDto = createAuthoritativeStatePersistenceDto(
        rooms,
        games,
        ruleConfigRegistry,
        discardPileRegistry,
        playerRuleStateRegistry,
        dynamicRuleStateRegistry,
        exhaustiveDrawReasonRegistry,
        json,
    )

    /** 使用所有內建 registry 還原 Game 索引。 */
    private fun AuthoritativeStatePersistenceDto.toGames(): Map<Uuid, TableState> = toGames(
        ruleConfigRegistry,
        discardPileRegistry,
        playerRuleStateRegistry,
        dynamicRuleStateRegistry,
        exhaustiveDrawReasonRegistry,
        json,
    )
}
