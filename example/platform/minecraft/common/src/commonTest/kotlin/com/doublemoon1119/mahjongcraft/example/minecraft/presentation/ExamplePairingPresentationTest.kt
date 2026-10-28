package com.doublemoon1119.mahjongcraft.example.minecraft.presentation

import com.doublemoon1119.mahjongcraft.example.ExamplePairingWinSettlementDetailResolver
import com.doublemoon1119.mahjongcraft.example.minecraft.room.examplePairingGameConfigPresentation
import com.doublemoon1119.mahjongcraft.example.minecraft.text.ExampleMinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingDiscardPile
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingDynamicState
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingPlayerState
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingTileType
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigEditorSpec
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.BuiltInTablePropDefinitions
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.BuiltInTablePropKinds
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoArgument
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropContent
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropLayoutResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPresentation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證範例 Minecraft 呈現元件可獨立登記並保留規則專屬設定。 */
class ExamplePairingPresentationTest {
    /** 規則設定包含範例自己的四個欄位與預期編輯範圍。 */
    @Test
    fun `game config presentation exposes pairing fields`() {
        val definition = examplePairingGameConfigPresentation()

        assertEquals(ExamplePairingIds.RULE_MODULE, definition.ruleModuleId)
        assertEquals(
            setOf(
                ExampleMinecraftPresentationIds.WALL_TILE_VALUES_FIELD,
                ExampleMinecraftPresentationIds.EXCHANGE_FIELD,
                ExampleMinecraftPresentationIds.PAYMENT_FIELD,
                ExampleMinecraftPresentationIds.ROUND_COUNT_FIELD,
            ),
            definition.fields.map { it.id }.toSet(),
        )
        assertEquals(
            GameConfigEditorSpec.IntegerInput(1, 100),
            definition.fields.single { it.id == ExampleMinecraftPresentationIds.PAYMENT_FIELD }.editor,
        )
        assertEquals(
            GameConfigEditorSpec.IntegerInput(1, 16),
            definition.fields.single { it.id == ExampleMinecraftPresentationIds.ROUND_COUNT_FIELD }.editor,
        )
    }

    /** 規則目錄只接受簡易配對設定，其他規則設定不會被誤認。 */
    @Test
    fun `catalogue provider accepts only pairing config`() {
        val provider = examplePairingCatalogue()

        assertNotNull(provider.catalogue(provider.defaultRuleConfig()))
        assertEquals(null, provider.catalogue(RiichiRuleConfig()))
    }

    /** 局況 registry 註冊牌參數、規則文字與範例規則 provider。 */
    @Test
    fun `round info registers displays and provider`() {
        val registry = RoundInfoLineDisplayRegistryImpl()

        registerExamplePairingRoundInfo(registry)

        assertNotNull(registry.find(ExampleMinecraftPresentationIds.WALL_REMAINING_INFO))
        assertNotNull(registry.find(ExampleMinecraftPresentationIds.ROUND_COUNT_INFO))
        assertNotNull(registry.find(ExampleMinecraftPresentationIds.LATEST_DISCARD_INFO))
        assertNotNull(registry.find(ExampleMinecraftPresentationIds.EXCHANGE_STATUS_INFO))
        assertNotNull(registry.findMessage(ExampleMinecraftPresentationIds.EXCHANGE_ENABLED_MESSAGE))
        assertNotNull(registry.findMessage(ExampleMinecraftPresentationIds.EXCHANGE_DISABLED_MESSAGE))
        assertTrue("provider:${ExamplePairingIds.RULE_MODULE}" in registry.registrationKeys)
        assertEquals(4, registry.registrationKeys.count { it.startsWith("line:") })
        val players = listOf(Wind.EAST, Wind.WEST).mapIndexed { index, wind ->
            MahjongPlayer(
                id = Uuid.random(),
                initialSeatIndex = index,
                discardPile = ExamplePairingDiscardPile(),
                seatWind = wind,
            )
        }
        val latestDiscardTile = Tile.Numeric(Tile.Suit.Dot, 5)
        val discarder = players.last().copy(
            discardPile = ExamplePairingDiscardPile().discard(
                DiscardPile.DiscardEntry(IdentifiedTile(Uuid.random(), latestDiscardTile)),
            ),
        )
        val playersWithDiscard = players.dropLast(1) + discarder
        val table = TableState(
            id = Uuid.random(),
            players = playersWithDiscard,
            config = ExamplePairingRuleConfig(exchangeEnabled = true, roundCount = 8),
            tileWall = TileWall(),
            dealerPlayerId = players.first().id,
            roundNumber = 3,
        )
        val lines = registry.buildLines(ExamplePairingIds.RULE_MODULE, table)
        assertEquals(listOf(RoundInfoArgument.Number(0)), lines.single { it.key == ExampleMinecraftPresentationIds.WALL_REMAINING_INFO }.args)
        assertEquals(
            listOf(RoundInfoArgument.Number(3), RoundInfoArgument.Number(8)),
            lines.single { it.key == ExampleMinecraftPresentationIds.ROUND_COUNT_INFO }.args,
        )
        assertEquals(
            listOf(RoundInfoArgument.TileValue(latestDiscardTile)),
            lines.single { it.key == ExampleMinecraftPresentationIds.LATEST_DISCARD_INFO }.args,
        )
        assertEquals(
            listOf(RoundInfoArgument.Message(ExampleMinecraftPresentationIds.EXCHANGE_ENABLED_MESSAGE)),
            lines.single { it.key == ExampleMinecraftPresentationIds.EXCHANGE_STATUS_INFO }.args,
        )
        val disabledTable = table.copy(config = ExamplePairingRuleConfig(exchangeEnabled = false, roundCount = 8))
        assertEquals(
            listOf(RoundInfoArgument.Message(ExampleMinecraftPresentationIds.EXCHANGE_DISABLED_MESSAGE)),
            registry.buildLines(ExamplePairingIds.RULE_MODULE, disabledTable)
                .single { it.key == ExampleMinecraftPresentationIds.EXCHANGE_STATUS_INFO }.args,
        )
    }

