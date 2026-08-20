package com.doublemoon1119.mahjongcraft.flow.persistence.dto.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.TypedPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.room.RoomPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule.buildRuleConfigPersistenceRegistry
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiScoreConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanScoreConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/** 驗證 Room 權威狀態與 persistence DTO 的 encoded round-trip。 */
class RoomPersistenceDtoTest {
    /** persistence 測試使用的 JSON 編解碼器。 */
    private val json = Json

    /** 驗證自訂日麻規則、成員、準備狀態與 AI strategy key 完整恢復。 */
    @Test
    fun `riichi room round-trips through encoded persistence DTO`() {
        val config = RiichiRuleConfig(
            redDoraCount = 4,
            allowOpenTanyao = false,
            useLocalYaku = true,
            minimumWinConstraint = 2,
            scoreConfig = RiichiScoreConfig(30000, -100, 35000, 1500),
            gameLength = RiichiGameLength.TwoWinds,
            multiRonPolicy = MultiRonPolicy(RonResolution.NEAREST_WINNER, RonResolution.ABORTIVE_DRAW),
        )
        assertRoomRoundTrip(config)
    }

    /** 驗證自訂台麻規則完整恢復，且不依賴 network DTO schema。 */
    @Test
    fun `taiwan room round-trips through encoded persistence DTO`() {
        val config = TaiwanRuleConfig(
            useFlowerTiles = false,
            minimumWinConstraint = 3,
            scoreConfig = TaiwanScoreConfig(100, 20, 1000, -1),
            gameLength = TaiwanGameLength.FourWinds,
            multiRonPolicy = MultiRonPolicy(RonResolution.ALL_WINNERS, RonResolution.NEAREST_WINNER),
        )
        assertRoomRoundTrip(config)
    }

    /** 驗證第三方規則能以穩定 type key 註冊並完成 Room round-trip。 */
    @Test
    fun `third-party rule config can register without changing persistence module`() {
        val registry = buildRuleConfigPersistenceRegistry().apply {
            register(
                typeKey = "example:custom_rule_config",
                domainClass = ThirdPartyRuleConfig::class,
                serializer = ThirdPartyRuleConfigPersistenceDto.serializer(),
                toDto = { ThirdPartyRuleConfigPersistenceDto(it.marker) },
                toDomain = { ThirdPartyRuleConfig(it.marker) },
            )
        }
        val room = createRoom(ThirdPartyRuleConfig("custom"))
        val encoded = json.encodeToString(RoomPersistenceDto.serializer(), room.toPersistenceDto(registry, json))
        val restored = json.decodeFromString(RoomPersistenceDto.serializer(), encoded).toDomain(registry, json)

        assertEquals(room, restored)
    }

    /** 驗證未知第三方 type key 不會被默默忽略或還原成錯誤規則。 */
    @Test
    fun `unknown rule config type key is rejected`() {
        val registry = buildRuleConfigPersistenceRegistry()
        val dto = createRoom(RiichiRuleConfig()).toPersistenceDto(registry).copy(
            config = TypedPersistenceDto("missing:rule", kotlinx.serialization.json.buildJsonObject { }),
        )

        assertFailsWith<IllegalStateException> { dto.toDomain(registry) }
    }

    /** 針對指定規則配置執行 Room encoded round-trip 並比對完整領域狀態。 */
    private fun assertRoomRoundTrip(config: MahjongRuleConfig) {
        val registry = buildRuleConfigPersistenceRegistry()
        val room = createRoom(config)
        val encoded = json.encodeToString(RoomPersistenceDto.serializer(), room.toPersistenceDto(registry, json))
        val restored = json.decodeFromString(RoomPersistenceDto.serializer(), encoded).toDomain(registry, json)

        assertEquals(room, restored)
    }

    /** 建立同時包含人類、AI 與準備狀態的完整測試 Room。 */
    private fun createRoom(config: MahjongRuleConfig): Room {
        val hostId = Uuid.random()
        val humanId = Uuid.random()
        val aiId = Uuid.random()
        return Room(
            id = Uuid.random(),
            hostId = hostId,
            gameConfig = GameConfig(config),
            playerIds = listOf(hostId, humanId, aiId),
            readyPlayerIds = listOf(humanId, aiId),
            aiPlayerStrategyKeys = mapOf(aiId to "random"),
        )
    }
}

/** 測試第三方 persistence registry 擴充能力的最小規則配置。 */
private data class ThirdPartyRuleConfig(
    val marker: String,
    override val initialHandSize: Int = 13,
    override val deadTileCount: Int = 0,
    override val scoreConfig: RiichiScoreConfig = RiichiScoreConfig(),
    override val gameLength: RiichiGameLength = RiichiGameLength.OneGame,
    override val minimumWinConstraint: Int = 0,
    override val minPlayers: Int = 1,
    override val maxPlayers: Int = 4,
    override val multiRonPolicy: MultiRonPolicy = MultiRonPolicy(
        RonResolution.ALL_WINNERS,
        RonResolution.ALL_WINNERS,
    ),
    override val revealsClosedKanTiles: Boolean = true,
) : MahjongRuleConfig

/** 測試第三方規則配置的 persistence DTO。 */
@Serializable
private data class ThirdPartyRuleConfigPersistenceDto(val marker: String)
