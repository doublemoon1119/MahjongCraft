package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolverRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistry
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

/**
 * 集中保存 Minecraft extension bootstrap 使用的強型別呈現 registry。
 *
 * 一般 renderer、screen 與 service 仍應只依賴實際需要的單一 registry；這個集合只用於 extension 註冊與
 * 統一凍結邊界。
 *
 * @property tileAssetRegistry 牌種 Minecraft asset registry。
 * @property ruleCatalogueRegistry 規則說明目錄 registry。
 * @property tileDisplayNameRegistry 牌種顯示名稱 registry。
 * @property tileEmojiRegistry 牌面 emoji registry。
 * @property tileLabelRegistry 牌面輔助標籤 registry。
 * @property gameActionVocabularyRegistry 遊戲動作顯示名稱 registry。
 * @property automaticControlDisplayRegistry 自動操作顯示資料 registry。
 * @property decisionStatusDisplayNameRegistry 決策狀態顯示名稱 registry。
 * @property gameActionSoundPresentationRegistry 遊戲動作音效呈現 registry。
 * @property exhaustiveDrawReasonDisplayNameRegistry 流局原因顯示名稱 registry。
 * @property roundOutcomeDisplayNameRegistry 規則特殊本局結果顯示名稱 registry。
 * @property historyDiscardMarkerDisplayRegistry 歷史牌河公開標記呈現 registry。
 * @property roundPreparationDisplayNameRegistry 開局準備顯示名稱 registry。
 * @property roundInfoLineDisplayRegistry 局況資訊行顯示 registry。
 * @property tablePropDescriberRegistry 規則桌面物件描述 registry。
 * @property winCelebrationShowcaseRegistry 胡牌 showcase registry。
 * @property winSettlementTemplateRegistry 胡牌結算模板 registry。
 * @property matchSettlementTemplateRegistry 終局結算模板 registry。
 * @property aiStrategyDisplayNameRegistry AI 策略顯示名稱 registry。
 * @property ruleModuleDisplayNameRegistry 規則模組顯示名稱 registry。
 * @property playerPortraitSourceRegistry 玩家頭像來源 registry。
 * @property publicPlayerIndicatorDisplayRegistry 公開玩家 indicator 顯示 registry。
 * @property roomMemberAppearanceSourceRegistry 房間成員外觀來源 registry。
 * @property gameConfigPresentationRegistry 遊戲設定呈現 registry。
 * @property gameAchievementResolverRegistry 規則專屬對局成果判定 registry。
 */