    /** 尚未有人捨牌時不顯示最近捨牌行，其他局況行仍照順序輸出。 */
    @Test
    fun `round info omits the latest discard before anyone discards`() {
        val registry = RoundInfoLineDisplayRegistryImpl().apply { registerExamplePairingRoundInfo(this) }
        val players = listOf(Wind.EAST, Wind.WEST).mapIndexed { index, wind ->
            MahjongPlayer(
                id = Uuid.random(),
                initialSeatIndex = index,
                discardPile = ExamplePairingDiscardPile(),
                seatWind = wind,
            )
        }
        val table = TableState(
            id = Uuid.random(),
            players = players,
            config = ExamplePairingRuleConfig(),
            tileWall = TileWall(),
            dealerPlayerId = players.first().id,
        )

        assertEquals(
            listOf(
                ExampleMinecraftPresentationIds.WALL_REMAINING_INFO,
                ExampleMinecraftPresentationIds.ROUND_COUNT_INFO,
                ExampleMinecraftPresentationIds.EXCHANGE_STATUS_INFO,
            ),
            registry.buildLines(ExamplePairingIds.RULE_MODULE, table).map { it.key },
        )
    }

    /** 結算 registry 同時收到模板、規則綁定與付款詳情格式器。 */
    @Test
    fun `settlement registration keeps template and payment formatter`() {
        val registry = WinSettlementPresentationTemplateRegistryImpl()

        registerExamplePairingSettlementTemplates(registry)

        assertNotNull(registry.findTemplate(ExampleMinecraftPresentationIds.SETTLEMENT_TEMPLATE))
        assertTrue("rule:${ExamplePairingIds.RULE_MODULE}" in registry.registrationKeys)
        val formatter = assertNotNull(registry.findDetailTextFormatter(ExamplePairingWinSettlementDetailResolver.PAYMENT_PER_PLAYER))
        assertEquals(
            PresentationValue.TextValue(ExampleMinecraftMessageKeys.POINTS, listOf("25")),
            formatter.quantities(listOf(WinSettlementQuantity(ExamplePairingWinSettlementDetailResolver.POINTS, 25))),
        )
    }

    /** 桌面中央展示讀取本局保存的加分牌，並以穩定順序包含倒置一萬。 */
    @Test
    fun `table props display saved bonus tiles in stable order`() {
        val players = listOf(Wind.EAST, Wind.WEST).mapIndexed { index, wind ->
            MahjongPlayer(
                id = Uuid.random(),
                initialSeatIndex = index,
                discardPile = ExamplePairingDiscardPile(),
                seatWind = wind,
            )
        }
        val table = TableState(
            id = Uuid.random(),
            players = players,
            config = ExamplePairingRuleConfig(wallTileValues = setOf(1, 2, 3)),
            tileWall = TileWall(),
            dealerPlayerId = players.first().id,
            dynamicRuleState = ExamplePairingDynamicState(setOf(3, 1)),
        )

        val props = ExamplePairingTableProps.describe(table)

        assertEquals(
            listOf(0, 1),
            props.map { (it.location as TablePropLocation.Center).order },
        )
        assertEquals(
            listOf(
                TablePropContent.FaceUpTile(ExamplePairingTileType.invertedOneMan()),
                TablePropContent.FaceUpTile(Tile.Numeric(Tile.Suit.Character, 3)),
            ),
            props.map { it.content },
        )
    }

