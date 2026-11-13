package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan

import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanWinSettlementIds
import com.doublemoon1119.mahjongcraft.logic.judgment.WaitingTileAvailabilityIds
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanDiscardReadinessAnalyzer
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanGameAction
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPlayerTileAreaIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPublicIndicatorIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanReadyKind
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.BuiltInGameActionIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabulary
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.DecisionStatusDisplayNameRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.ExhaustiveDrawReasonDisplayNameRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.GameActionSoundPresentationRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.GameActionVocabularyRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.GameConfigPresentationRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.HistoryTileSetAsideDisplayRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.MinecraftTileAssetRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.PublicPlayerIndicatorDisplayRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.RoundInfoLineDisplayRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.RoundOutcomeDisplayNameRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.RuleCatalogueRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.RuleModuleDisplayNameRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.TableTileLayoutProfileRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.TileDisplayNameRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.TileEmojiRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.TileLabelRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.WinSettlementPresentationTemplateRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryTileAreaDisplayDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryTileReplacementDisplayDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan.catalogue.TaiwanCatalogueProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundOutcomeDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementDetailTextFormatterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationFieldProviderDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementRuleTemplateBindingDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.BuiltInGameActionSoundIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoArgument
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLine
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineProviderDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.BuiltInMinecraftTileAppearanceIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftRuleTileAppearanceDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableAddedKanPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableDiscardArea
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableMeldArea
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileLayoutProfile
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileLayoutProfileDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabel
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelColor
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelText

/**
 * 台麻的 Minecraft 呈現整合，與平台無關的台麻 extension 使用同一個 extension ID。
 *
 * 包含花牌的貼圖、表情與標籤，以及規則名稱與設定畫面。
 */
object BundledTaiwanMinecraftExtension : MinecraftMahjongExtension {
    override val id: String = MahjongCraftMetadata.id("taiwan")

    override fun registerTileAssets(registry: MinecraftTileAssetRegistrar) {
        registry.register(MinecraftRuleTileAppearanceDefinition(BuiltInRuleModuleIds.TAIWAN, BuiltInMinecraftTileAppearanceIds.DEFAULT))
        registry.register(MinecraftTileAssetDefinition(TaiwanTileTypes.SPRING, "flower_spring"))
        registry.register(MinecraftTileAssetDefinition(TaiwanTileTypes.SUMMER, "flower_summer"))
        registry.register(MinecraftTileAssetDefinition(TaiwanTileTypes.AUTUMN, "flower_autumn"))
        registry.register(MinecraftTileAssetDefinition(TaiwanTileTypes.WINTER, "flower_winter"))
        registry.register(MinecraftTileAssetDefinition(TaiwanTileTypes.PLUM, "flower_plum"))
        registry.register(MinecraftTileAssetDefinition(TaiwanTileTypes.ORCHID, "flower_orchid"))
        registry.register(MinecraftTileAssetDefinition(TaiwanTileTypes.BAMBOO, "flower_bamboo"))
        registry.register(MinecraftTileAssetDefinition(TaiwanTileTypes.CHRYSANTHEMUM, "flower_chrysanthemum"))
    }

    override fun registerRuleModuleDisplayNames(registry: RuleModuleDisplayNameRegistrar) {
        registry.register(RuleModuleDisplayNameDefinition(BuiltInRuleModuleIds.TAIWAN, TaiwanTranslationKeys.RULE_NAME))
    }

    override fun registerGameActionVocabulary(registry: GameActionVocabularyRegistrar) {
        registry.register(
            GameActionVocabularyDefinition(
                ruleModuleId = BuiltInRuleModuleIds.TAIWAN,
                actionId = TaiwanGameAction.RevealFlower.id,
                vocabulary = GameActionVocabulary(
                    labelKey = TaiwanTranslationKeys.ACTION_REVEAL_FLOWER,
                    order = REVEAL_FLOWER_ACTION_ORDER,
                    descriptionKey = TaiwanTranslationKeys.ACTION_REVEAL_FLOWER_DESCRIPTION,
                ),
            ),
        )
        registry.register(
            GameActionVocabularyDefinition(
                ruleModuleId = BuiltInRuleModuleIds.TAIWAN,
                actionId = TaiwanGameAction.DeclareReady.id,
                vocabulary = GameActionVocabulary(
                    labelKey = TaiwanTranslationKeys.ACTION_DECLARE_READY,
                    order = DECLARE_READY_ACTION_ORDER,
                    descriptionKey = TaiwanTranslationKeys.ACTION_DECLARE_READY_DESCRIPTION,
                ),
            ),
        )
        // 台灣麻將把胡別人打出的牌說成「胡牌」，不是「榮和」。
        registry.register(
            GameActionVocabularyDefinition(
                ruleModuleId = BuiltInRuleModuleIds.TAIWAN,
                actionId = BuiltInGameActionIds.RON,
                vocabulary = GameActionVocabulary(labelKey = TaiwanTranslationKeys.ACTION_HU, messageKey = TaiwanTranslationKeys.ACTION_HU_MESSAGE, order = HU_ACTION_ORDER),
            ),
        )
    }

