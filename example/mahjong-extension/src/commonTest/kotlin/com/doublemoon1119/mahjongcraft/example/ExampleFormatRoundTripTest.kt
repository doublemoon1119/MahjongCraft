package com.doublemoon1119.mahjongcraft.example

import com.doublemoon1119.mahjongcraft.example.action.ExamplePairingRevealHandAction
import com.doublemoon1119.mahjongcraft.example.action.ExamplePairingRevealHandCommand
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingDiscardPile
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingDynamicState
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingHandValueResult
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingPlayerState
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleModule
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DiscardPileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DynamicRuleStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExtensionGameActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExtensionGameCommandDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.MahjongRuleConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.PlayerRuleStateDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.UnregisteredPersistenceTypeException
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.emptyPersistenceRegistries
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
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
    /** 缺少範例登記時拒讀原資料，補回相同登記後可直接還原，不需重建資料。 */
    @Test
    fun `missing extension registrations reject saved data and restored registrations read it`() {
        val original = emptyPersistenceRegistries()
        ExampleMahjongExtension().registerPersistenceDtos(original)
        original.freeze()
        val config = ExamplePairingRuleConfig(exchangeEnabled = true, paymentPerPlayer = 20, roundCount = 2, wallTileValues = setOf(1, 3, 5, 7))
        val state = ExamplePairingPlayerState(handRevealed = true)
        val savedConfig = original.ruleConfigs.encode(config)
        val savedState = original.playerRuleStates.encode(state)
        val savedAction = original.extensionGameActions.encode(ExamplePairingRevealHandAction)

        val missing = emptyPersistenceRegistries()
        missing.freeze()
        assertEquals(savedConfig.typeKey, assertFailsWith<UnregisteredPersistenceTypeException> { missing.ruleConfigs.decode(savedConfig) }.typeKey)
        assertEquals(savedState.typeKey, assertFailsWith<UnregisteredPersistenceTypeException> { missing.playerRuleStates.decode(savedState) }.typeKey)
        assertEquals(savedAction.typeKey, assertFailsWith<UnregisteredPersistenceTypeException> { missing.extensionGameActions.decode(savedAction) }.typeKey)

        val restored = emptyPersistenceRegistries()
        ExampleMahjongExtension().registerPersistenceDtos(restored)
        restored.freeze()
        assertEquals(config, restored.ruleConfigs.decode(savedConfig))
        assertEquals(state, restored.playerRuleStates.decode(savedState))
        assertEquals(ExamplePairingRevealHandAction, restored.extensionGameActions.decode(savedAction))

        val restoredState = restored.playerRuleStates.decode(savedState)
        val restoredTable = FakeTableStateFactory.create(
            config = config,
            players = listOf(FakeMahjongPlayerFactory.create(playerRuleState = restoredState)),
        )
        val module = ExamplePairingRuleModule(ExamplePairingIds.RULE_MODULE, config)
        assertEquals(
            restoredState,
            module.projectPlayerRuleState(restoredTable, restoredTable.players.single(), Uuid.random()),
        )
    }

    /** 結算詳情只記實際每人付款數值，不產生其他規則的役種或翻符。 */
    @Test
    fun `settlement detail records the configured payment`() {
        val table = FakeTableStateFactory.create(config = ExamplePairingRuleConfig(paymentPerPlayer = 20))
        val fields = ExamplePairingWinSettlementDetailResolver.resolve(table, ExamplePairingHandValueResult())
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
            .copy(dynamicRuleState = ExamplePairingDynamicState(setOf(3, 1)))
        val fields = ExamplePairingWinSettlementDetailResolver.resolve(table, ExamplePairingHandValueResult(bonusTileCount = 2))
        val faces = assertIs<WinSettlementDetailValue.TileFaces>(fields.single { it.id == ExamplePairingWinSettlementDetailResolver.BONUS_TILES }.value)
        assertEquals(listOf(Tile.Extension(ExamplePairingIds.INVERTED_ONE_MAN), Tile.Numeric(Tile.Suit.Character, 3)), faces.faces)
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
        val config = ExamplePairingRuleConfig(exchangeEnabled = true, paymentPerPlayer = 20, roundCount = 2, wallTileValues = setOf(1, 3, 5, 7))
        val configSerializer = PolymorphicSerializer(MahjongRuleConfigDto::class)
        val encodedConfig = format.encodeToString(configSerializer, registries.ruleConfig.toDto(config))
        assertTrue(encodedConfig.contains("mahjongcraft_example:pairing/rule_config"))
        assertEquals(config, registries.ruleConfig.toDomain(format.decodeFromString(configSerializer, encodedConfig)))

        val state = ExamplePairingPlayerState(handRevealed = true)
        val stateSerializer = PolymorphicSerializer(PlayerRuleStateDto::class)
        val stateDto = format.decodeFromString(stateSerializer, format.encodeToString(stateSerializer, registries.playerRuleState.toDto(state)))
        assertEquals(state, registries.playerRuleState.toDomain(stateDto))

        val dynamicState = ExamplePairingDynamicState(setOf(1, 3, 5))
        val dynamicStateSerializer = PolymorphicSerializer(DynamicRuleStateDto::class)
        val dynamicStateDto = format.decodeFromString(
            dynamicStateSerializer,
            format.encodeToString(dynamicStateSerializer, registries.dynamicRuleState.toDto(dynamicState)),
        )
        assertEquals(dynamicState, registries.dynamicRuleState.toDomain(dynamicStateDto))

        val actionSerializer = PolymorphicSerializer(ExtensionGameActionDto::class)
        val actionDto = format.decodeFromString(actionSerializer, format.encodeToString(actionSerializer, registries.extensionGameAction.toDto(ExamplePairingRevealHandAction)))
        assertEquals(ExamplePairingRevealHandAction, registries.extensionGameAction.toDomain(actionDto))
        val commandSerializer = PolymorphicSerializer(ExtensionGameCommandDto::class)
        val commandDto = format.decodeFromString(commandSerializer, format.encodeToString(commandSerializer, registries.extensionGameCommand.toDto(ExamplePairingRevealHandCommand)))
        assertEquals(ExamplePairingRevealHandCommand, registries.extensionGameCommand.toDomain(commandDto))

        val tile = IdentifiedTile(id = Uuid.random(), tile = Tile.Extension(ExamplePairingIds.INVERTED_ONE_MAN))
        val pile = ExamplePairingDiscardPile().discardTile(tile).takeLast()
        val pileSerializer = PolymorphicSerializer(DiscardPileDto::class)
        val pileDto = format.decodeFromString(pileSerializer, format.encodeToString(pileSerializer, registries.discardPile.toDto(pile)))
        val restored = assertIs<ExamplePairingDiscardPile>(registries.discardPile.toDomain(pileDto))
        assertEquals(tile, restored.entries.single().tile)
        assertTrue(restored.entries.single().isTaken)
    }

    /** 保存型別使用獨立 DTO，並保留攤牌與倒置一萬的穩定身分。 */
    @Test
    fun `persistence registries preserve extension identities`() {
        val registries = emptyPersistenceRegistries()
        ExampleMahjongExtension().registerPersistenceDtos(registries)
        registries.freeze()
        val config = ExamplePairingRuleConfig(exchangeEnabled = true)
        assertEquals(config, registries.ruleConfigs.decode(registries.ruleConfigs.encode(config)))
        val state = ExamplePairingPlayerState(handRevealed = true)
        assertEquals(state, registries.playerRuleStates.decode(registries.playerRuleStates.encode(state)))
        val dynamicState = ExamplePairingDynamicState(setOf(1, 3, 5))
        assertEquals(dynamicState, registries.dynamicRuleStates.decode(registries.dynamicRuleStates.encode(dynamicState)))
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