    /** 同一份桌面描述同時包含中央加分牌與已攤牌玩家的自訂籌碼。 */
    @Test
    fun `table props include hand reveal chip beside bonus tiles`() {
        val revealedPlayer = MahjongPlayer(
            id = Uuid.random(),
            initialSeatIndex = 0,
            playerRuleState = ExamplePairingPlayerState(handRevealed = true),
            discardPile = ExamplePairingDiscardPile(),
            seatWind = Wind.EAST,
        )
        val table = TableState(
            id = Uuid.random(),
            players = listOf(
                revealedPlayer,
                MahjongPlayer(
                    id = Uuid.random(),
                    initialSeatIndex = 1,
                    discardPile = ExamplePairingDiscardPile(),
                    seatWind = Wind.WEST,
                ),
            ),
            config = ExamplePairingRuleConfig(wallTileValues = setOf(1, 2)),
            tileWall = TileWall(),
            dealerPlayerId = revealedPlayer.id,
            dynamicRuleState = ExamplePairingDynamicState(setOf(1)),
        )

        val props = ExamplePairingTableProps.describe(table)

        assertEquals(2, props.size)
        assertEquals("mahjongcraft:face_up_tile", props.first().kind)
        assertEquals("mahjongcraft_example:hand_reveal_chip", props.last().kind)
    }

    /** 八種牌山加四名攤牌玩家時，中央混合物件仍能通過共用幾何配置。 */
    @Test
    fun `maximum bonus tiles and hand reveal chips fit the center`() {
        val players = (0 until 4).map { index ->
            MahjongPlayer(
                id = Uuid.random(),
                initialSeatIndex = index,
                playerRuleState = ExamplePairingPlayerState(handRevealed = true),
                discardPile = ExamplePairingDiscardPile(),
                seatWind = Wind.entries[index],
            )
        }
        val table = TableState(
            id = Uuid.random(),
            players = players,
            config = ExamplePairingRuleConfig(),
            tileWall = TileWall(),
            dealerPlayerId = players.first().id,
            dynamicRuleState = ExamplePairingDynamicState((1..7).toSet()),
        )
        val placements = ExamplePairingTableProps.describe(table)
        val presentation = TablePropPresentation(
            tableId = table.id,
            ruleModuleId = ExamplePairingIds.RULE_MODULE,
            tableLocation = TableLocation("minecraft:overworld", 0, 64, 0),
            tableFacing = MahjongTableFacing.SOUTH,
            seatLayout = MahjongTableSeatLayout.FOUR_PLAYER,
            placements = placements,
        )
        val result = presentation.targets(
            mapOf(
                BuiltInTablePropKinds.FACE_UP_TILE to BuiltInTablePropDefinitions.FACE_UP_TILE,
                ExampleMinecraftPresentationIds.HAND_REVEAL_CHIP_KIND to ExampleTablePropDefinitions.HAND_REVEAL_CHIP,
            ),
        )

        assertTrue(result is TablePropLayoutResult.Success)
    }

    /** 所有玩家人數與莊家席位都面向正確桌邊，並隨桌子朝向旋轉。 */
    @Test
    fun `bonus tiles face the current dealer for every roster and table facing`() {
        val expectedSeatYaws = mapOf(2 to listOf(0f, 180f), 3 to listOf(0f, 270f, 180f), 4 to listOf(0f, 270f, 180f, 90f))
        val facingYaws = mapOf(MahjongTableFacing.NORTH to 0f, MahjongTableFacing.EAST to 90f, MahjongTableFacing.SOUTH to 180f, MahjongTableFacing.WEST to 270f)
        for ((count, seatYaws) in expectedSeatYaws) {
            val players = (0 until count).map { index ->
                MahjongPlayer(
                    id = Uuid.random(),
                    initialSeatIndex = index,
                    playerRuleState = ExamplePairingPlayerState(handRevealed = true),
                    discardPile = ExamplePairingDiscardPile(),
                    seatWind = Wind.entries[index],
                )
            }
            for (dealer in players.indices) {
                val state = TableState(
                    id = Uuid.random(),
                    players = players,
                    config = ExamplePairingRuleConfig(),
                    tileWall = TileWall(),
                    dealerPlayerId = players[dealer].id,
                    dynamicRuleState = ExamplePairingDynamicState((1..7).toSet()),
                )
                val props = ExamplePairingTableProps.describe(state)
                assertTrue(props.filter { it.kind == BuiltInTablePropKinds.FACE_UP_TILE }.all { (it.location as TablePropLocation.Center).yaw == seatYaws[dealer] })
                for ((facing, facingYaw) in facingYaws) {
                    val scene = TablePropPresentation(
                        tableId = state.id,
                        ruleModuleId = ExamplePairingIds.RULE_MODULE,
                        tableLocation = TableLocation("minecraft:overworld", 5, 64, -8),
                        tableFacing = facing,
                        seatLayout = MahjongTableSeatLayout.forPlayerCount(count),
                        placements = props,
                    )
                    val targets = assertIs<TablePropLayoutResult.Success>(scene.targets(mapOf(BuiltInTablePropKinds.FACE_UP_TILE to BuiltInTablePropDefinitions.FACE_UP_TILE, ExampleMinecraftPresentationIds.HAND_REVEAL_CHIP_KIND to ExampleTablePropDefinitions.HAND_REVEAL_CHIP))).targets
                    assertTrue(targets.filter { it.kind == BuiltInTablePropKinds.FACE_UP_TILE }.all { it.yaw == (seatYaws[dealer] + facingYaw).mod(360f) })
                }
            }
        }
    }
}