    /** 登記台灣麻將的過水與等待牌胡牌資格文字；可以胡牌的狀態改用台麻說法。 */
    override fun registerDecisionStatusDisplayNames(registry: DecisionStatusDisplayNameRegistrar) {
        listOf(
            TaiwanDiscardReadinessAnalyzer.StatusIds.PASSED_WIN to TaiwanTranslationKeys.HUD_PASSED_WIN,
            WaitingTileAvailabilityIds.WIN_AVAILABLE to TaiwanTranslationKeys.HUD_WIN_AVAILABLE,
            TaiwanDiscardReadinessAnalyzer.StatusIds.WIN_TSUMO_ONLY to TaiwanTranslationKeys.HUD_WIN_TSUMO_ONLY,
            TaiwanDiscardReadinessAnalyzer.StatusIds.WIN_BELOW_MINIMUM to TaiwanTranslationKeys.HUD_WIN_BELOW_MINIMUM,
            TaiwanDiscardReadinessAnalyzer.StatusIds.WIN_NOT_AVAILABLE to TaiwanTranslationKeys.HUD_WIN_NOT_AVAILABLE,
        ).forEach { (statusId, translationKey) ->
            registry.register(DecisionStatusDisplayNameDefinition(BuiltInRuleModuleIds.TAIWAN, statusId, translationKey))
        }
    }

    /** 宣告聽牌的種類與莊家連莊次數的公開標示。 */
    override fun registerPublicPlayerIndicatorDisplays(registry: PublicPlayerIndicatorDisplayRegistrar) {
        listOf(
            TaiwanReadyKind.GENERAL.indicatorId to TaiwanTranslationKeys.INDICATOR_READY,
            TaiwanReadyKind.HEAVENLY.indicatorId to TaiwanTranslationKeys.INDICATOR_HEAVENLY_READY,
            TaiwanReadyKind.EARTHLY.indicatorId to TaiwanTranslationKeys.INDICATOR_EARTHLY_READY,
        ).forEach { (indicatorId, translationKey) ->
            registry.register(PublicPlayerIndicatorDisplayDefinition(indicatorId, PublicPlayerIndicatorDisplay(translationKey)))
        }
        registry.register(PublicPlayerIndicatorDisplayDefinition(TaiwanPublicIndicatorIds.DEALER_STREAK, PublicPlayerIndicatorDisplay(TaiwanTranslationKeys.INDICATOR_DEALER_STREAK)))
    }

    /** 局況資訊：圈風與局數、連莊一次以上時的連莊次數，以及活牌剩餘張數。 */
    override fun registerRoundInfoPresentations(registry: RoundInfoLineDisplayRegistrar) {
        registry.register(RoundInfoLineDisplayDefinition(TAIWAN_ROUND_TITLE_LINE, RoundInfoLineDisplay(TaiwanTranslationKeys.ROUND_INFO_TITLE)))
        registry.register(RoundInfoLineDisplayDefinition(TAIWAN_DEALER_STREAK_LINE, RoundInfoLineDisplay(TaiwanTranslationKeys.ROUND_INFO_DEALER_STREAK)))
        registry.register(RoundInfoLineDisplayDefinition(TAIWAN_WALL_REMAINING_LINE, RoundInfoLineDisplay(MinecraftMessageKeys.ROUND_INFO_WALL_REMAINING)))
        registry.register(RoundInfoLineProviderDefinition(BuiltInRuleModuleIds.TAIWAN, RoundInfoLineProvider(::taiwanRoundInfoLines)))
    }