class MinecraftPresentationRegistries(
    // 牌面呈現
    val tileAssetRegistry: MinecraftTileAssetRegistry,
    val tileDisplayNameRegistry: TileDisplayNameRegistry,
    val tileEmojiRegistry: TileEmojiRegistry,
    val tileLabelRegistry: TileLabelRegistry,
    val ruleCatalogueRegistry: RuleCatalogueRegistry,
    // 動作與局況呈現
    val gameActionVocabularyRegistry: GameActionVocabularyRegistry,
    val automaticControlDisplayRegistry: AutomaticControlDisplayRegistry,
    val decisionStatusDisplayNameRegistry: DecisionStatusDisplayNameRegistry,
    val gameActionSoundPresentationRegistry: GameActionSoundPresentationRegistry,
    val exhaustiveDrawReasonDisplayNameRegistry: ExhaustiveDrawReasonDisplayNameRegistry,
    val roundOutcomeDisplayNameRegistry: RoundOutcomeDisplayNameRegistry,
    val historyDiscardMarkerDisplayRegistry: HistoryDiscardMarkerDisplayRegistry,
    val roundPreparationDisplayNameRegistry: RoundPreparationDisplayNameRegistry,
    val roundInfoLineDisplayRegistry: RoundInfoLineDisplayRegistry,
    val tablePropDescriberRegistry: TablePropDescriberRegistry,
    // 胡牌與結算呈現
    val winCelebrationShowcaseRegistry: WinCelebrationShowcaseRegistry,
    val winSettlementTemplateRegistry: WinSettlementPresentationTemplateRegistry,
    val matchSettlementTemplateRegistry: MatchSettlementPresentationTemplateRegistry,
    // 玩家與房間呈現
    val aiStrategyDisplayNameRegistry: AiStrategyDisplayNameRegistry,
    val ruleModuleDisplayNameRegistry: RuleModuleDisplayNameRegistry,
    val playerPortraitSourceRegistry: PlayerPortraitSourceRegistry,
    val publicPlayerIndicatorDisplayRegistry: PublicPlayerIndicatorDisplayRegistry,
    val roomMemberAppearanceSourceRegistry: RoomMemberAppearanceSourceRegistry,
    val gameConfigPresentationRegistry: GameConfigPresentationRegistry,
    // 對局成果
    val gameAchievementResolverRegistry: GameAchievementResolverRegistry,
) {
    /**
     * 取得目前所有 Minecraft presentation registry 的不可變診斷快照。
     *
     * @return 各 registry 分類及當前註冊 key 的快照。
     */
    fun registrationSnapshot(): MinecraftPresentationRegistrationSnapshot = MinecraftPresentationRegistrationSnapshot(
        listOf(
            snapshotCategory(MinecraftExtensionRegistrationCategoryIds.TILE_ASSET, "Tile Asset", tileAssetRegistry.registrationKeys),
            snapshotCategory(MinecraftExtensionRegistrationCategoryIds.TILE_SET_APPEARANCE, "Tile Set Appearance", tileAssetRegistry.appearanceRegistrationKeys),
            snapshotCategory(MinecraftExtensionRegistrationCategoryIds.RULE_TILE_APPEARANCE, "Rule Tile Appearance", tileAssetRegistry.ruleAppearanceBindingKeys),
            snapshotCategory(MinecraftExtensionRegistrationCategoryIds.TILE_DISPLAY_NAME, "Tile Display Name", tileDisplayNameRegistry.registrationKeys),
            snapshotCategory(MinecraftExtensionRegistrationCategoryIds.TILE_EMOJI, "Tile Emoji", tileEmojiRegistry.registrationKeys),
            snapshotCategory(MinecraftExtensionRegistrationCategoryIds.TILE_LABEL, "Tile Label", tileLabelRegistry.registrationKeys),
            snapshotCategory(MinecraftExtensionRegistrationCategoryIds.RULE_CATALOGUE, "Rule Catalogue", ruleCatalogueRegistry.registrationKeys),
            snapshotCategory(MinecraftExtensionRegistrationCategoryIds.GAME_ACTION_VOCABULARY, "Game Action Vocabulary", gameActionVocabularyRegistry.registrationKeys),
            snapshotCategory(MinecraftExtensionRegistrationCategoryIds.AUTOMATIC_CONTROL_DISPLAY, "Automatic Control Display", automaticControlDisplayRegistry.registrationKeys),
            snapshotCategory(MinecraftExtensionRegistrationCategoryIds.DECISION_STATUS_DISPLAY_NAME, "Decision Status Display Name", decisionStatusDisplayNameRegistry.registrationKeys),
            snapshotCategory(MinecraftExtensionRegistrationCategoryIds.GAME_ACTION_SOUND, "Game Action Sound", gameActionSoundPresentationRegistry.registrationKeys),
            snapshotCategory(MinecraftExtensionRegistrationCategoryIds.EXHAUSTIVE_DRAW_REASON_DISPLAY_NAME, "Exhaustive Draw Reason Display Name", exhaustiveDrawReasonDisplayNameRegistry.registrationKeys),
            snapshotCategory(MinecraftExtensionRegistrationCategoryIds.ROUND_OUTCOME_DISPLAY_NAME, "Round Outcome Display Name", roundOutcomeDisplayNameRegistry.registrationKeys),
            snapshotCategory(MinecraftExtensionRegistrationCategoryIds.HISTORY_DISCARD_MARKER_DISPLAY, "History Discard Marker Display", historyDiscardMarkerDisplayRegistry.registrationKeys),
            snapshotCategory(MinecraftExtensionRegistrationCategoryIds.ROUND_PREPARATION_DISPLAY_NAME, "Round Preparation Display Name", roundPreparationDisplayNameRegistry.registrationKeys),
            snapshotCategory(MinecraftExtensionRegistrationCategoryIds.ROUND_INFO_LINE_DISPLAY, "Round Info Line Display", roundInfoLineDisplayRegistry.registrationKeys),
            snapshotCategory(MinecraftExtensionRegistrationCategoryIds.TABLE_PROP_DESCRIBER, "Table Prop Describer", tablePropDescriberRegistry.registrationKeys),
            snapshotCategory(MinecraftExtensionRegistrationCategoryIds.WIN_CELEBRATION_SHOWCASE, "Win Celebration Showcase", winCelebrationShowcaseRegistry.registrationKeys),
            snapshotCategory(MinecraftExtensionRegistrationCategoryIds.WIN_SETTLEMENT_PRESENTATION, "Win Settlement Presentation", winSettlementTemplateRegistry.registrationKeys),
            snapshotCategory(MinecraftExtensionRegistrationCategoryIds.MATCH_SETTLEMENT_TEMPLATE, "Match Settlement Template", matchSettlementTemplateRegistry.registrationKeys),
            snapshotCategory(MinecraftExtensionRegistrationCategoryIds.AI_STRATEGY_DISPLAY_NAME, "AI Strategy Display Name", aiStrategyDisplayNameRegistry.registrationKeys),
            snapshotCategory(MinecraftExtensionRegistrationCategoryIds.RULE_MODULE_DISPLAY_NAME, "Rule Module Display Name", ruleModuleDisplayNameRegistry.registrationKeys),
            snapshotCategory(MinecraftExtensionRegistrationCategoryIds.PLAYER_PORTRAIT_SOURCE, "Player Portrait Source", playerPortraitSourceRegistry.registrationKeys),
            snapshotCategory(MinecraftExtensionRegistrationCategoryIds.PUBLIC_PLAYER_INDICATOR_DISPLAY, "Public Player Indicator Display", publicPlayerIndicatorDisplayRegistry.registrationKeys),
            snapshotCategory(MinecraftExtensionRegistrationCategoryIds.ROOM_MEMBER_APPEARANCE_SOURCE, "Room Member Appearance Source", roomMemberAppearanceSourceRegistry.registrationKeys),
            snapshotCategory(MinecraftExtensionRegistrationCategoryIds.GAME_CONFIG_PRESENTATION, "Game Config Presentation", gameConfigPresentationRegistry.registrationKeys),
            snapshotCategory(MinecraftExtensionRegistrationCategoryIds.GAME_ACHIEVEMENT_RESOLVER, "Game Achievement Resolver", gameAchievementResolverRegistry.registrationKeys),
        ),
    )

    /** 依固定分類順序凍結集合內所有 registry。 */
    fun freezeAll() {
        tileAssetRegistry.freeze()
        tileDisplayNameRegistry.freeze()
        tileEmojiRegistry.freeze()
        tileLabelRegistry.freeze()
        ruleCatalogueRegistry.freeze()
        gameActionVocabularyRegistry.freeze()
        automaticControlDisplayRegistry.freeze()
        decisionStatusDisplayNameRegistry.freeze()
        gameActionSoundPresentationRegistry.freeze()
        exhaustiveDrawReasonDisplayNameRegistry.freeze()
        roundOutcomeDisplayNameRegistry.freeze()
        historyDiscardMarkerDisplayRegistry.freeze()
        roundPreparationDisplayNameRegistry.freeze()
        roundInfoLineDisplayRegistry.freeze()
        tablePropDescriberRegistry.freeze()
        winCelebrationShowcaseRegistry.freeze()
        winSettlementTemplateRegistry.freeze()
        matchSettlementTemplateRegistry.freeze()
        aiStrategyDisplayNameRegistry.freeze()
        ruleModuleDisplayNameRegistry.freeze()
        playerPortraitSourceRegistry.freeze()
        publicPlayerIndicatorDisplayRegistry.freeze()
        roomMemberAppearanceSourceRegistry.freeze()
        gameConfigPresentationRegistry.freeze()
        gameAchievementResolverRegistry.freeze()
    }
}

/**
 * 建立單一 Minecraft presentation registry 的診斷快照類別。
 *
 * @param id registry 的穩定分類 ID。
 * @param displayName 診斷畫面使用的分類名稱。
 * @param registrationKeys registry 目前登記的 key。
 * @return 保存分類資訊與 key 快照的物件。
 */
private fun snapshotCategory(
    id: String,
    displayName: String,
    registrationKeys: Iterable<String>,
): MinecraftPresentationRegistrationSnapshotCategory = MinecraftPresentationRegistrationSnapshotCategory(
    id,
    displayName,
    registrationKeys.toSet(),
)
