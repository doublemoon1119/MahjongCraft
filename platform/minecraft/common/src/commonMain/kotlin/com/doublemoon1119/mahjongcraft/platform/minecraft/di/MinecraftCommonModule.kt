package com.doublemoon1119.mahjongcraft.platform.minecraft.di

import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolverRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftPresentationRegistries
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.preparation.RoundPreparationDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundOutcomeDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropDescriberRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Minecraft loader 與版本無關、client／server 共用的 Koin 定義。
 *
 * 掃描共用服務上的註解，並以明確 provider 將同一份 registry 組成 bootstrap 集合。
 * server 專用狀態由 [MinecraftServerModule] 提供；共用名稱來源直接由本模組綁定，
 * 原生文字轉換留在對應平台 adapter，不為純委派另外建立 loader registry。
 */
@Module
@ComponentScan("com.doublemoon1119.mahjongcraft.platform.minecraft")
class MinecraftCommonModule {
    /**
     * 將 Koin 管理的 Minecraft presentation registry single 組成 bootstrap 專用集合。
     *
     * @param gameActionVocabularyRegistry 各規則動作用語來源。
     * @param automaticControlDisplayRegistry 自動操作顯示資料來源。
     * @param decisionStatusDisplayNameRegistry 捨牌分析狀態名稱來源。
     * @param aiStrategyDisplayNameRegistry AI 策略名稱來源。
     * @param playerPortraitSourceRegistry 玩家頭像來源。
     * @param publicPlayerIndicatorDisplayRegistry 公開玩家指示器顯示來源。
     * @param roundPreparationDisplayNameRegistry 開局準備名稱來源。
     * @param gameConfigPresentationRegistry 房間設定呈現來源。
     * @param roomMemberAppearanceSourceRegistry 房間成員外觀來源。
     * @param ruleModuleDisplayNameRegistry 規則模組名稱來源。
     * @param exhaustiveDrawReasonDisplayNameRegistry 流局原因名稱來源。
     * @param roundOutcomeDisplayNameRegistry 本局結果名稱來源。
     * @param historyDiscardMarkerDisplayRegistry 歷史牌河標記呈現來源。
     * @param matchSettlementTemplateRegistry 終局結算模板來源。
     * @param winSettlementTemplateRegistry 胡牌結算模板來源。
     * @param winCelebrationShowcaseRegistry 胡牌 showcase 來源。
     * @param gameActionSoundPresentationRegistry 遊戲動作音效來源。
     * @param roundInfoLineDisplayRegistry 局況顯示行來源。
     * @param tablePropDescriberRegistry 桌上物件描述來源。
     * @param tileAssetRegistry 牌面資產來源。
     * @param tileDisplayNameRegistry 牌面名稱來源。
     * @param tileEmojiRegistry 牌面 emoji 來源。
     * @param tileLabelRegistry 牌面輔助標籤來源。
     * @param ruleCatalogueRegistry 規則說明目錄來源。
     * @param gameAchievementResolverRegistry 規則專屬成果判定來源。
     * @return 供 extension bootstrap 註冊及凍結使用的呈現 registry 集合。
     */
    @Single
    fun provideMinecraftPresentationRegistries(
        gameActionVocabularyRegistry: GameActionVocabularyRegistry,
        automaticControlDisplayRegistry: AutomaticControlDisplayRegistry,
        decisionStatusDisplayNameRegistry: DecisionStatusDisplayNameRegistry,
        aiStrategyDisplayNameRegistry: AiStrategyDisplayNameRegistry,
        playerPortraitSourceRegistry: PlayerPortraitSourceRegistry,
        publicPlayerIndicatorDisplayRegistry: PublicPlayerIndicatorDisplayRegistry,
        roundPreparationDisplayNameRegistry: RoundPreparationDisplayNameRegistry,
        gameConfigPresentationRegistry: GameConfigPresentationRegistry,
        roomMemberAppearanceSourceRegistry: RoomMemberAppearanceSourceRegistry,
        ruleModuleDisplayNameRegistry: RuleModuleDisplayNameRegistry,
        exhaustiveDrawReasonDisplayNameRegistry: ExhaustiveDrawReasonDisplayNameRegistry,
        roundOutcomeDisplayNameRegistry: RoundOutcomeDisplayNameRegistry,
        historyDiscardMarkerDisplayRegistry: HistoryDiscardMarkerDisplayRegistry,
        matchSettlementTemplateRegistry: MatchSettlementPresentationTemplateRegistry,
        winSettlementTemplateRegistry: WinSettlementPresentationTemplateRegistry,
        winCelebrationShowcaseRegistry: WinCelebrationShowcaseRegistry,
        gameActionSoundPresentationRegistry: GameActionSoundPresentationRegistry,
        roundInfoLineDisplayRegistry: RoundInfoLineDisplayRegistry,
        tablePropDescriberRegistry: TablePropDescriberRegistry,
        tileAssetRegistry: MinecraftTileAssetRegistry,
        tileDisplayNameRegistry: TileDisplayNameRegistry,
        tileEmojiRegistry: TileEmojiRegistry,
        tileLabelRegistry: TileLabelRegistry,
        ruleCatalogueRegistry: RuleCatalogueRegistry,
        gameAchievementResolverRegistry: GameAchievementResolverRegistry,
    ): MinecraftPresentationRegistries = MinecraftPresentationRegistries(
        tileAssetRegistry = tileAssetRegistry,
        tileDisplayNameRegistry = tileDisplayNameRegistry,
        tileEmojiRegistry = tileEmojiRegistry,
        tileLabelRegistry = tileLabelRegistry,
        ruleCatalogueRegistry = ruleCatalogueRegistry,
        gameActionVocabularyRegistry = gameActionVocabularyRegistry,
        automaticControlDisplayRegistry = automaticControlDisplayRegistry,
        decisionStatusDisplayNameRegistry = decisionStatusDisplayNameRegistry,
        gameActionSoundPresentationRegistry = gameActionSoundPresentationRegistry,
        exhaustiveDrawReasonDisplayNameRegistry = exhaustiveDrawReasonDisplayNameRegistry,
        roundOutcomeDisplayNameRegistry = roundOutcomeDisplayNameRegistry,
        historyDiscardMarkerDisplayRegistry = historyDiscardMarkerDisplayRegistry,
        roundPreparationDisplayNameRegistry = roundPreparationDisplayNameRegistry,
        roundInfoLineDisplayRegistry = roundInfoLineDisplayRegistry,
        tablePropDescriberRegistry = tablePropDescriberRegistry,
        winCelebrationShowcaseRegistry = winCelebrationShowcaseRegistry,
        winSettlementTemplateRegistry = winSettlementTemplateRegistry,
        matchSettlementTemplateRegistry = matchSettlementTemplateRegistry,
        aiStrategyDisplayNameRegistry = aiStrategyDisplayNameRegistry,
        ruleModuleDisplayNameRegistry = ruleModuleDisplayNameRegistry,
        playerPortraitSourceRegistry = playerPortraitSourceRegistry,
        publicPlayerIndicatorDisplayRegistry = publicPlayerIndicatorDisplayRegistry,
        roomMemberAppearanceSourceRegistry = roomMemberAppearanceSourceRegistry,
        gameConfigPresentationRegistry = gameConfigPresentationRegistry,
        gameAchievementResolverRegistry = gameAchievementResolverRegistry,
    )
}