    /** 登記台灣麻將吃、碰、槓、胡牌、自摸與宣告聽牌的宣告語音。 */
    override fun registerGameActionSounds(registry: GameActionSoundPresentationRegistrar) {
        listOf(
            BuiltInGameActionSoundIds.CHI to TaiwanGameActionVoiceSoundIds.CHI,
            BuiltInGameActionSoundIds.PON to TaiwanGameActionVoiceSoundIds.PON,
            BuiltInGameActionSoundIds.KAN to TaiwanGameActionVoiceSoundIds.KAN,
            BuiltInGameActionSoundIds.RON to TaiwanGameActionVoiceSoundIds.HU,
            BuiltInGameActionSoundIds.TSUMO to TaiwanGameActionVoiceSoundIds.TSUMO,
            TaiwanGameAction.DeclareReady.id to TaiwanGameActionVoiceSoundIds.READY,
        ).forEach { (actionId, soundId) ->
            registry.register(GameActionSoundDefinition(BuiltInRuleModuleIds.TAIWAN, actionId, GameActionSoundPresentation(soundId)))
        }
    }

    /** 花牌區在牌譜上標示為「花牌」，補花流程稱為「補花」。 */
    override fun registerHistoryTileSetAsideDisplays(registry: HistoryTileSetAsideDisplayRegistrar) {
        registry.register(HistoryTileAreaDisplayDefinition(TaiwanPlayerTileAreaIds.FLOWERS, TaiwanTranslationKeys.HISTORY_TILE_AREA_FLOWERS))
        registry.register(HistoryTileReplacementDisplayDefinition(BuiltInRuleModuleIds.TAIWAN, TaiwanTranslationKeys.HISTORY_REPLACEMENT))
    }

    override fun registerExhaustiveDrawReasonDisplayNames(registry: ExhaustiveDrawReasonDisplayNameRegistrar) {
        registry.register(ExhaustiveDrawReasonDisplayNameDefinition(TaiwanExhaustiveDrawReason.Normal.id, MinecraftMessageKeys.EXHAUSTIVE_DRAW_REASON_NORMAL))
        registry.register(
            definition = ExhaustiveDrawReasonDisplayNameDefinition(
                reasonId = TaiwanExhaustiveDrawReason.NoSupplementalTile.id,
                translationKey = TaiwanTranslationKeys.EXHAUSTIVE_DRAW_NO_SUPPLEMENTAL_TILE,
            ),
        )
        registry.register(ExhaustiveDrawReasonDisplayNameDefinition(TaiwanExhaustiveDrawReason.FourKans.id, TaiwanTranslationKeys.EXHAUSTIVE_DRAW_FOUR_KANS))
        registry.register(ExhaustiveDrawReasonDisplayNameDefinition(TaiwanExhaustiveDrawReason.FourWinds.id, TaiwanTranslationKeys.EXHAUSTIVE_DRAW_FOUR_WINDS))
    }

    override fun registerRoundOutcomeDisplayNames(registry: RoundOutcomeDisplayNameRegistrar) {
        registry.register(RoundOutcomeDisplayNameDefinition(TaiwanRoundOutcomeIds.EIGHT_FLOWERS, TaiwanTranslationKeys.OUTCOME_EIGHT_FLOWERS))
        registry.register(RoundOutcomeDisplayNameDefinition(TaiwanRoundOutcomeIds.SEVEN_FLOWERS, TaiwanTranslationKeys.OUTCOME_SEVEN_FLOWERS))
    }

    override fun registerWinSettlementPresentationTemplates(registry: WinSettlementPresentationTemplateRegistrar) {
        registry.register(TaiwanWinSettlementTemplates.TEMPLATE)
        registry.register(WinSettlementRuleTemplateBindingDefinition(BuiltInRuleModuleIds.TAIWAN, TaiwanWinSettlementTemplates.TEMPLATE_KEY))
        registry.register(
            WinSettlementPresentationFieldProviderDefinition(TaiwanWinSettlementTemplates.FLOWERS_LABEL) {
                PresentationValue.TextValue(TaiwanTranslationKeys.SETTLEMENT_FLOWERS)
            },
        )
        TaiwanWinSettlementTemplates.EXTENSION_FIELDS.forEach { fieldId ->
            registry.register(WinSettlementPresentationFieldProviderDefinition(fieldId) { snapshot -> snapshot.extensionField(fieldId) })
        }
        registry.register(
            WinSettlementDetailTextFormatterDefinition(
                TaiwanWinSettlementIds.TAI_FIELD,
                TaiwanWinSettlementDetailTexts.taiText,
            ),
        )
        registry.register(
            WinSettlementDetailTextFormatterDefinition(
                TaiwanWinSettlementIds.TAI_TOTAL_FIELD,
                TaiwanWinSettlementDetailTexts.taiTotalText,
            ),
        )
        registry.register(
            WinSettlementDetailTextFormatterDefinition(
                TaiwanWinSettlementIds.DEALER_TAI_TOTAL_FIELD,
                TaiwanWinSettlementDetailTexts.dealerTaiTotalText,
            ),
        )
    }

