package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHistoryDiscardMarkerIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiMatchEndReasonIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawSettlementStatusLabels
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundOutcomeDisplayNameRegistryImpl
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證日麻在歷史畫面需要的呈現由日麻 extension 登記。 */
class BundledRiichiMinecraftExtensionHistoryTest {
    /** 立直宣告的牌橫擺並以「立直」為提示。 */
    @Test
    fun `riichi declaration marker is sideways`() {
        val registry = HistoryDiscardMarkerDisplayRegistryImpl().apply { BundledRiichiMinecraftExtension.registerHistoryDiscardMarkerDisplays(this) }

        assertEquals(
            expected = HistoryDiscardMarkerDisplay(labelTranslationKey = RiichiTranslationKeys.INDICATOR_RIICHI, sideways = true),
            actual = registry.find(RiichiHistoryDiscardMarkerIds.RIICHI_DECLARED),
        )
    }

    /** 流局滿貫有自己的結果名稱。 */
    @Test
    fun `nagashi mangan has an outcome name`() {
        val registry = RoundOutcomeDisplayNameRegistryImpl().apply { BundledRiichiMinecraftExtension.registerRoundOutcomeDisplayNames(this) }

        assertEquals(RiichiTranslationKeys.OUTCOME_NAGASHI_MANGAN, registry.find(RiichiRoundOutcomeIds.NAGASHI_MANGAN))
    }

    /** 最後莊家第一終局是日麻自訂的整場終止原因，由日麻登記名稱。 */
    @Test
    fun `dealer top finish has an outcome name`() {
        val registry = RoundOutcomeDisplayNameRegistryImpl().apply { BundledRiichiMinecraftExtension.registerRoundOutcomeDisplayNames(this) }

        assertEquals(RiichiTranslationKeys.OUTCOME_DEALER_TOP_FINISH, registry.find(RiichiMatchEndReasonIds.DEALER_TOP_FINISH))
    }

    /** 一般荒牌流局的得分者顯示聽牌，其他人顯示未聽。 */
    @Test
    fun `normal exhaustive draw labels tenpai and noten`() {
        val registry = ExhaustiveDrawReasonDisplayNameRegistryImpl().apply { BundledRiichiMinecraftExtension.registerExhaustiveDrawReasonDisplayNames(this) }

        assertEquals(
            expected = ExhaustiveDrawSettlementStatusLabels(
                beneficiaryTranslationKey = RiichiTranslationKeys.EXHAUSTIVE_DRAW_TENPAI,
                othersTranslationKey = RiichiTranslationKeys.EXHAUSTIVE_DRAW_NOTEN,
            ),
            actual = registry.findSettlementStatusLabels(RiichiExhaustiveDrawReason.Normal.id),
        )
    }
}
