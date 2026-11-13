package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiWinSettlementIds
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInAutomaticControlIds
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHistoryDiscardMarkerIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiMatchEndReasonIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerTileAreaIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPublicIndicatorIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileValues
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.AutomaticControlDisplayRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.DecisionStatusDisplayNameRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.ExhaustiveDrawReasonDisplayNameRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.GameAchievementResolverRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.GameActionSoundPresentationRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.GameActionVocabularyRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.GameConfigPresentationRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.HistoryDiscardMarkerDisplayRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.HistoryTileSetAsideDisplayRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.MinecraftTileAssetRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.PublicPlayerIndicatorDisplayRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.RoundInfoLineDisplayRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.RoundOutcomeDisplayNameRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.RuleCatalogueRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.RuleModuleDisplayNameRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.RuleWordingRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.TablePropDescriberRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.TableTileLayoutProfileRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.TileDisplayNameRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.TileEmojiRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.TileLabelRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.WinCelebrationShowcaseRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.WinSettlementPresentationTemplateRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlRuleDisplayDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.BuiltInAutomaticControlDisplayOrders
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.BuiltInAutomaticControlTranslationKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplayDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryTileAreaDisplayDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.achievement.RiichiGameAchievementResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.catalogue.RiichiCatalogueProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawSettlementStatusLabels
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawSettlementStatusLabelsDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundOutcomeDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementDetailTextFormatterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationFieldProviderDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementTextKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.BuiltInRuleWordingIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.RuleWordingDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileStyleAssetDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileLayoutProfile
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileLayoutProfileDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileStyleDisplayNameDefinition
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

    override fun registerTileAssets(registry: MinecraftTileAssetRegistrar) {
        RIICHI_FAMILY_RULE_MODULE_IDS.forEach { ruleModuleId -> RiichiFamilyMinecraftPresentation.registerTileAppearance(registry, ruleModuleId) }
        registry.register(
            definition = MinecraftTileStyleAssetDefinition(
                styleId = RiichiTileValues.RED_FIVE_APPEARANCE_ID,
                tile = Tile.Numeric(Tile.Suit.Character, 5),
                assetKey = "m5_red",
            ),
        )
        registry.register(
            definition = MinecraftTileStyleAssetDefinition(
                styleId = RiichiTileValues.RED_FIVE_APPEARANCE_ID,
                tile = Tile.Numeric(Tile.Suit.Dot, 5),
                assetKey = "p5_red",
            ),
        )
        registry.register(
            definition = MinecraftTileStyleAssetDefinition(
                styleId = RiichiTileValues.RED_FIVE_APPEARANCE_ID,
                tile = Tile.Numeric(Tile.Suit.Bamboo, 5),
                assetKey = "s5_red",
            ),
        )
    }

    override fun registerTileDisplayNames(registry: TileDisplayNameRegistrar) {
        registry.register(
            definition = TileStyleDisplayNameDefinition(
                styleId = RiichiTileValues.RED_FIVE_APPEARANCE_ID,
                tile = Tile.Numeric(Tile.Suit.Character, 5),
                translationKey = RiichiTranslationKeys.TILE_RED_FIVE_CHARACTER,
            ),
        )
        registry.register(
            definition = TileStyleDisplayNameDefinition(
                styleId = RiichiTileValues.RED_FIVE_APPEARANCE_ID,
                tile = Tile.Numeric(Tile.Suit.Dot, 5),
                translationKey = RiichiTranslationKeys.TILE_RED_FIVE_DOT,
            ),
        )
        registry.register(
            definition = TileStyleDisplayNameDefinition(
                styleId = RiichiTileValues.RED_FIVE_APPEARANCE_ID,
                tile = Tile.Numeric(Tile.Suit.Bamboo, 5),
                translationKey = RiichiTranslationKeys.TILE_RED_FIVE_BAMBOO,
            ),
        )
    }

    override fun registerRuleModuleDisplayNames(registry: RuleModuleDisplayNameRegistrar) {
        registry.register(RuleModuleDisplayNameDefinition(BuiltInRuleModuleIds.RIICHI, RiichiTranslationKeys.RULE_NAME))
        registry.register(RuleModuleDisplayNameDefinition(BuiltInRuleModuleIds.RIICHI_THREE_PLAYER, RiichiTranslationKeys.RULE_NAME_THREE_PLAYER))
    }

    override fun registerTileEmojis(registry: TileEmojiRegistrar) {
        registry.register(TileEmojiDefinition("m5_red", "🀬"))
        registry.register(TileEmojiDefinition("s5_red", "🀭"))
        registry.register(TileEmojiDefinition("p5_red", "🀮"))
    }

    /** 赤五牌面印刷成紅色，角落文字用黑色。 */
    override fun registerTileLabels(registry: TileLabelRegistrar) {
        registry.register(TileLabelDefinition("m5_red", blackTextOnRedTile("5")))
        registry.register(TileLabelDefinition("s5_red", blackTextOnRedTile("5")))
        registry.register(TileLabelDefinition("p5_red", blackTextOnRedTile("5")))
    }

    override fun registerRuleCatalogues(registry: RuleCatalogueRegistrar) {
        registry.register(RiichiCatalogueProvider())
        registry.register(
            RiichiCatalogueProvider(
                ruleModuleId = BuiltInRuleModuleIds.RIICHI_THREE_PLAYER,
                configClass = ThreePlayerRiichiRuleConfig::class,
                defaultConfig = ::ThreePlayerRiichiRuleConfig,
            ),
        )
    }

    override fun registerGameAchievementResolvers(registry: GameAchievementResolverRegistrar) {
        RIICHI_FAMILY_RULE_MODULE_IDS.forEach { ruleModuleId -> registry.register(RiichiGameAchievementResolver(ruleModuleId)) }
    }

    override fun registerWinCelebrationShowcases(registry: WinCelebrationShowcaseRegistrar) {
        riichiWinCelebrationShowcases().forEach(registry::register)
    }

    override fun registerExhaustiveDrawReasonDisplayNames(registry: ExhaustiveDrawReasonDisplayNameRegistrar) {
        registry.register(ExhaustiveDrawReasonDisplayNameDefinition(RiichiExhaustiveDrawReason.Normal.id, MinecraftMessageKeys.EXHAUSTIVE_DRAW_REASON_NORMAL))
        registry.register(ExhaustiveDrawReasonDisplayNameDefinition(RiichiExhaustiveDrawReason.KyuushuKyuuhai.id, RiichiTranslationKeys.ACTION_KYUUSHU_KYUUHAI))
        registry.register(ExhaustiveDrawReasonDisplayNameDefinition(RiichiExhaustiveDrawReason.SuufonRenda.id, RiichiTranslationKeys.EXHAUSTIVE_DRAW_SUUFON_RENDA))
        registry.register(ExhaustiveDrawReasonDisplayNameDefinition(RiichiExhaustiveDrawReason.SuukanNagare.id, RiichiTranslationKeys.EXHAUSTIVE_DRAW_SUUKAN_NAGARE))
        registry.register(ExhaustiveDrawReasonDisplayNameDefinition(RiichiExhaustiveDrawReason.SuuchaRiichi.id, RiichiTranslationKeys.EXHAUSTIVE_DRAW_SUUCHA_RIICHI))
        registry.register(ExhaustiveDrawReasonDisplayNameDefinition(RiichiExhaustiveDrawReason.SanchaHou.id, RiichiTranslationKeys.EXHAUSTIVE_DRAW_SANCHA_HOU))
        registry.register(
            definition = ExhaustiveDrawSettlementStatusLabelsDefinition(
                reasonId = RiichiExhaustiveDrawReason.Normal.id,
                labels = ExhaustiveDrawSettlementStatusLabels(
                    beneficiaryTranslationKey = RiichiTranslationKeys.EXHAUSTIVE_DRAW_TENPAI,
                    othersTranslationKey = RiichiTranslationKeys.EXHAUSTIVE_DRAW_NOTEN,
                ),
            ),
        )
    }

    override fun registerRoundOutcomeDisplayNames(registry: RoundOutcomeDisplayNameRegistrar) {
        registry.register(RoundOutcomeDisplayNameDefinition(RiichiRoundOutcomeIds.NAGASHI_MANGAN, RiichiTranslationKeys.OUTCOME_NAGASHI_MANGAN))
        registry.register(RoundOutcomeDisplayNameDefinition(RiichiMatchEndReasonIds.DEALER_TOP_FINISH, RiichiTranslationKeys.OUTCOME_DEALER_TOP_FINISH))
    }

    /** 三人日麻拔出的北在牌譜上標示為「拔出的牌」。 */
    override fun registerHistoryTileSetAsideDisplays(registry: HistoryTileSetAsideDisplayRegistrar) {
        registry.register(HistoryTileAreaDisplayDefinition(RiichiPlayerTileAreaIds.NORTH, RiichiTranslationKeys.HISTORY_TILE_AREA_NORTH))
    }

    /** 立直宣告的牌在歷史牌河上橫擺，提示顯示「立直」。 */
    override fun registerHistoryDiscardMarkerDisplays(registry: HistoryDiscardMarkerDisplayRegistrar) {
        registry.register(
            definition = HistoryDiscardMarkerDisplayDefinition(
                markerId = RiichiHistoryDiscardMarkerIds.RIICHI_DECLARED,
                display = HistoryDiscardMarkerDisplay(labelTranslationKey = RiichiTranslationKeys.INDICATOR_RIICHI, sideways = true),
            ),
        )
    }

    /** 日麻完整模板綁定日麻規則；標題與胡牌者摘要涵蓋流局滿貫，役種、翻符與役滿倍數直接顯示規則提供的內容，寶牌與裏寶牌指示牌固定顯示五個位置。 */
    override fun registerWinSettlementPresentationTemplates(registry: WinSettlementPresentationTemplateRegistrar) {
        registry.register(RiichiWinSettlementTemplates.RIICHI)
        RIICHI_FAMILY_RULE_MODULE_IDS.forEach { ruleModuleId -> RiichiFamilyMinecraftPresentation.registerWinSettlementTemplate(registry, ruleModuleId) }
        registry.register(
            definition = WinSettlementPresentationFieldProviderDefinition(RiichiWinSettlementTemplates.OUTCOME_TITLE) { snapshot ->
                PresentationValue.TextValue(
                    translationKey = when {
                        snapshot.outcomeId == RiichiRoundOutcomeIds.NAGASHI_MANGAN -> RiichiTranslationKeys.SETTLEMENT_NAGASHI_MANGAN
                        else -> snapshot.winActionLabelKey
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
        registry.register(
            definition = WinSettlementPresentationFieldProviderDefinition(RiichiWinSettlementTemplates.DORA_LABEL) {
                PresentationValue.TextValue(RiichiTranslationKeys.SETTLEMENT_DORA)
            },
        )
        registry.register(
            definition = WinSettlementPresentationFieldProviderDefinition(RiichiWinSettlementTemplates.URA_DORA_LABEL) {
                PresentationValue.TextValue(RiichiTranslationKeys.SETTLEMENT_URA_DORA)
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

    override fun registerPublicPlayerIndicatorDisplays(registry: PublicPlayerIndicatorDisplayRegistrar) {
        registry.register(PublicPlayerIndicatorDisplayDefinition(RiichiPublicIndicatorIds.RIICHI, PublicPlayerIndicatorDisplay(RiichiTranslationKeys.INDICATOR_RIICHI)))
    }

    override fun registerGameConfigPresentations(registry: GameConfigPresentationRegistrar) {
        registry.register(riichiGameConfigPresentation())
        registry.register(threePlayerRiichiGameConfigPresentation())
    }

    /** 六種暫用宣告語音，依動作 ID 對應；三人日麻沿用同一組。 */
    override fun registerGameActionSounds(registry: GameActionSoundPresentationRegistrar) {
        RIICHI_FAMILY_RULE_MODULE_IDS.forEach { ruleModuleId -> RiichiFamilyMinecraftPresentation.registerGameActionSounds(registry, ruleModuleId) }
    }

    override fun registerRoundInfoPresentations(registry: RoundInfoLineDisplayRegistrar) {
        registry.register(RoundInfoLineDisplayDefinition(RIICHI_TITLE_KEY, RoundInfoLineDisplay(RiichiTranslationKeys.ROUND_INFO_TITLE)))
        registry.register(RoundInfoLineDisplayDefinition(RIICHI_WALL_REMAINING_KEY, RoundInfoLineDisplay(RiichiTranslationKeys.ROUND_INFO_WALL_REMAINING)))
        registry.register(RoundInfoLineDisplayDefinition(RIICHI_STICK_POT_KEY, RoundInfoLineDisplay(RiichiTranslationKeys.ROUND_INFO_STICK_POT)))
        RIICHI_FAMILY_RULE_MODULE_IDS.forEach { ruleModuleId -> RiichiFamilyMinecraftPresentation.registerRoundInfo(registry, ruleModuleId) }
    }

    /** 日麻把牌堆稱為牌山、槓的英文為 Kan。 */
    override fun registerRuleWordings(registry: RuleWordingRegistrar) {
        RIICHI_FAMILY_RULE_MODULE_IDS.forEach { ruleModuleId ->
            registry.register(RuleWordingDefinition(ruleModuleId, BuiltInRuleWordingIds.WALL_EXHAUSTED, RiichiTranslationKeys.WALL_EXHAUSTED))
            registry.register(RuleWordingDefinition(ruleModuleId, BuiltInRuleWordingIds.HISTORY_KAN, RiichiTranslationKeys.HISTORY_ACTION_KAN))
        }
    }

    /** 日麻把「打出剛摸到的牌」稱為摸切，覆寫這個自動操作的名稱；說明與排列順序沿用預設。 */
    override fun registerAutomaticControlDisplays(registry: AutomaticControlDisplayRegistrar) {
        RIICHI_FAMILY_RULE_MODULE_IDS.forEach { ruleModuleId ->
            registry.register(
                definition = AutomaticControlRuleDisplayDefinition(
                    ruleModuleId = ruleModuleId,
                    controlId = BuiltInAutomaticControlIds.AUTO_TSUMOGIRI,
                    display = AutomaticControlDisplay(
                        labelTranslationKey = RiichiTranslationKeys.AUTOMATIC_CONTROL_TSUMOGIRI,
                        descriptionTranslationKey = BuiltInAutomaticControlTranslationKeys.AUTO_TSUMOGIRI_DESCRIPTION,
                        displayOrder = BuiltInAutomaticControlDisplayOrders.AUTO_TSUMOGIRI,
                    ),
                ),
            )
        }
    }

    /** 立直與九種九牌接在核心動作（自摸為 4）之後；三人日麻另有拔北。 */
    override fun registerGameActionVocabulary(registry: GameActionVocabularyRegistrar) {
        RiichiFamilyMinecraftPresentation.registerGameActionVocabulary(registry, BuiltInRuleModuleIds.RIICHI, includesPullNorth = false)
        RiichiFamilyMinecraftPresentation.registerGameActionVocabulary(registry, BuiltInRuleModuleIds.RIICHI_THREE_PLAYER, includesPullNorth = true)
    }

    override fun registerDecisionStatusDisplayNames(registry: DecisionStatusDisplayNameRegistrar) {
        RIICHI_FAMILY_RULE_MODULE_IDS.forEach { ruleModuleId -> RiichiFamilyMinecraftPresentation.registerDecisionStatusDisplayNames(registry, ruleModuleId) }
    }

    override fun registerTablePropDescribers(registry: TablePropDescriberRegistrar) {
        RIICHI_FAMILY_RULE_MODULE_IDS.forEach { ruleModuleId -> RiichiFamilyMinecraftPresentation.registerTableProps(registry, ruleModuleId) }
    }

    override fun registerTableTileLayoutProfiles(registry: TableTileLayoutProfileRegistrar) {
        RIICHI_FAMILY_RULE_MODULE_IDS.forEach { ruleModuleId ->
            registry.register(TableTileLayoutProfileDefinition(ruleModuleId, TableTileLayoutProfile.STANDARD))
        }
    }
}

/** 共用日麻呈現登記的規則模組：四人與三人日麻。 */
private val RIICHI_FAMILY_RULE_MODULE_IDS: List<String> = listOf(BuiltInRuleModuleIds.RIICHI, BuiltInRuleModuleIds.RIICHI_THREE_PLAYER)
