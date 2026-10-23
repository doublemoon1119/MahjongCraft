package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExtensionGameActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.MahjongRuleConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.DebugMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledNetworkDtos
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.serialization.PolymorphicSerializer
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證開發用可宣告不聽四人日麻的一般流局結算與格式。 */
class DebugNotenDeclarationRiichiRuleModuleTest {
    /** 受測規則模組。 */
    private val module = DebugNotenDeclarationRiichiRuleModule(
        DebugNotenDeclarationRiichiRuleModule.ID,
        DebugNotenDeclarationRiichiRuleConfig(RiichiRuleConfig()),
    )

    /** 動作紀錄中有宣告不聽的聽牌玩家，一般流局結算時不公開手牌，照不聽支付罰符。 */
    @Test
    fun `a tenpai player who declared noten settles as noten`() {
        val tenpai = FakeMahjongPlayerFactory.create(Wind.EAST).copy(hand = tenpaiHand())
        val declared = FakeMahjongPlayerFactory.create(Wind.SOUTH).copy(hand = tenpaiHand())
            .recordAction(GameAction.Extension(DebugDeclareNotenAction))
        val noten = listOf(Wind.WEST, Wind.NORTH).map { FakeMahjongPlayerFactory.create(it).copy(hand = notenHand()) }
        val state = FakeTableStateFactory.create(players = listOf(tenpai, declared) + noten, config = module.config)

        val result = module.declareExhaustiveDraw(state)

        assertEquals(setOf(tenpai.id), result.tenpaiPlayerIds)
        assertEquals(listOf(tenpai.id), result.revealedHands.map { it.playerId })
        assertEquals(mapOf(tenpai.id to 3000, declared.id to -1000) + noten.associate { it.id to -1000 }, result.scoreDeltas)
    }

    /** 規則設定與宣告不聽動作都可以經網路格式與存檔格式往返。 */
    @Test
    fun `the config and the noten declaration round trip through network and persistence formats`() {
        val config = DebugNotenDeclarationRiichiRuleConfig(RiichiRuleConfig(redDoraCount = 0))
        val debugExtension = DebugMahjongExtension(winRoundContinuationState = DebugWinRoundContinuationState())
        val networkRegistries = DefaultNetworkDtoRegistries().apply {
            registerBundledNetworkDtos()
            debugExtension.registerNetworkDtos(this)
            freeze()
        }
        val format = NetworkJson(networkRegistries)
        val configSerializer = PolymorphicSerializer(MahjongRuleConfigDto::class)
        val encodedConfig = format.encodeToString(configSerializer, networkRegistries.ruleConfig.toDto(config))
        val actionSerializer = PolymorphicSerializer(ExtensionGameActionDto::class)
        val encodedAction = format.encodeToString(actionSerializer, networkRegistries.extensionGameAction.toDto(DebugDeclareNotenAction))

        assertEquals(config, networkRegistries.ruleConfig.toDomain(format.decodeFromString(configSerializer, encodedConfig)))
        assertEquals(DebugDeclareNotenAction, networkRegistries.extensionGameAction.toDomain(format.decodeFromString(actionSerializer, encodedAction)))
        val persistenceRegistries = bundledPersistenceRegistries().apply {
            debugExtension.registerPersistenceDtos(this)
            freeze()
        }
        assertEquals(config, persistenceRegistries.ruleConfigs.decode(persistenceRegistries.ruleConfigs.encode(config)))
        assertEquals(
            DebugDeclareNotenAction,
            persistenceRegistries.extensionGameActions.decode(persistenceRegistries.extensionGameActions.encode(DebugDeclareNotenAction)),
        )
    }

    /**
     * 十三張萬子立牌 1112345678999m；加入任一種萬子都能和牌。
     *
     * @return 聽牌的手牌。
     */
    private fun tenpaiHand(): Hand = hand(listOf(1, 1, 1, 2, 3, 4, 5, 6, 7, 8, 9, 9, 9).map { Tile.Numeric(Tile.Suit.Character, it) })

    /**
     * 三種數牌各取 1、4、7，再加四種風牌。
     *
     * @return 不聽的手牌。
     */
    private fun notenHand(): Hand = hand(
        listOf(Tile.Suit.Character, Tile.Suit.Dot, Tile.Suit.Bamboo).flatMap { suit -> listOf(1, 4, 7).map { Tile.Numeric(suit, it) } } +
            listOf(Tile.Honor.East, Tile.Honor.South, Tile.Honor.West, Tile.Honor.North),
    )

    /**
     * 以指定牌種建立立牌。
     *
     * @param tiles 立牌的牌種。
     * @return 只有立牌的手牌。
     */
    private fun hand(tiles: List<Tile>): Hand = Hand(tiles = tiles.map { FakeIdentifiedTileFactory.create(it) })
}
