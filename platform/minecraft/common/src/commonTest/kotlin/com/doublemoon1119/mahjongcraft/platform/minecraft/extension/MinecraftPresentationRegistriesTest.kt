package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolverRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolverRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.preparation.RoundPreparationDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawSettlementStatusLabels
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawSettlementStatusLabelsDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplateRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundOutcomeDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundPresentationRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropDescriberRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證 [MinecraftPresentationRegistries] 的集中凍結涵蓋所有具名 registry。 */
class MinecraftPresentationRegistriesTest {
    /** 驗證 [MinecraftPresentationRegistries.freezeAll] 會凍結每一個 Minecraft 呈現 registry。 */
    @Test
    fun `freeze all freezes every Minecraft presentation registry`() {
        val registries = createRegistries()

        registries.freezeAll()

        assertTrue(registries.tileAssetRegistry.isFrozen)
        assertTrue(registries.tileDisplayNameRegistry.isFrozen)
        assertTrue(registries.tileEmojiRegistry.isFrozen)
        assertTrue(registries.tileLabelRegistry.isFrozen)
        assertTrue(registries.gameActionVocabularyRegistry.isFrozen)
        assertTrue(registries.automaticControlDisplayRegistry.isFrozen)
        assertTrue(registries.gameActionSoundPresentationRegistry.isFrozen)
        assertTrue(registries.exhaustiveDrawReasonDisplayNameRegistry.isFrozen)
        assertTrue(registries.roundPreparationDisplayNameRegistry.isFrozen)
        assertTrue(registries.roundInfoLineDisplayRegistry.isFrozen)
        assertTrue(registries.winCelebrationShowcaseRegistry.isFrozen)
        assertTrue(registries.winSettlementTemplateRegistry.isFrozen)
        assertTrue(registries.aiStrategyDisplayNameRegistry.isFrozen)
        assertTrue(registries.ruleModuleDisplayNameRegistry.isFrozen)
        assertTrue(registries.playerPortraitSourceRegistry.isFrozen)
        assertTrue(registries.publicPlayerIndicatorDisplayRegistry.isFrozen)
        assertTrue(registries.roomMemberAppearanceSourceRegistry.isFrozen)
        assertTrue(registries.gameConfigPresentationRegistry.isFrozen)
        assertTrue(registries.ruleCatalogueRegistry.isFrozen)
        assertTrue(registries.gameAchievementResolverRegistry.isFrozen)
    }

    /** 聚合快照會分開回報流局原因名稱與玩家結算身分用語的新增項目。 */
    @Test
    fun `registration snapshot reports draw status labels independently from reason names`() {
        val reason = ExhaustiveDrawReasonDisplayNameDefinition("example:draw", "example.draw")
        val labels = ExhaustiveDrawSettlementStatusLabelsDefinition(
            reasonId = "example:draw",
            labels = ExhaustiveDrawSettlementStatusLabels("example.tenpai", "example.noten"),
        )
        val reasonOnly = ExhaustiveDrawReasonDisplayNameRegistryImpl().apply { register(reason) }
        val labelsOnly = ExhaustiveDrawReasonDisplayNameRegistryImpl().apply { register(labels) }
        val reasonBaseline = createRegistries(reasonOnly).registrationSnapshot()
        val labelsBaseline = createRegistries(labelsOnly).registrationSnapshot()
        reasonOnly.register(labels)
        labelsOnly.register(reason)

        assertEquals(
            listOf(
                MinecraftPresentationRegistrationCategory(
                    id = MinecraftExtensionRegistrationCategoryIds.EXHAUSTIVE_DRAW_SETTLEMENT_STATUS_LABEL,
                    displayName = "Exhaustive Draw Settlement Status Label",
                    registrationIds = listOf("example:draw"),
                ),
            ),
            reasonBaseline.additionsSince(createRegistries(reasonOnly).registrationSnapshot()),
        )
        assertEquals(
            listOf(
                MinecraftPresentationRegistrationCategory(
                    id = MinecraftExtensionRegistrationCategoryIds.EXHAUSTIVE_DRAW_REASON_DISPLAY_NAME,
                    displayName = "Exhaustive Draw Reason Display Name",
                    registrationIds = listOf("example:draw"),
                ),
            ),
            labelsBaseline.additionsSince(createRegistries(labelsOnly).registrationSnapshot()),
        )
    }

    /** 建立測試用的完整 presentation registry 集合。 */
    private fun createRegistries(
        exhaustiveDrawReasonDisplayNameRegistry: ExhaustiveDrawReasonDisplayNameRegistry =
            ExhaustiveDrawReasonDisplayNameRegistryImpl(),
    ): MinecraftPresentationRegistries = MinecraftPresentationRegistries(
        tileAssetRegistry = MinecraftTileAssetRegistryImpl(),
        tileDisplayNameRegistry = TileDisplayNameRegistryImpl(),
        tileEmojiRegistry = TileEmojiRegistryImpl(),
        tileLabelRegistry = TileLabelRegistryImpl(),
        ruleCatalogueRegistry = RuleCatalogueRegistryImpl(),
        gameActionVocabularyRegistry = GameActionVocabularyRegistryImpl(),
        automaticControlDisplayRegistry = AutomaticControlDisplayRegistryImpl(),
        decisionStatusDisplayNameRegistry = DecisionStatusDisplayNameRegistryImpl(),
        gameActionSoundPresentationRegistry = GameActionSoundPresentationRegistryImpl(),
        exhaustiveDrawReasonDisplayNameRegistry = exhaustiveDrawReasonDisplayNameRegistry,
        roundOutcomeDisplayNameRegistry = RoundOutcomeDisplayNameRegistryImpl(),
        historyDiscardMarkerDisplayRegistry = HistoryDiscardMarkerDisplayRegistryImpl(),
        roundPreparationDisplayNameRegistry = RoundPreparationDisplayNameRegistryImpl(),
        roundInfoLineDisplayRegistry = RoundInfoLineDisplayRegistryImpl(),
        tablePropDescriberRegistry = TablePropDescriberRegistryImpl(),
        winCelebrationShowcaseRegistry = WinCelebrationShowcaseRegistryImpl(),
        winSettlementTemplateRegistry = WinSettlementPresentationTemplateRegistryImpl(),
        matchSettlementTemplateRegistry = MatchSettlementPresentationTemplateRegistryImpl(),
        aiStrategyDisplayNameRegistry = AiStrategyDisplayNameRegistryImpl(),
        ruleModuleDisplayNameRegistry = RuleModuleDisplayNameRegistryImpl(),
        playerPortraitSourceRegistry = PlayerPortraitSourceRegistryImpl(failurePolicy = ExtensionFailurePolicy.Default),
        publicPlayerIndicatorDisplayRegistry = PublicPlayerIndicatorDisplayRegistryImpl(),
        roomMemberAppearanceSourceRegistry = RoomMemberAppearanceSourceRegistryImpl(failurePolicy = ExtensionFailurePolicy.Default),
        gameConfigPresentationRegistry = GameConfigPresentationRegistryImpl(
            RoundPreparationResolverRegistry(),
        ),
        gameAchievementResolverRegistry = GameAchievementResolverRegistryImpl(),
    )
}
