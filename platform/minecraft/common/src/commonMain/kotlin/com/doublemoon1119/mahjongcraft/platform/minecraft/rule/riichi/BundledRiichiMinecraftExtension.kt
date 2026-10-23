package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiWinSettlementIds
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHistoryDiscardMarkerIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolverRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplayDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.achievement.RiichiGameAchievementResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.catalogue.RiichiCatalogueProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawSettlementStatusLabels
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawSettlementStatusLabelsDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundOutcomeDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundOutcomeDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementDetailTextFormatterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationFieldProviderDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementTextKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropDescriberRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.blackTextOnRedTile

/**
 * 日麻（四人與三人）的 Minecraft 呈現整合，與平台無關的日麻 extension 使用同一個 extension ID。
 *
 * 包含赤五的貼圖、名稱、表情與標籤、規則名稱與設定畫面、規則說明、成就、役滿演出、流局原因名稱、
 * 胡牌結算版面、動作用語、決策狀態、音效、資訊列、桌面物件說明、立直標示，以及歷史畫面的流局滿貫名稱與立直宣告牌。
 * 綁定到規則模組 ID 的項目透過 [RiichiFamilyMinecraftPresentation] 登記，四人與三人日麻各登記一次。
 */
object BundledRiichiMinecraftExtension : MinecraftMahjongExtension {
    override val id: String = MahjongCraftMetadata.id("riichi")

    override fun registerTileAssets(registry: MinecraftTileAssetRegistry) {
        RIICHI_FAMILY_RULE_MODULE_IDS.forEach { ruleModuleId -> RiichiFamilyMinecraftPresentation.registerTileAppearance(registry, ruleModuleId) }
        registry.register(MinecraftTileAssetDefinition(RiichiTileTypes.RED_FIVE_CHARACTER, "m5_red"))
        registry.register(MinecraftTileAssetDefinition(RiichiTileTypes.RED_FIVE_DOT, "p5_red"))
        registry.register(MinecraftTileAssetDefinition(RiichiTileTypes.RED_FIVE_BAMBOO, "s5_red"))
    }

    override fun registerTileDisplayNames(registry: TileDisplayNameRegistry) {
        registry.register(TileDisplayNameDefinition(RiichiTileTypes.RED_FIVE_CHARACTER, MinecraftMessageKeys.TILE_RED_FIVE_CHARACTER))
        registry.register(TileDisplayNameDefinition(RiichiTileTypes.RED_FIVE_DOT, MinecraftMessageKeys.TILE_RED_FIVE_DOT))
        registry.register(TileDisplayNameDefinition(RiichiTileTypes.RED_FIVE_BAMBOO, MinecraftMessageKeys.TILE_RED_FIVE_BAMBOO))
    }

    override fun registerRuleModuleDisplayNames(registry: RuleModuleDisplayNameRegistry) {
        registry.register(RuleModuleDisplayNameDefinition(BuiltInRuleModuleIds.RIICHI, MinecraftMessageKeys.RULE_MODULE_RIICHI))
        registry.register(RuleModuleDisplayNameDefinition(BuiltInRuleModuleIds.RIICHI_THREE_PLAYER, MinecraftMessageKeys.RULE_MODULE_RIICHI_THREE_PLAYER))
    }

    override fun registerTileEmojis(registry: TileEmojiRegistry) {
        registry.register(TileEmojiDefinition("m5_red", "🀬"))
        registry.register(TileEmojiDefinition("s5_red", "🀭"))
        registry.register(TileEmojiDefinition("p5_red", "🀮"))
    }

    /** 赤五牌面印刷成紅色，角落文字用黑色。 */
    override fun registerTileLabels(registry: TileLabelRegistry) {
        registry.register(TileLabelDefinition("m5_red", blackTextOnRedTile("5")))
        registry.register(TileLabelDefinition("s5_red", blackTextOnRedTile("5")))
        registry.register(TileLabelDefinition("p5_red", blackTextOnRedTile("5")))
    }

    override fun registerRuleCatalogues(registry: RuleCatalogueRegistry) {
        registry.register(RiichiCatalogueProvider())
        registry.register(
            RiichiCatalogueProvider(
                ruleModuleId = BuiltInRuleModuleIds.RIICHI_THREE_PLAYER,
                configClass = ThreePlayerRiichiRuleConfig::class,
                defaultConfig = ::ThreePlayerRiichiRuleConfig,
            ),
        )
    }

    override fun registerGameAchievementResolvers(registry: GameAchievementResolverRegistry) {
        RIICHI_FAMILY_RULE_MODULE_IDS.forEach { ruleModuleId -> registry.register(RiichiGameAchievementResolver(ruleModuleId)) }
    }

