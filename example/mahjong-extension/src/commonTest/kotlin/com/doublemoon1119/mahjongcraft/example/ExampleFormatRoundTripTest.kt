package com.doublemoon1119.mahjongcraft.example

import com.doublemoon1119.mahjongcraft.example.action.ExamplePairingRevealHandAction
import com.doublemoon1119.mahjongcraft.example.action.ExamplePairingRevealHandCommand
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingBonusTiles
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingComponents
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingDiscardPile
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingHandValueResult
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleModule
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExtensionGameActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExtensionGameCommandDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.MahjongRuleConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.DiscardPileSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.toDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.UnregisteredPersistenceTypeException
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.emptyPersistenceRegistries
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentProjectionContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentReadContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.serialization.PolymorphicSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 用少量代表資料確認擴充自己的型別可經正式網路與保存註冊表往返。 */
class ExampleFormatRoundTripTest {
    /** 缺少範例登記時拒讀原資料，補回相同登記後可直接還原，不需重建資料；攤牌狀態不交付觀察者。 */
    @Test
    fun `missing extension registrations reject saved data and restored registrations read it`() {
        val original = emptyPersistenceRegistries()
        ExampleMahjongExtension().registerPersistenceDtos(original)
        original.freeze()
        val config = ExamplePairingRuleConfig(exchangeEnabled = true, paymentPerPlayer = 20, roundCount = 2, wallTileValues = setOf(1, 3, 5, 7), publicFacesEnabled = true)
        val state = ComponentSet.EMPTY.with(ExamplePairingComponents.HAND_REVEALED, Unit)
        val savedConfig = original.ruleConfigs.encode(config)
        val savedState = original.components.encode(state)
        val savedAction = original.extensionGameActions.encode(ExamplePairingRevealHandAction)

        val missing = emptyPersistenceRegistries()
        missing.freeze()
        assertEquals(savedConfig.typeKey, assertFailsWith<UnregisteredPersistenceTypeException> { missing.ruleConfigs.decode(savedConfig) }.typeKey)
        assertEquals(savedState.single().typeKey, assertFailsWith<UnregisteredPersistenceTypeException> { missing.components.decode(savedState) }.typeKey)
        assertEquals(savedAction.typeKey, assertFailsWith<UnregisteredPersistenceTypeException> { missing.extensionGameActions.decode(savedAction) }.typeKey)

        val restored = emptyPersistenceRegistries()
        ExampleMahjongExtension().registerPersistenceDtos(restored)
        restored.freeze()
        assertEquals(config, restored.ruleConfigs.decode(savedConfig))
        assertEquals(state, restored.components.decode(savedState))
        assertEquals(ExamplePairingRevealHandAction, restored.extensionGameActions.decode(savedAction))

        val restoredState = restored.components.decode(savedState)
        val restoredTable = FakeTableStateFactory.create(
            config = config,
            players = listOf(FakeMahjongPlayerFactory.create(components = restoredState)),
        )
        val module = ExamplePairingRuleModule(ExamplePairingIds.RULE_MODULE, config)
        val projected = exampleComponentRegistry().project(
            ComponentProjectionContext(
                ruleModuleId = module.id,
                tableState = restoredTable,
                ownerType = BuiltInComponentOwners.PLAYER,
                owner = restoredTable.players.single(),
                reader = ComponentReadContext.Public,
            ),
            restoredState,
        )
        assertTrue(projected.isEmpty)
    }

    /** 結算詳情只記實際每人付款數值，不產生其他規則的役種或翻符。 */
    @Test
    fun `settlement detail records the configured payment`() {
        val table = FakeTableStateFactory.create(config = ExamplePairingRuleConfig(paymentPerPlayer = 20))
            .copy(components = ComponentSet.EMPTY.with(ExamplePairingComponents.BONUS_TILES, ExamplePairingBonusTiles(setOf(1))))
        val fields = ExamplePairingWinSettlementDetailResolver.resolve(table, table.players.first().id, ExamplePairingHandValueResult()) { TileFaceView(it.tile) }
        val field = fields.first { it.id == ExamplePairingWinSettlementDetailResolver.PAYMENT_PER_PLAYER }
        assertEquals(ExamplePairingWinSettlementDetailResolver.PAYMENT_PER_PLAYER, field.id)
        val quantity = assertIs<WinSettlementDetailValue.Quantities>(field.value).quantities.single()
        assertEquals(ExamplePairingWinSettlementDetailResolver.POINTS, quantity.unitId)
        assertEquals(20, quantity.amount)
        val bonus = fields.first { it.id == ExamplePairingWinSettlementDetailResolver.BONUS_TILE_COUNT }
        val bonusQuantity = assertIs<WinSettlementDetailValue.Quantities>(bonus.value).quantities.single()
        assertEquals(0, bonusQuantity.amount)
    }

