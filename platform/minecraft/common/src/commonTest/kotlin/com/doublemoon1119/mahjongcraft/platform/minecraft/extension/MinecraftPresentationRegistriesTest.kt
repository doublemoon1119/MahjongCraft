package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.preparation.RoundPreparationDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplateRegistryImpl
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
import kotlin.test.assertTrue

/** 驗證 [MinecraftPresentationRegistries] 的集中凍結涵蓋所有具名 registry。 */
class MinecraftPresentationRegistriesTest {
    /** 驗證 [MinecraftPresentationRegistries.freezeAll] 會凍結每一個 Minecraft 呈現 registry。 */
    @Test
    fun `freeze all freezes every Minecraft presentation registry`() {
        val registries = MinecraftPresentationRegistries(
            tileAssetRegistry = MinecraftTileAssetRegistryImpl(),
            tileDisplayNameRegistry = TileDisplayNameRegistryImpl(),
            tileEmojiRegistry = TileEmojiRegistryImpl(),
            tileLabelRegistry = TileLabelRegistryImpl(),
            gameActionVocabularyRegistry = GameActionVocabularyRegistryImpl(),
            decisionStatusDisplayNameRegistry = DecisionStatusDisplayNameRegistryImpl(),
            gameActionSoundPresentationRegistry = GameActionSoundPresentationRegistryImpl(),
            exhaustiveDrawReasonDisplayNameRegistry = ExhaustiveDrawReasonDisplayNameRegistryImpl(),
            roundPreparationDisplayNameRegistry = RoundPreparationDisplayNameRegistryImpl(),
            roundInfoLineDisplayRegistry = RoundInfoLineDisplayRegistryImpl(),
            tablePropDescriberRegistry = TablePropDescriberRegistryImpl(),
            winCelebrationShowcaseRegistry = WinCelebrationShowcaseRegistryImpl(),
            winSettlementTemplateRegistry = WinSettlementPresentationTemplateRegistryImpl(),
            matchSettlementTemplateRegistry = MatchSettlementPresentationTemplateRegistryImpl(),
            aiStrategyDisplayNameRegistry = AiStrategyDisplayNameRegistryImpl(),
            ruleModuleDisplayNameRegistry = RuleModuleDisplayNameRegistryImpl(),
            playerPortraitSourceRegistry = PlayerPortraitSourceRegistryImpl(),
            publicPlayerIndicatorDisplayRegistry = PublicPlayerIndicatorDisplayRegistryImpl(),
            roomMemberAppearanceSourceRegistry = RoomMemberAppearanceSourceRegistryImpl(),
            gameConfigPresentationRegistry = GameConfigPresentationRegistryImpl(),
        )

        registries.freezeAll()

        assertTrue(registries.tileAssetRegistry.isFrozen)
        assertTrue(registries.tileDisplayNameRegistry.isFrozen)
        assertTrue(registries.tileEmojiRegistry.isFrozen)
        assertTrue(registries.tileLabelRegistry.isFrozen)
        assertTrue(registries.gameActionVocabularyRegistry.isFrozen)
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
    }
}