    override fun registerWinCelebrationShowcases(registry: WinCelebrationShowcaseRegistry) {
        riichiWinCelebrationShowcases().forEach(registry::register)
    }

    override fun registerExhaustiveDrawReasonDisplayNames(registry: ExhaustiveDrawReasonDisplayNameRegistry) {
        registry.register(ExhaustiveDrawReasonDisplayNameDefinition(RiichiExhaustiveDrawReason.Normal.id, MinecraftMessageKeys.EXHAUSTIVE_DRAW_REASON_NORMAL))
        registry.register(ExhaustiveDrawReasonDisplayNameDefinition(RiichiExhaustiveDrawReason.KyuushuKyuuhai.id, MinecraftMessageKeys.GAME_ACTION_KYUUSHU_KYUUHAI))
        registry.register(ExhaustiveDrawReasonDisplayNameDefinition(RiichiExhaustiveDrawReason.SuufonRenda.id, MinecraftMessageKeys.GAME_ACTION_SUUFON_RENDA))
        registry.register(ExhaustiveDrawReasonDisplayNameDefinition(RiichiExhaustiveDrawReason.SuukanNagare.id, MinecraftMessageKeys.GAME_ACTION_SUUKAN_NAGARE))
        registry.register(ExhaustiveDrawReasonDisplayNameDefinition(RiichiExhaustiveDrawReason.SuuchaRiichi.id, MinecraftMessageKeys.GAME_ACTION_SUUCHA_RIICHI))
        registry.register(ExhaustiveDrawReasonDisplayNameDefinition(RiichiExhaustiveDrawReason.SanchaHou.id, MinecraftMessageKeys.GAME_ACTION_SANCHA_HOU))
        registry.register(
            definition = ExhaustiveDrawSettlementStatusLabelsDefinition(
                reasonId = RiichiExhaustiveDrawReason.Normal.id,
                labels = ExhaustiveDrawSettlementStatusLabels(
                    beneficiaryTranslationKey = MinecraftMessageKeys.EXHAUSTIVE_DRAW_SETTLEMENT_STATUS_TENPAI,
                    othersTranslationKey = MinecraftMessageKeys.EXHAUSTIVE_DRAW_SETTLEMENT_STATUS_NOTEN,
                ),
            ),
        )
    }

    override fun registerRoundOutcomeDisplayNames(registry: RoundOutcomeDisplayNameRegistry) {
        registry.register(RoundOutcomeDisplayNameDefinition(RiichiRoundOutcomeIds.NAGASHI_MANGAN, MinecraftHistoryScreenKeys.ROUND_OUTCOME_NAGASHI_MANGAN))
    }

    /** 立直宣告的牌在歷史牌河上橫擺，提示顯示「立直」。 */
    override fun registerHistoryDiscardMarkerDisplays(registry: HistoryDiscardMarkerDisplayRegistry) {
        registry.register(
            definition = HistoryDiscardMarkerDisplayDefinition(
                markerId = RiichiHistoryDiscardMarkerIds.RIICHI_DECLARED,
                display = HistoryDiscardMarkerDisplay(labelTranslationKey = MinecraftMessageKeys.PLAYER_INDICATOR_RIICHI, sideways = true),
            ),
        )
    }

    /** 日麻完整模板綁定日麻規則；標題與胡牌者摘要涵蓋流局滿貫，役種、翻符與役滿倍數直接顯示規則提供的內容，寶牌與裏寶牌指示牌固定顯示五個位置。 */
    override fun registerWinSettlementPresentationTemplates(registry: WinSettlementPresentationTemplateRegistry) {
        registry.register(RiichiWinSettlementTemplates.RIICHI)
        RIICHI_FAMILY_RULE_MODULE_IDS.forEach { ruleModuleId -> RiichiFamilyMinecraftPresentation.registerWinSettlementTemplate(registry, ruleModuleId) }
        registry.register(
            definition = WinSettlementPresentationFieldProviderDefinition(RiichiWinSettlementTemplates.OUTCOME_TITLE) { snapshot ->
                PresentationValue.TextValue(
                    translationKey = when {
                        snapshot.outcomeId == RiichiRoundOutcomeIds.NAGASHI_MANGAN -> WinSettlementTextKeys.NAGASHI_MANGAN
                        snapshot.isTsumo -> WinSettlementTextKeys.TSUMO
                        else -> WinSettlementTextKeys.RON
                    },
                )
            },
        )
        registry.register(
            definition = WinSettlementPresentationFieldProviderDefinition(RiichiWinSettlementTemplates.WINNER_SUMMARY) { snapshot ->
                if (snapshot.isTsumo || snapshot.outcomeId == RiichiRoundOutcomeIds.NAGASHI_MANGAN) {
                    PresentationValue.TextValue("%s", listOf(snapshot.winnerDisplayName))
                } else {
                    PresentationValue.TextValue(
                        translationKey = WinSettlementTextKeys.RON_RELATIONSHIP,
                        arguments = listOf(snapshot.winnerDisplayName, snapshot.responsiblePlayerDisplayName.orEmpty()),
                    )
                }
            },
        )
        registry.register(WinSettlementDetailTextFormatterDefinition(RiichiWinSettlementIds.YAKU_FIELD, RiichiWinSettlementDetailTexts.yaku))
        registry.register(WinSettlementDetailTextFormatterDefinition(RiichiWinSettlementIds.HAN_FU_FIELD, RiichiWinSettlementDetailTexts.hanFu))
        registry.register(WinSettlementDetailTextFormatterDefinition(RiichiWinSettlementIds.YAKUMAN_TOTAL_FIELD, RiichiWinSettlementDetailTexts.yakumanTotal))
        RiichiWinSettlementTemplates.ENTRY_FIELDS.forEach { fieldId ->
            registry.register(WinSettlementPresentationFieldProviderDefinition(fieldId) { snapshot -> snapshot.extensionField(fieldId) })
        }
        RiichiWinSettlementTemplates.INDICATOR_FIELDS.forEach { fieldId ->
            registry.register(WinSettlementPresentationFieldProviderDefinition(fieldId) { snapshot -> RiichiWinSettlementTemplates.indicatorTiles(snapshot, fieldId) })
        }
    }

