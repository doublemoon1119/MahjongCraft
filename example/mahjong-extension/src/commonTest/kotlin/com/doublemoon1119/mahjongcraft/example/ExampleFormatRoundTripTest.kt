package com.doublemoon1119.mahjongcraft.example

import com.doublemoon1119.mahjongcraft.example.action.ExamplePairingDeclarationAction
import com.doublemoon1119.mahjongcraft.example.action.ExamplePairingDeclarationCommand
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingDiscardPile
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingHandValueResult
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingPlayerState
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DiscardPileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExtensionGameActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExtensionGameCommandDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.MahjongRuleConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.PlayerRuleStateDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.emptyPersistenceRegistries
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.serialization.PolymorphicSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 用少量代表資料確認擴充自己的型別可經正式網路與保存註冊表往返。 */
class ExampleFormatRoundTripTest {
    /** 結算詳情只記實際每人付款數值，不產生其他規則的役種或翻符。 */
    @Test
    fun `settlement detail records the configured payment`() {
        val table = FakeTableStateFactory.create(config = ExamplePairingRuleConfig(paymentPerPlayer = 20))
        val field = ExamplePairingWinSettlementDetailResolver.resolve(table, ExamplePairingHandValueResult).single()
        assertEquals(ExamplePairingWinSettlementDetailResolver.PAYMENT_PER_PLAYER, field.id)
        val quantity = assertIs<WinSettlementDetailValue.Quantities>(field.value).quantities.single()
        assertEquals(ExamplePairingWinSettlementDetailResolver.POINTS, quantity.unitId)
        assertEquals(20, quantity.amount)
    }

    /** 網路多型格式必須保留設定、公開狀態、動作、命令及擴充牌種。 */
    @Test
    fun `network registries round trip custom data without bundled registrations`() {
        val registries = DefaultNetworkDtoRegistries()
        ExampleMahjongExtension().registerNetworkDtos(registries)
        registries.freeze()
        val format = NetworkJson(registries)
        val config = ExamplePairingRuleConfig(exchangeEnabled = true, paymentPerPlayer = 20, roundCount = 2)
        val configSerializer = PolymorphicSerializer(MahjongRuleConfigDto::class)
        val encodedConfig = format.encodeToString(configSerializer, registries.ruleConfig.toDto(config))
        assertTrue(encodedConfig.contains("mahjongcraft_example:pairing/rule_config"))
        assertEquals(config, registries.ruleConfig.toDomain(format.decodeFromString(configSerializer, encodedConfig)))

        val state = ExamplePairingPlayerState(declared = true)
        val stateSerializer = PolymorphicSerializer(PlayerRuleStateDto::class)
        val stateDto = format.decodeFromString(stateSerializer, format.encodeToString(stateSerializer, registries.playerRuleState.toDto(state)))
        assertEquals(state, registries.playerRuleState.toDomain(stateDto))

        val actionSerializer = PolymorphicSerializer(ExtensionGameActionDto::class)
        val actionDto = format.decodeFromString(actionSerializer, format.encodeToString(actionSerializer, registries.extensionGameAction.toDto(ExamplePairingDeclarationAction)))
        assertEquals(ExamplePairingDeclarationAction, registries.extensionGameAction.toDomain(actionDto))
        val commandSerializer = PolymorphicSerializer(ExtensionGameCommandDto::class)
        val commandDto = format.decodeFromString(commandSerializer, format.encodeToString(commandSerializer, registries.extensionGameCommand.toDto(ExamplePairingDeclarationCommand)))
        assertEquals(ExamplePairingDeclarationCommand, registries.extensionGameCommand.toDomain(commandDto))

        val tile = IdentifiedTile(id = Uuid.random(), tile = Tile.Extension(ExamplePairingIds.INVERTED_ONE_MAN))
        val pile = ExamplePairingDiscardPile().discardTile(tile).takeLast()
        val pileSerializer = PolymorphicSerializer(DiscardPileDto::class)
        val pileDto = format.decodeFromString(pileSerializer, format.encodeToString(pileSerializer, registries.discardPile.toDto(pile)))
        val restored = assertIs<ExamplePairingDiscardPile>(registries.discardPile.toDomain(pileDto))
        assertEquals(tile, restored.entries.single().tile)
        assertTrue(restored.entries.single().isTaken)
    }

    /** 保存型別使用獨立 DTO，並保留公開宣告與倒置一萬的穩定身分。 */
    @Test
    fun `persistence registries preserve extension identities`() {
        val registries = emptyPersistenceRegistries()
        ExampleMahjongExtension().registerPersistenceDtos(registries)
        registries.freeze()
        val config = ExamplePairingRuleConfig(exchangeEnabled = true)
        assertEquals(config, registries.ruleConfigs.decode(registries.ruleConfigs.encode(config)))
        val state = ExamplePairingPlayerState(declared = true)
        assertEquals(state, registries.playerRuleStates.decode(registries.playerRuleStates.encode(state)))
        val encodedAction = registries.extensionGameActions.encode(ExamplePairingDeclarationAction)
        assertEquals(ExamplePairingIds.DECLARE_ACTION, encodedAction.typeKey)
        assertEquals(ExamplePairingDeclarationAction, registries.extensionGameActions.decode(encodedAction))
        assertEquals(ExamplePairingExhaustiveDrawReason, registries.exhaustiveDrawReasons.decode(registries.exhaustiveDrawReasons.encode(ExamplePairingExhaustiveDrawReason)))
        val tile = IdentifiedTile(id = Uuid.random(), tile = Tile.Extension(ExamplePairingIds.INVERTED_ONE_MAN))
        val pile = ExamplePairingDiscardPile().discardTile(tile).takeLast()
        val restored = registries.discardPiles.decode(registries.discardPiles.encode(pile))
        assertEquals(tile, restored.entries.single().tile)
        assertTrue(restored.entries.single().isTaken)
    }
}
