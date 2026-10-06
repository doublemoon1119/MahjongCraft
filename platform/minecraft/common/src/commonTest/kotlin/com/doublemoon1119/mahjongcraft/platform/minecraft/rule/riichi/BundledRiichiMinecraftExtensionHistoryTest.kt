package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiHistoryDiscardMarkerIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawSettlementStatusLabels
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundOutcomeDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證日麻在歷史畫面需要的呈現由日麻 extension 登記。 */
class BundledRiichiMinecraftExtensionHistoryTest {
    /** 立直宣告的牌橫擺並以「立直」為提示。 */
    @Test
    fun `riichi declaration marker is sideways`() {
        val registry = HistoryDiscardMarkerDisplayRegistryImpl().apply { BundledRiichiMinecraftExtension.registerHistoryDiscardMarkerDisplays(this) }

        assertEquals(
            HistoryDiscardMarkerDisplay(labelTranslationKey = MinecraftMessageKeys.PLAYER_INDICATOR_RIICHI, sideways = true),
            registry.find(RiichiHistoryDiscardMarkerIds.RIICHI_DECLARED),
        )
    }

    /** 流局滿貫有自己的結果名稱。 */
    @Test
    fun `nagashi mangan has an outcome name`() {
        val registry = RoundOutcomeDisplayNameRegistryImpl().apply { BundledRiichiMinecraftExtension.registerRoundOutcomeDisplayNames(this) }

        assertEquals(MinecraftHistoryScreenKeys.ROUND_OUTCOME_NAGASHI_MANGAN, registry.find(RiichiRoundOutcomeIds.NAGASHI_MANGAN))
    }

    /** 一般荒牌流局的得分者顯示聽牌，其他人顯示未聽。 */
    @Test
    fun `normal exhaustive draw labels tenpai and noten`() {
        val registry = ExhaustiveDrawReasonDisplayNameRegistryImpl().apply { BundledRiichiMinecraftExtension.registerExhaustiveDrawReasonDisplayNames(this) }

        assertEquals(
            ExhaustiveDrawSettlementStatusLabels(
                beneficiaryTranslationKey = MinecraftMessageKeys.EXHAUSTIVE_DRAW_SETTLEMENT_STATUS_TENPAI,
                othersTranslationKey = MinecraftMessageKeys.EXHAUSTIVE_DRAW_SETTLEMENT_STATUS_NOTEN,
            ),
            registry.findSettlementStatusLabels(RiichiExhaustiveDrawReason.Normal.id),
        )
    }
}