    /** 加分牌以獨立牌面傳遞，付款包含命中種類的加分。 */
    @Test
    fun `settlement includes saved bonus faces and total payment`() {
        val table = FakeTableStateFactory.create(config = ExamplePairingRuleConfig(paymentPerPlayer = 10))
            .copy(components = ComponentSet.EMPTY.with(ExamplePairingComponents.BONUS_TILES, ExamplePairingBonusTiles(setOf(3, 1))))
        val fields = ExamplePairingWinSettlementDetailResolver.resolve(table, table.players.first().id, ExamplePairingHandValueResult(bonusTileCount = 2)) { TileFaceView(it.tile) }
        val faces = assertIs<WinSettlementDetailValue.TileFaces>(fields.single { it.id == ExamplePairingWinSettlementDetailResolver.BONUS_TILES }.value)
        assertEquals(listOf(Tile.Extension(ExamplePairingIds.INVERTED_ONE_MAN), Tile.Numeric(Tile.Suit.Character, 3)).map(::TileFaceView), faces.faces)
        assertEquals(12, assertIs<WinSettlementDetailValue.Quantities>(fields.single { it.id == ExamplePairingWinSettlementDetailResolver.PAYMENT_PER_PLAYER }.value).quantities.single().amount)
        assertEquals(2, assertIs<WinSettlementDetailValue.Quantities>(fields.single { it.id == ExamplePairingWinSettlementDetailResolver.BONUS_TILE_COUNT }.value).quantities.single().amount)
    }

    /** 網路多型格式必須保留設定、公開狀態、動作、命令及擴充牌種。 */
    @Test
    fun `network registries round trip custom data without bundled registrations`() {
        val registries = DefaultNetworkDtoRegistries()
        ExampleMahjongExtension().registerNetworkDtos(registries)
        registries.freeze()
        val format = NetworkJson(registries)
        val config = ExamplePairingRuleConfig(exchangeEnabled = true, paymentPerPlayer = 20, roundCount = 2, wallTileValues = setOf(1, 3, 5, 7), publicFacesEnabled = true)
        val configSerializer = PolymorphicSerializer(MahjongRuleConfigDto::class)
        val encodedConfig = format.encodeToString(configSerializer, registries.ruleConfig.toDto(config))
        assertTrue(encodedConfig.contains("mahjongcraft_example:pairing/rule_config"))
        assertEquals(config, registries.ruleConfig.toDomain(format.decodeFromString(configSerializer, encodedConfig)))

        val actionSerializer = PolymorphicSerializer(ExtensionGameActionDto::class)
        val actionDto = format.decodeFromString(actionSerializer, format.encodeToString(actionSerializer, registries.extensionGameAction.toDto(ExamplePairingRevealHandAction)))
        assertEquals(ExamplePairingRevealHandAction, registries.extensionGameAction.toDomain(actionDto))
        val commandSerializer = PolymorphicSerializer(ExtensionGameCommandDto::class)
        val commandDto = format.decodeFromString(commandSerializer, format.encodeToString(commandSerializer, registries.extensionGameCommand.toDto(ExamplePairingRevealHandCommand)))
        assertEquals(ExamplePairingRevealHandCommand, registries.extensionGameCommand.toDomain(commandDto))

        val tile = IdentifiedTile(id = Uuid.random(), tile = Tile.Extension(ExamplePairingIds.INVERTED_ONE_MAN))
        val pile = ExamplePairingDiscardPile().discardTile(tile).takeLast()
        val snapshot = pile.toSnapshot()
        val encodedPile = format.encodeToString(DiscardPileSnapshotDto.serializer(), snapshot.toDto(registries))
        val restored = format.decodeFromString(DiscardPileSnapshotDto.serializer(), encodedPile).toDomain(registries)
        assertEquals(snapshot, restored)
        assertEquals(tile.id, restored.entries.single().tile.id)
        assertTrue(restored.entries.single().isTaken)
    }

    /** 保存型別使用獨立 DTO，並保留攤牌與倒置一萬的穩定身分。 */
    @Test
    fun `persistence registries preserve extension identities`() {
        val registries = emptyPersistenceRegistries()
        ExampleMahjongExtension().registerPersistenceDtos(registries)
        registries.freeze()
        val config = ExamplePairingRuleConfig(exchangeEnabled = true, publicFacesEnabled = true)
        assertEquals(config, registries.ruleConfigs.decode(registries.ruleConfigs.encode(config)))
        val publicFace = ComponentSet.EMPTY.with(ExamplePairingComponents.PUBLIC_FACE, Unit)
        assertEquals(publicFace, registries.components.decode(registries.components.encode(publicFace)))
        val state = ComponentSet.EMPTY.with(ExamplePairingComponents.HAND_REVEALED, Unit)
        assertEquals(state, registries.components.decode(registries.components.encode(state)))
        val bonusTiles = ComponentSet.EMPTY.with(ExamplePairingComponents.BONUS_TILES, ExamplePairingBonusTiles(setOf(1, 3, 5)))
        assertEquals(bonusTiles, registries.components.decode(registries.components.encode(bonusTiles)))
        val encodedAction = registries.extensionGameActions.encode(ExamplePairingRevealHandAction)
        assertEquals(ExamplePairingIds.REVEAL_HAND_ACTION, encodedAction.typeKey)
        assertEquals(ExamplePairingRevealHandAction, registries.extensionGameActions.decode(encodedAction))
        assertEquals(ExamplePairingExhaustiveDrawReason, registries.exhaustiveDrawReasons.decode(registries.exhaustiveDrawReasons.encode(ExamplePairingExhaustiveDrawReason)))
        val tile = IdentifiedTile(id = Uuid.random(), tile = Tile.Extension(ExamplePairingIds.INVERTED_ONE_MAN))
        val pile = ExamplePairingDiscardPile().discardTile(tile).takeLast()
        val restored = registries.discardPiles.decode(registries.discardPiles.encode(pile))
        assertEquals(tile, restored.entries.single().tile)
        assertTrue(restored.entries.single().isTaken)
    }
}
