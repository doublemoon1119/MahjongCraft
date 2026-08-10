package com.doublemoon1119.mahjongcraft.flow.dto

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.room.model.JoinReason
import com.doublemoon1119.mahjongcraft.flow.common.room.model.LeaveReason
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.config.GameLength
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.config.ScoreConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoLiability
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoYaku
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardEntry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/**
 * 驗證 `:mahjong-flow-dto` 的 DTO 來回（領域物件 → DTO → JSON → 解碼 → DTO → 領域物件）不失真。
 *
 * 刻意全部使用真正的 [RiichiRuleConfig]/[TaiwanRuleConfig] 等規則實作建構測試資料，不使用
 * `testing/mahjong-logic` 裡的 `FakeMahjongRuleConfig`/`FakeGameLength`/`FakeScoreConfig`/
 * `FakeDiscardPile`——那些是測試專用型別，本來就不會（也不該）註冊進正式的
 * [MahjongRuleDtoRegistries]。
 */
class DtoRoundTripTest {

    private lateinit var json: Json

    @BeforeTest
    fun setUp() {
        registerBuiltInRuleConfigDtos()
        json = Json { serializersModule = buildMahjongDtoSerializersModule() }
    }

    @Test
    fun `test GameCommand round-trips through every variant`() {
        val tileId = Uuid.random()
        val commands = listOf(
            GameCommand.Draw,
            GameCommand.Discard(tileId),
            GameCommand.Riichi(tileId),
            GameCommand.Tsumo,
            GameCommand.Kan(GameAction.KanType.CLOSED_KAN, tileId),
            GameCommand.RespondToDiscard(GameAction.Ron(tileId)),
            GameCommand.RespondToChankan(GameAction.Pass),
            GameCommand.KyuushuKyuuhai,
        )

        commands.forEach { command ->
            val encoded = json.encodeToString(GameCommandDto.serializer(), command.toDto())
            val decoded = json.decodeFromString(GameCommandDto.serializer(), encoded).toDomain()
            assertEquals(command, decoded, "GameCommand round-trip failed for $command")
        }
    }

