package com.doublemoon1119.mahjongcraft.example.minecraft

import com.doublemoon1119.mahjongcraft.example.minecraft.presentation.ExampleMinecraftPresentationIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.MahjongRuleConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.emptyPersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.state.AuthoritativeStatePersistenceCodec
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.UpdateConfigUseCase
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.component.AuthoritativeComponentValidator
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigEditorSpec
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomConfigSubmission
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.repository.FakeRoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.service.FakeRoomEventPublisher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.PolymorphicSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

/** 以實際範例登記、網路設定、房間更新與保存路徑驗證牌山多選，不只檢查畫面控制項。 */
class ExamplePairingMultiChoiceSubmissionTest {
    /** 範例以同一個擴充入口登記規則與各邊界。 */
    private val extension = ExampleMinecraftMahjongExtension()

    /** 使用正式設定呈現重建客戶端送來的集合，再經房間用例寫入並保存。 */
    @Test
    fun `wall selection passes real submission updates and authoritative persistence`() = runTest {
        val current = GameConfig(ExamplePairingRuleConfig())
        val modules = MahjongModuleRegistryImpl().apply(extension::registerRuleModules).apply { freeze() }
        val resolvers = RuleDecisionResolverRegistry().apply(extension::registerRuleDecisionResolvers).apply { freeze() }
        val presentations = GameConfigPresentationRegistryImpl(resolvers).apply(extension::registerGameConfigPresentations).apply { freeze() }
        val field = assertNotNull(presentations.find(ExamplePairingIds.RULE_MODULE)).fields.single {
            it.id == ExampleMinecraftPresentationIds.WALL_TILE_VALUES_FIELD
        }
        val selectedIds = setOf(ExamplePairingIds.tileValue(2), ExamplePairingIds.tileValue(7))
        val editor = assertIs<GameConfigEditorSpec.MultiChoice>(field.editor)
        assertFalse(editor.accepts(GameConfigPresentationValue.MultiChoiceValue(setOf("another:pairing/tile_2", ExamplePairingIds.tileValue(7)))))
        assertFalse(editor.accepts(GameConfigPresentationValue.MultiChoiceValue(setOf(ExamplePairingIds.tileValue(2)))))
        val draft = assertNotNull(field.update)(current, GameConfigPresentationValue.MultiChoiceValue(selectedIds))
        assertEquals(setOf(2, 7), (draft.ruleConfig as ExamplePairingRuleConfig).wallTileValues)
        assertFailsWith<IllegalArgumentException> {
            assertNotNull(field.update)(current, GameConfigPresentationValue.MultiChoiceValue(setOf("another:pairing/tile_2", ExamplePairingIds.tileValue(7))))
        }
        val network = DefaultNetworkDtoRegistries().apply(extension::registerNetworkDtos).apply { freeze() }
        val json = NetworkJson(network)
        val serializer = PolymorphicSerializer(MahjongRuleConfigDto::class)
        val request = json.encodeToString(serializer, network.ruleConfig.toDto(draft.ruleConfig))
        val submitted = draft.copy(ruleConfig = network.ruleConfig.toDomain(json.decodeFromString(serializer, request)))
        val accepted = assertIs<RoomConfigSubmission.Result.Accepted>(RoomConfigSubmission.review(submitted, current, presentations, modules))
        val store = AuthoritativeStateStore()
        val rooms = RoomRepositoryImpl(store)
        val players = List(2) { Uuid.random() }
        val room = Room(id = Uuid.random(), hostId = players.first(), playerIds = players, gameConfig = current)
        rooms.setRoom(room)
        val update = UpdateConfigUseCase(rooms, FakeRoomSnapshotRepository(), FakeRoomEventPublisher())
        assertIs<Outcome.Success<*>>(update(room.id, room.hostId, accepted.config))
        val updated = assertNotNull(rooms.getRoom(room.id))
        assertEquals(selectedIds, (field.read(updated.gameConfig) as GameConfigPresentationValue.MultiChoiceValue).optionIds)
        val persistence = emptyPersistenceRegistries().apply(extension::registerPersistenceDtos).apply { freeze() }
        val codec = AuthoritativeStatePersistenceCodec(persistence, componentValidator = AuthoritativeComponentValidator { error("This fixture only saves rooms") })
        val restored = codec.decode(codec.encode(listOf(updated), emptyList())).rooms.values.single()
        assertEquals(updated, restored)
        assertEquals(selectedIds, (field.read(restored.gameConfig) as GameConfigPresentationValue.MultiChoiceValue).optionIds)
    }

    /** 三人或四人已在房間時，不能套用只足夠更少玩家的牌山；拒絕後原房間保持不變。 */
    @Test
    fun `room rejects wall selection too small for its actual roster`() = runTest {
        for ((playerCount, selectedValues) in listOf(3 to setOf(1, 2), 4 to setOf(1, 2, 3))) {
            val players = List(playerCount) { Uuid.random() }
            val room = Room(
                id = Uuid.random(),
                hostId = players.first(),
                playerIds = players,
                gameConfig = GameConfig(ExamplePairingRuleConfig()),
            )
            val rooms = RoomRepositoryImpl(AuthoritativeStateStore())
            rooms.setRoom(room)
            val update = UpdateConfigUseCase(rooms, FakeRoomSnapshotRepository(), FakeRoomEventPublisher())
            val result = update(room.id, room.hostId, GameConfig(ExamplePairingRuleConfig(wallTileValues = selectedValues)))
            assertEquals(Outcome.Error(RoomError.RoomPlayerCountInvalid(room.id)), result)
            assertEquals(room, rooms.getRoom(room.id))
        }
    }
}