    /** 花牌以文字列出時（例如牌譜的補花紀錄）顯示牌名。 */
    override fun registerTileDisplayNames(registry: TileDisplayNameRegistrar) {
        registry.register(TileDisplayNameDefinition(TaiwanTileTypes.SPRING, TaiwanTranslationKeys.TILE_SPRING))
        registry.register(TileDisplayNameDefinition(TaiwanTileTypes.SUMMER, TaiwanTranslationKeys.TILE_SUMMER))
        registry.register(TileDisplayNameDefinition(TaiwanTileTypes.AUTUMN, TaiwanTranslationKeys.TILE_AUTUMN))
        registry.register(TileDisplayNameDefinition(TaiwanTileTypes.WINTER, TaiwanTranslationKeys.TILE_WINTER))
        registry.register(TileDisplayNameDefinition(TaiwanTileTypes.PLUM, TaiwanTranslationKeys.TILE_PLUM))
        registry.register(TileDisplayNameDefinition(TaiwanTileTypes.ORCHID, TaiwanTranslationKeys.TILE_ORCHID))
        registry.register(TileDisplayNameDefinition(TaiwanTileTypes.BAMBOO, TaiwanTranslationKeys.TILE_BAMBOO))
        registry.register(TileDisplayNameDefinition(TaiwanTileTypes.CHRYSANTHEMUM, TaiwanTranslationKeys.TILE_CHRYSANTHEMUM))
    }

    override fun registerTileEmojis(registry: TileEmojiRegistrar) {
        registry.register(TileEmojiDefinition("flower_plum", "🀣"))
        registry.register(TileEmojiDefinition("flower_orchid", "🀤"))
        registry.register(TileEmojiDefinition("flower_bamboo", "🀥"))
        registry.register(TileEmojiDefinition("flower_chrysanthemum", "🀦"))
        registry.register(TileEmojiDefinition("flower_spring", "🀧"))
        registry.register(TileEmojiDefinition("flower_summer", "🀨"))
        registry.register(TileEmojiDefinition("flower_autumn", "🀩"))
        registry.register(TileEmojiDefinition("flower_winter", "🀪"))
    }

    /**
     * 花牌成對出現：春夏秋冬右上角是紅色中文字、左上角是黑色數字（依春夏秋冬排序，1～4）；梅蘭菊竹右上角是
     * 紅色數字（依梅蘭菊竹排序，1～4，對應 [TaiwanTileTypes.ALL] 的順序）、左上角是黑色中文字。
     *
     * 花牌標籤一律標記 [TileLabel.forced]＝`true`：八張花牌都是同一個樹形剪影配不同季節配色（見
     * `mahjong_tile_flower_*.png`），彼此外觀相近、單靠材質不容易一眼分辨花色與順序，標籤本身兼有辨識
     * 功能，不只是給非中文圈玩家看的輔助資訊，因此無視玩家本機開關、永遠顯示。
     */
    override fun registerTileLabels(registry: TileLabelRegistrar) {
        // 四季花牌：右上紅色中文字、左上黑色數字，依春夏秋冬排序
        registry.register(TileLabelDefinition("flower_spring", seasonalFlower(chinese = "春", order = "1")))
        registry.register(TileLabelDefinition("flower_summer", seasonalFlower(chinese = "夏", order = "2")))
        registry.register(TileLabelDefinition("flower_autumn", seasonalFlower(chinese = "秋", order = "3")))
        registry.register(TileLabelDefinition("flower_winter", seasonalFlower(chinese = "冬", order = "4")))

        // 四君子花牌：右上紅色數字、左上黑色中文字，依梅蘭菊竹排序
        registry.register(TileLabelDefinition("flower_plum", plantFlower(chinese = "梅", order = "1")))
        registry.register(TileLabelDefinition("flower_orchid", plantFlower(chinese = "蘭", order = "2")))
        registry.register(TileLabelDefinition("flower_chrysanthemum", plantFlower(chinese = "菊", order = "3")))
        registry.register(TileLabelDefinition("flower_bamboo", plantFlower(chinese = "竹", order = "4")))
    }