    @Test
    fun `test GameAction round-trips through every variant including each RiichiExhaustiveDrawReason leaf`() {
        val tileId = Uuid.random()
        val withTiles = listOf(Uuid.random(), Uuid.random())
        val actions = listOf(
            GameAction.GameStarted,
            GameAction.RoundStarted,
            GameAction.Draw,
            GameAction.Discard(tileId),
            GameAction.Chi(tileId, withTiles),
            GameAction.Pon(tileId),
            GameAction.Kan(GameAction.KanType.OPEN_KAN, tileId, withTiles),
            GameAction.Ron(tileId),
            GameAction.Tsumo,
            GameAction.Riichi,
            GameAction.Pass,
            GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.Normal),
            GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.KyuushuKyuuhai),
            GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.SuufonRenda),
            GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.SuukanNagare),
            GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.SuuchaRiichi),
            GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.SanchaHou),
        )

        actions.forEach { action ->
            val encoded = json.encodeToString(GameActionDto.serializer(), action.toDto())
            val decoded = json.decodeFromString(GameActionDto.serializer(), encoded).toDomain()
            assertEquals(action, decoded, "GameAction round-trip failed for $action")
        }
    }

    @Test
    fun `test TableStateSnapshot round-trips with a real RiichiRuleConfig`() {
        val riichiPlayer = FakeMahjongPlayerFactory.create(
            hand = Hand(tiles = listOf(IdentifiedTile(Uuid.random(), Tile.Numeric(Tile.Suit.Character, 5, isRed = true)))),
            discardPile = RiichiDiscardPile(
                listOf(RiichiDiscardEntry(IdentifiedTile(Uuid.random(), Tile.Honor.East), isRiichi = true)),
            ),
            playerRuleState = RiichiPlayerState(
                riichiTile = IdentifiedTile(Uuid.random(), Tile.Honor.East),
                isIppatsu = true,
                paoLiability = PaoLiability(PaoYaku.Daisuushii, RelativeDirection.Across),
            ),
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(riichiPlayer),
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(riichiStickCount = 2),
        )
        val snapshot = tableState.toSnapshot(riichiPlayer.id)
        val snapshotDto = snapshot.toDto()

        val encoded = json.encodeToString(TableStateSnapshotDto.serializer(), snapshotDto)
        val decodedDto = json.decodeFromString(TableStateSnapshotDto.serializer(), encoded)
        // DiscardPile.DiscardEntry/RiichiDiscardEntry 沒有 equals()，改比對來回前後的 DTO
        // （DTO 都是 data class，有結構化相等），不比對還原後的領域物件。
        assertEquals(snapshotDto, decodedDto)
    }

    @Test
    fun `test TableStateSnapshot round-trips with a real TaiwanRuleConfig`() {
        val taiwanPlayer = FakeMahjongPlayerFactory.create(
            discardPile = TaiwanDiscardPile(
                listOf(DiscardPile.DiscardEntry(IdentifiedTile(Uuid.random(), Tile.Numeric(Tile.Suit.Dot, 3)))),
            ),
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(taiwanPlayer),
            config = TaiwanRuleConfig(),
        )
        val snapshot = tableState.toSnapshot(taiwanPlayer.id)
        val snapshotDto = snapshot.toDto()

        val encoded = json.encodeToString(TableStateSnapshotDto.serializer(), snapshotDto)
        val decodedDto = json.decodeFromString(TableStateSnapshotDto.serializer(), encoded)
        assertEquals(snapshotDto, decodedDto)
    }

    @Test
    fun `test RoomSnapshot round-trips`() {
        val hostId = Uuid.random()
        val snapshot = RoomSnapshot(
            id = Uuid.random(),
            hostId = hostId,
            config = RiichiRuleConfig(),
            playerIds = setOf(hostId, Uuid.random()),
            readyPlayerIds = setOf(hostId),
            aiPlayerIds = emptySet(),
            canStart = false,
            isHost = true,
            isInRoom = true,
        )

        val encoded = json.encodeToString(RoomSnapshotDto.serializer(), snapshot.toDto())
        val decoded = json.decodeFromString(RoomSnapshotDto.serializer(), encoded).toDomain()
        assertEquals(snapshot, decoded)
    }

    @Test
    fun `test JoinReason and LeaveReason round-trip`() {
        listOf(JoinReason.Created, JoinReason.Joined).forEach { reason ->
            val encoded = json.encodeToString(JoinReasonDto.serializer(), reason.toDto())
            assertEquals(reason, json.decodeFromString(JoinReasonDto.serializer(), encoded).toDomain())
        }
        listOf(LeaveReason.Voluntary, LeaveReason.Dissolved, LeaveReason.Kicked).forEach { reason ->
            val encoded = json.encodeToString(LeaveReasonDto.serializer(), reason.toDto())
            assertEquals(reason, json.decodeFromString(LeaveReasonDto.serializer(), encoded).toDomain())
        }
    }

    @Test
    fun `test GameUpdatePayload and RoomUpdatePayload round-trip`() {
        val player = FakeMahjongPlayerFactory.create(discardPile = RiichiDiscardPile())
        val snapshot = FakeTableStateFactory.create(players = listOf(player), config = RiichiRuleConfig())
            .toSnapshot(player.id)
        val gamePayload = GameUpdatePayloadDto(
            gameId = Uuid.random().toString(),
            actorId = player.id.toString(),
            action = GameAction.Draw.toDto(),
            snapshot = snapshot.toDto(),
        )
        val encodedGame = json.encodeToString(GameUpdatePayloadDto.serializer(), gamePayload)
        assertEquals(gamePayload, json.decodeFromString(GameUpdatePayloadDto.serializer(), encodedGame))

        val roomSnapshot = RoomSnapshot(
            id = Uuid.random(),
            hostId = player.id,
            config = RiichiRuleConfig(),
            playerIds = setOf(player.id),
            readyPlayerIds = emptySet(),
            aiPlayerIds = emptySet(),
            canStart = false,
            isHost = true,
            isInRoom = true,
        )
        val roomPayload = RoomUpdatePayloadDto(
            roomId = Uuid.random().toString(),
            event = RoomUpdateEventDto.Join(player.id.toString(), JoinReason.Created.toDto()),
            snapshot = roomSnapshot.toDto(),
        )
        val encodedRoom = json.encodeToString(RoomUpdatePayloadDto.serializer(), roomPayload)
        assertEquals(roomPayload, json.decodeFromString(RoomUpdatePayloadDto.serializer(), encodedRoom))
    }

    @Test
    fun `test explicit room and game snapshot sync payloads round-trip`() {
        val player = FakeMahjongPlayerFactory.create(discardPile = RiichiDiscardPile())
        val gameSnapshot = FakeTableStateFactory.create(players = listOf(player), config = RiichiRuleConfig())
            .toSnapshot(player.id)
        val gamePayload = GameSnapshotSyncPayloadDto(
            gameId = gameSnapshot.id.toString(),
            snapshot = gameSnapshot.toDto(),
        )
        val encodedGame = json.encodeToString(GameSnapshotSyncPayloadDto.serializer(), gamePayload)
        assertEquals(gamePayload, json.decodeFromString(GameSnapshotSyncPayloadDto.serializer(), encodedGame))

        val roomSnapshot = RoomSnapshot(
            id = Uuid.random(),
            hostId = player.id,
            config = RiichiRuleConfig(),
            playerIds = setOf(player.id),
            readyPlayerIds = emptySet(),
            aiPlayerIds = emptySet(),
            canStart = false,
            isHost = true,
            isInRoom = true,
        )
        val roomPayload = RoomSnapshotSyncPayloadDto(
            roomId = roomSnapshot.id.toString(),
            snapshot = roomSnapshot.toDto(),
        )
        val encodedRoom = json.encodeToString(RoomSnapshotSyncPayloadDto.serializer(), roomPayload)
        assertEquals(roomPayload, json.decodeFromString(RoomSnapshotSyncPayloadDto.serializer(), encodedRoom))
    }

    @Test
    fun `test a third-party MahjongRuleConfig can register itself and round-trip without touching this module`() {
        MahjongRuleDtoRegistries.ruleConfig.register(
            ThirdPartyRuleConfig::class,
            ThirdPartyRuleConfigDto::class,
            ThirdPartyRuleConfigDto.serializer(),
            { it.toDto() },
            { it.toDomain() },
        )

        val thirdPartyJson = Json { serializersModule = buildMahjongDtoSerializersModule() }
        val config: MahjongRuleConfig = ThirdPartyRuleConfig()
        val configDto = config.toDto()

        // ThirdPartyRuleConfig.scoreConfig/gameLength 預設為匿名物件（沒有 equals()），改比對
        // 來回前後的 DTO，不比對還原後的領域物件。
        val encoded = thirdPartyJson.encodeToString<MahjongRuleConfigDto>(configDto)
        val decodedDto = thirdPartyJson.decodeFromString<MahjongRuleConfigDto>(encoded)
        assertEquals(configDto, decodedDto)
    }
}

