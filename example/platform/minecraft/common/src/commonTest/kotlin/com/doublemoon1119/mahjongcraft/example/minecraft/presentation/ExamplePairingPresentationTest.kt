package com.doublemoon1119.mahjongcraft.example.minecraft.presentation

import com.doublemoon1119.mahjongcraft.example.ExamplePairingWinSettlementDetailResolver
import com.doublemoon1119.mahjongcraft.example.minecraft.room.examplePairingGameConfigPresentation
import com.doublemoon1119.mahjongcraft.example.minecraft.text.ExampleMinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingDiscardPile
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigEditorSpec
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoArgument
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayRegistryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證範例 Minecraft 呈現元件可獨立登記並保留規則專屬設定。 */
class ExamplePairingPresentationTest {
    /** 規則設定包含範例自己的三個欄位與預期編輯範圍。 */
    @Test
    fun `game config presentation exposes pairing fields`() {
        val definition = examplePairingGameConfigPresentation()

        assertEquals(ExamplePairingIds.RULE_MODULE, definition.ruleModuleId)
        assertEquals(
            setOf(
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

    /** 局況 registry 同時收到兩個顯示定義與範例規則 provider。 */
    @Test
    fun `round info registers displays and provider`() {
        val registry = RoundInfoLineDisplayRegistryImpl()

        registerExamplePairingRoundInfo(registry)

        assertNotNull(registry.find(ExampleMinecraftPresentationIds.WALL_REMAINING_INFO))
        assertNotNull(registry.find(ExampleMinecraftPresentationIds.ROUND_COUNT_INFO))
        assertTrue("provider:${ExamplePairingIds.RULE_MODULE}" in registry.registrationKeys)
        assertEquals(2, registry.registrationKeys.count { it.startsWith("line:") })
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
            config = ExamplePairingRuleConfig(roundCount = 8),
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
}
