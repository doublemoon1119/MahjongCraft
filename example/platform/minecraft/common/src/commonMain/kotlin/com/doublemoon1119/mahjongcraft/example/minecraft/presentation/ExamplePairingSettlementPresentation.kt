package com.doublemoon1119.mahjongcraft.example.minecraft.presentation

import com.doublemoon1119.mahjongcraft.example.ExamplePairingWinSettlementDetailResolver
import com.doublemoon1119.mahjongcraft.example.minecraft.text.ExampleMinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.BuiltInWinSettlementFieldIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.FallbackWinSettlementDetailTextFormatter
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationContainerStyle
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationFieldId
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementDetailTextFormatter
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementDetailTextFormatterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationFieldProviderDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplate
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementRuleTemplateBindingDefinition

/**
 * 登記簡易配對麻將使用的胡牌結算模板與付款詳情格式器。
 *
 * @param registry 要寫入結算模板與格式器的 registry。
 */
fun registerExamplePairingSettlementTemplates(registry: WinSettlementPresentationTemplateRegistry) {
    listOf(
        ExamplePairingWinSettlementDetailResolver.BONUS_TILE_COUNT,
        ExamplePairingWinSettlementDetailResolver.BONUS_TILES,
    ).forEach { id ->
        val fieldId = PresentationFieldId(id)
        registry.register(WinSettlementPresentationFieldProviderDefinition(fieldId) { snapshot -> snapshot.extensionField(fieldId) })
    }
    registry.register(
        WinSettlementPresentationFieldProviderDefinition(PresentationFieldId(ExampleMinecraftPresentationIds.BONUS_TILES_LABEL)) {
            PresentationValue.TextValue(ExampleMinecraftMessageKeys.BONUS_TILES, emptyList())
        },
    )
    registry.register(
        WinSettlementPresentationTemplate(
            key = ExampleMinecraftPresentationIds.SETTLEMENT_TEMPLATE,
            root = PresentationLayout.Column(
                children = listOf(
                    PresentationLayout.Text(BuiltInWinSettlementFieldIds.OUTCOME_TITLE),
                    PresentationLayout.PlayerIdentity(BuiltInWinSettlementFieldIds.WINNER_IDENTITY),
                    PresentationLayout.Row(
                        children = listOf(
                            PresentationLayout.TileGroups(BuiltInWinSettlementFieldIds.COMPLETE_HAND_GROUPS),
                            PresentationLayout.Tile(BuiltInWinSettlementFieldIds.WINNING_TILE),
                        ),
                        spacing = 5f,
                    ),
                    PresentationLayout.Text(PresentationFieldId(ExampleMinecraftPresentationIds.BONUS_TILES_LABEL)),
                    PresentationLayout.TileList(
                        fieldId = PresentationFieldId(ExamplePairingWinSettlementDetailResolver.BONUS_TILES),
                        tileWidth = 8f,
                        tileHeight = 11f,
                        spacing = 2f,
                    ),
                    PresentationLayout.Text(PresentationFieldId(ExamplePairingWinSettlementDetailResolver.BONUS_TILE_COUNT)),
                    PresentationLayout.Text(BuiltInWinSettlementFieldIds.PAYMENT_SUMMARY),
                    PresentationLayout.Text(BuiltInWinSettlementFieldIds.TOTAL_SCORE),
                ),
                spacing = 6f,
                style = PresentationContainerStyle(backgroundArgb = 0xCC101722.toInt(), padding = 8f),
            ),
            detailFieldLabelKeys = mapOf(
                PresentationFieldId(ExamplePairingWinSettlementDetailResolver.BONUS_TILES) to ExampleMinecraftMessageKeys.BONUS_TILES,
                PresentationFieldId(ExamplePairingWinSettlementDetailResolver.PAYMENT_PER_PLAYER) to ExampleMinecraftMessageKeys.PAYMENT_PER_PLAYER,
                PresentationFieldId(ExamplePairingWinSettlementDetailResolver.BONUS_TILE_COUNT) to ExampleMinecraftMessageKeys.BONUS_TILE_COUNT,
            ),
        ),
    )
    registry.register(
        WinSettlementRuleTemplateBindingDefinition(
            ruleModuleId = ExamplePairingIds.RULE_MODULE,
            templateKey = ExampleMinecraftPresentationIds.SETTLEMENT_TEMPLATE,
        ),
    )
    registry.register(
        WinSettlementDetailTextFormatterDefinition(
            fieldId = ExamplePairingWinSettlementDetailResolver.PAYMENT_PER_PLAYER,
            formatter = ExamplePairingPaymentFormatter,
        ),
    )
    registry.register(
        WinSettlementDetailTextFormatterDefinition(
            fieldId = ExamplePairingWinSettlementDetailResolver.BONUS_TILE_COUNT,
            formatter = ExamplePairingBonusTileFormatter,
        ),
    )
}

/** 將自摸手牌命中的不同加分牌種類數量顯示為加分點數。 */
private object ExamplePairingBonusTileFormatter : WinSettlementDetailTextFormatter {
    override fun quantities(quantities: List<WinSettlementQuantity>): PresentationValue.TextValue {
        val count = quantities.singleOrNull()?.takeIf { it.unitId == ExamplePairingWinSettlementDetailResolver.POINTS }
            ?: return FallbackWinSettlementDetailTextFormatter.quantities(quantities)
        return PresentationValue.TextValue(
            translationKey = ExampleMinecraftMessageKeys.BONUS_TILE_POINTS,
            arguments = listOf(count.amount.toString()),
        )
    }

    override fun entries(entries: List<WinSettlementDetailEntry>): PresentationValue.EntryListValue = FallbackWinSettlementDetailTextFormatter.entries(entries)
}

/** 將簡易配對的付款點數轉成翻譯文字。 */
private object ExamplePairingPaymentFormatter : WinSettlementDetailTextFormatter {
    override fun quantities(quantities: List<WinSettlementQuantity>): PresentationValue.TextValue {
        val payment = quantities.singleOrNull()?.takeIf { it.unitId == ExamplePairingWinSettlementDetailResolver.POINTS }
            ?: return FallbackWinSettlementDetailTextFormatter.quantities(quantities)
        return PresentationValue.TextValue(
            translationKey = ExampleMinecraftMessageKeys.PAYMENT_AMOUNT,
            arguments = listOf(payment.amount.toString()),
        )
    }

    override fun entries(entries: List<WinSettlementDetailEntry>): PresentationValue.EntryListValue = FallbackWinSettlementDetailTextFormatter.entries(entries)
}