    override fun registerPublicPlayerIndicatorDisplays(registry: PublicPlayerIndicatorDisplayRegistry) {
        registry.register(PublicPlayerIndicatorDisplayDefinition(RiichiRuleModule.RIICHI_INDICATOR_ID, PublicPlayerIndicatorDisplay(MinecraftMessageKeys.PLAYER_INDICATOR_RIICHI)))
    }

    override fun registerGameConfigPresentations(registry: GameConfigPresentationRegistry) {
        registry.register(riichiGameConfigPresentation())
        registry.register(threePlayerRiichiGameConfigPresentation())
    }

    /** 六種暫用宣告語音，依動作 ID 對應；三人日麻沿用同一組。 */
    override fun registerGameActionSounds(registry: GameActionSoundPresentationRegistry) {
        RIICHI_FAMILY_RULE_MODULE_IDS.forEach { ruleModuleId -> RiichiFamilyMinecraftPresentation.registerGameActionSounds(registry, ruleModuleId) }
    }

    override fun registerRoundInfoPresentations(registry: RoundInfoLineDisplayRegistry) {
        registry.register(RoundInfoLineDisplayDefinition(RIICHI_TITLE_KEY, RoundInfoLineDisplay(MinecraftMessageKeys.ROUND_INFO_TITLE)))
        registry.register(RoundInfoLineDisplayDefinition(RIICHI_WALL_REMAINING_KEY, RoundInfoLineDisplay(MinecraftMessageKeys.ROUND_INFO_WALL_REMAINING)))
        registry.register(RoundInfoLineDisplayDefinition(RIICHI_STICK_POT_KEY, RoundInfoLineDisplay(MinecraftMessageKeys.ROUND_INFO_STICK_POT)))
        RIICHI_FAMILY_RULE_MODULE_IDS.forEach { ruleModuleId -> RiichiFamilyMinecraftPresentation.registerRoundInfo(registry, ruleModuleId) }
    }

    /** 立直與九種九牌接在核心動作（自摸為 4）之後；三人日麻另有拔北。 */
    override fun registerGameActionVocabulary(registry: GameActionVocabularyRegistry) {
        RiichiFamilyMinecraftPresentation.registerGameActionVocabulary(registry, BuiltInRuleModuleIds.RIICHI, includesPullNorth = false)
        RiichiFamilyMinecraftPresentation.registerGameActionVocabulary(registry, BuiltInRuleModuleIds.RIICHI_THREE_PLAYER, includesPullNorth = true)
    }

    override fun registerDecisionStatusDisplayNames(registry: DecisionStatusDisplayNameRegistry) {
        RIICHI_FAMILY_RULE_MODULE_IDS.forEach { ruleModuleId -> RiichiFamilyMinecraftPresentation.registerDecisionStatusDisplayNames(registry, ruleModuleId) }
    }

    override fun registerTablePropDescribers(registry: TablePropDescriberRegistry) {
        RIICHI_FAMILY_RULE_MODULE_IDS.forEach { ruleModuleId -> RiichiFamilyMinecraftPresentation.registerTableProps(registry, ruleModuleId) }
    }
}

/** 共用日麻呈現登記的規則模組：四人與三人日麻。 */
private val RIICHI_FAMILY_RULE_MODULE_IDS: List<String> = listOf(BuiltInRuleModuleIds.RIICHI, BuiltInRuleModuleIds.RIICHI_THREE_PLAYER)