/**
 * 假裝是第三方規則模組的最小 [MahjongRuleConfig] 實作，只用於驗證 [MahjongRuleDtoRegistries] 真的
 * 對外開放，不是名義上開放、實際上寫死日麻/台麻兩種。
 */
private data class ThirdPartyRuleConfig(
    override val initialHandSize: Int = 13,
    override val deadTileCount: Int = 14,
    override val scoreConfig: ScoreConfig = object : ScoreConfig {
        override val initialScore: Int = 1000
        override val bustThreshold: Int? = null
    },
    override val gameLength: GameLength = object : GameLength {
        override val totalRounds: Int = 1
    },
    override val minimumWinConstraint: Int = 0,
    override val isSpectateAllowed: Boolean = true,
    override val minPlayers: Int = 2,
    override val maxPlayers: Int = 2,
    override val multiRonPolicy: MultiRonPolicy = MultiRonPolicy(RonResolution.ALL_WINNERS, RonResolution.ALL_WINNERS),
) : MahjongRuleConfig

@Serializable
private data class ThirdPartyRuleConfigDto(
    val initialHandSize: Int,
    val deadTileCount: Int,
    val minimumWinConstraint: Int,
    val isSpectateAllowed: Boolean,
    val minPlayers: Int,
    val maxPlayers: Int,
) : MahjongRuleConfigDto

private fun ThirdPartyRuleConfig.toDto() = ThirdPartyRuleConfigDto(
    initialHandSize = initialHandSize,
    deadTileCount = deadTileCount,
    minimumWinConstraint = minimumWinConstraint,
    isSpectateAllowed = isSpectateAllowed,
    minPlayers = minPlayers,
    maxPlayers = maxPlayers,
)

private fun ThirdPartyRuleConfigDto.toDomain() = ThirdPartyRuleConfig(
    initialHandSize = initialHandSize,
    deadTileCount = deadTileCount,
    minimumWinConstraint = minimumWinConstraint,
    isSpectateAllowed = isSpectateAllowed,
    minPlayers = minPlayers,
    maxPlayers = maxPlayers,
)
