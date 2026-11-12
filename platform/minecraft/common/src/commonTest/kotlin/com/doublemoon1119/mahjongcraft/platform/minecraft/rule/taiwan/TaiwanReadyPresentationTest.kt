package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.judgment.WaitingTileAvailabilityIds
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanGameAction
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPublicIndicatorIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanReadyKind
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundPresentationRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoArgument
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** 驗證台灣麻將宣告聽牌、連莊標示與局況資訊的登記。 */
class TaiwanReadyPresentationTest {
    /** 宣告聽牌有按鈕名稱、說明與語音；三種宣告與連莊都有標示文字。 */
    @Test
    fun `declare ready has a label, a voice and indicator wording`() {
        val vocabulary = GameActionVocabularyRegistryImpl().also(BundledTaiwanMinecraftExtension::registerGameActionVocabulary)
        val sounds = GameActionSoundPresentationRegistryImpl().also(BundledTaiwanMinecraftExtension::registerGameActionSounds)
        val indicators = PublicPlayerIndicatorDisplayRegistryImpl().also(BundledTaiwanMinecraftExtension::registerPublicPlayerIndicatorDisplays)

        val label = assertNotNull(vocabulary.find(BuiltInRuleModuleIds.TAIWAN, TaiwanGameAction.DeclareReady.id))
        assertNotNull(label.descriptionKey)
        assertEquals(
            TaiwanGameActionVoiceSoundIds.READY,
            sounds.find(BuiltInRuleModuleIds.TAIWAN, GameAction.Extension(TaiwanGameAction.DeclareReady))?.soundId,
        )
        TaiwanReadyKind.entries.forEach { kind -> assertNotNull(indicators.find(kind.indicatorId), "$kind") }
        assertEquals("mahjongcraft.taiwan.indicator.dealer_streak", indicators.find(TaiwanPublicIndicatorIds.DEALER_STREAK)?.translationKey)
    }

    /** 局況資訊依序是圈風與局數、連莊一次以上時的連莊次數，以及活牌剩餘張數；每一行都有顯示文字。 */
    @Test
    fun `round info lists the round, the streak only when there is one and the wall`() {
        val registry = RoundInfoLineDisplayRegistryImpl().also(BundledTaiwanMinecraftExtension::registerRoundInfoPresentations)
        val state = FakeTableStateFactory.create(config = TaiwanRuleConfig(), comboCount = 3)

        val lines = registry.buildLines(BuiltInRuleModuleIds.TAIWAN, state)
        val firstDealership = registry.buildLines(BuiltInRuleModuleIds.TAIWAN, state.copy(comboCount = 0))

        assertEquals(listOf(TAIWAN_ROUND_TITLE_LINE, TAIWAN_DEALER_STREAK_LINE, TAIWAN_WALL_REMAINING_LINE), lines.map { it.key })
        assertEquals(listOf(RoundInfoArgument.Number(3)), lines[1].args)
        assertEquals(listOf(TAIWAN_ROUND_TITLE_LINE, TAIWAN_WALL_REMAINING_LINE), firstDealership.map { it.key })
        lines.forEach { line -> assertNotNull(registry.find(line.key), line.key) }
    }

    /** 牌數行用共用的中立用語；可以胡牌的狀態改用台麻的胡牌說法。 */
    @Test
    fun `wall line uses neutral wording and win availability uses taiwan wording`() {
        val roundInfo = RoundInfoLineDisplayRegistryImpl().also(BundledTaiwanMinecraftExtension::registerRoundInfoPresentations)
        val statuses = DecisionStatusDisplayNameRegistryImpl().also(BundledTaiwanMinecraftExtension::registerDecisionStatusDisplayNames)

        assertEquals(MinecraftMessageKeys.ROUND_INFO_WALL_REMAINING, roundInfo.find(TAIWAN_WALL_REMAINING_LINE)?.translationKey)
        assertEquals(TaiwanTranslationKeys.HUD_WIN_AVAILABLE, statuses.find(BuiltInRuleModuleIds.TAIWAN, WaitingTileAvailabilityIds.WIN_AVAILABLE))
    }
}