    override fun registerGameConfigPresentations(registry: GameConfigPresentationRegistrar) {
        registry.register(taiwanGameConfigPresentation())
    }

    /** 登記台灣麻將預設台表、特殊胡牌與流局說明。 */
    override fun registerRuleCatalogues(registry: RuleCatalogueRegistrar) {
        registry.register(TaiwanCatalogueProvider())
    }

    override fun registerTableTileLayoutProfiles(registry: TableTileLayoutProfileRegistrar) {
        registry.register(TableTileLayoutProfileDefinition(BuiltInRuleModuleIds.TAIWAN, TAIWAN_TABLE_TILE_LAYOUT))
    }
}

/**
 * 建立四季花牌標籤：右上紅色中文字、左上黑色排序數字，永遠顯示（理由見 [BundledTaiwanMinecraftExtension.registerTileLabels]）。
 *
 * @param chinese 花牌的中文字。
 * @param order 春夏秋冬順序中的數字。
 * @return 永遠顯示的季節花牌標籤。
 */
private fun seasonalFlower(chinese: String, order: String): TileLabel = TileLabel(
    topLeft = TileLabelText(order, TileLabelColor.BLACK),
    topRight = TileLabelText(chinese, TileLabelColor.RED),
    forced = true,
)

/**
 * 建立四君子花牌標籤：右上紅色排序數字、左上黑色中文字，永遠顯示（理由見 [BundledTaiwanMinecraftExtension.registerTileLabels]）。
 *
 * @param chinese 花牌的中文字。
 * @param order 梅蘭菊竹順序中的數字。
 * @return 永遠顯示的四君子花牌標籤。
 */
private fun plantFlower(chinese: String, order: String): TileLabel = TileLabel(
    topLeft = TileLabelText(chinese, TileLabelColor.BLACK),
    topRight = TileLabelText(order, TileLabelColor.RED),
    forced = true,
)

/**
 * 台灣麻將的局況資訊行。
 *
 * @param tableState 目前桌況。
 * @return 依顯示順序排列的資訊行。
 */
internal fun taiwanRoundInfoLines(tableState: TableState): List<RoundInfoLine> = listOfNotNull(
    RoundInfoLine(TAIWAN_ROUND_TITLE_LINE, listOf(RoundInfoArgument.WindValue(tableState.prevalentWind), RoundInfoArgument.Number(tableState.localRoundNumber))),
    RoundInfoLine(TAIWAN_DEALER_STREAK_LINE, listOf(RoundInfoArgument.Number(tableState.comboCount))).takeIf { tableState.comboCount > 0 },
    RoundInfoLine(TAIWAN_WALL_REMAINING_LINE, listOf(RoundInfoArgument.Number(tableState.tileWall.remainingCount))),
)

/** 圈風與局數行。 */
internal const val TAIWAN_ROUND_TITLE_LINE: String = "mahjongcraft:taiwan/round_title"

/** 連莊次數行。 */
internal const val TAIWAN_DEALER_STREAK_LINE: String = "mahjongcraft:taiwan/dealer_streak"

/** 活牌剩餘張數行。 */
internal const val TAIWAN_WALL_REMAINING_LINE: String = "mahjongcraft:taiwan/wall_remaining"

/** 宣告聽牌在操作卡中的順序，與日麻立直相同。 */
private const val DECLARE_READY_ACTION_ORDER: Int = 5

/**
 * 台灣麻將的桌面牌位：牌牆往桌心移動並錯開成風車形，副露放在牌牆與手牌之間、全部直立，加槓四張一列，牌河散放在桌子中央。
 */
private val TAIWAN_TABLE_TILE_LAYOUT = TableTileLayoutProfile(
    wallInset = 0.105,
    wallCornerOverhang = 0.18,
    meldArea = TableMeldArea.BETWEEN_WALL_AND_HAND,
    claimedTileSideways = false,
    addedKan = TableAddedKanPlacement.IN_ROW,
    discardArea = TableDiscardArea.SCATTERED,
)

/** 胡牌在操作卡中的順序，與其他規則的榮和相同。 */
private const val HU_ACTION_ORDER: Int = 3

/** 公開花牌在操作卡中的順序。 */
private const val REVEAL_FLOWER_ACTION_ORDER: Int = 8
