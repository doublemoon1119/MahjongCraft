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
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplate
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementRuleTemplateBindingDefinition

/**
 * 登記簡易配對麻將使用的胡牌結算模板與付款詳情格式器。
 *
 * @param registry 要寫入結算模板與格式器的 registry。
 */
fun registerExamplePairingSettlementTemplates(registry: WinSettlementPresentationTemplateRegistry) {
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
                    PresentationLayout.Text(PresentationFieldId(ExamplePairingWinSettlementDetailResolver.PAYMENT_PER_PLAYER)),
                    PresentationLayout.Text(BuiltInWinSettlementFieldIds.PAYMENT_SUMMARY),
                    PresentationLayout.Text(BuiltInWinSettlementFieldIds.TOTAL_SCORE),
                ),
                spacing = 6f,
                style = PresentationContainerStyle(backgroundArgb = 0xCC101722.toInt(), padding = 8f),
            ),
            detailFieldLabelKeys = mapOf(
                PresentationFieldId(ExamplePairingWinSettlementDetailResolver.PAYMENT_PER_PLAYER) to ExampleMinecraftMessageKeys.PAYMENT_PER_PLAYER,
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
}

/** 將簡易配對的付款點數轉成翻譯文字。 */
private object ExamplePairingPaymentFormatter : WinSettlementDetailTextFormatter {
    override fun quantities(quantities: List<WinSettlementQuantity>): PresentationValue.TextValue {
        val payment = quantities.singleOrNull()?.takeIf { it.unitId == ExamplePairingWinSettlementDetailResolver.POINTS }
            ?: return FallbackWinSettlementDetailTextFormatter.quantities(quantities)
        return PresentationValue.TextValue(
            translationKey = ExampleMinecraftMessageKeys.POINTS,
            arguments = listOf(payment.amount.toString()),
        )
    }

    override fun entries(entries: List<WinSettlementDetailEntry>): PresentationValue.EntryListValue = FallbackWinSettlementDetailTextFormatter.entries(entries)
}
