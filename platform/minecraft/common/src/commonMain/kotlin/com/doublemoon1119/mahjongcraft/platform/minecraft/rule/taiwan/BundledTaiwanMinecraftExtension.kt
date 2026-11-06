package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan

import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanWinSettlementIds
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanGameAction
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPublicIndicatorIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanReadyKind
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.BuiltInGameActionIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabulary
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.HUD_ACTION_PREFIX
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.GameActionSoundPresentationRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.GameActionVocabularyRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.GameConfigPresentationRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.MinecraftTileAssetRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.PublicPlayerIndicatorDisplayRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.RoundInfoLineDisplayRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.RoundOutcomeDisplayNameRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.RuleModuleDisplayNameRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.TileEmojiRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.TileLabelRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.WinSettlementPresentationTemplateRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.RULE
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.SCORE
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.categories
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.readOnlyBoolean
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.readOnlyInt
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.translationKey
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameDefinition
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
        registry.register(RuleModuleDisplayNameDefinition(BuiltInRuleModuleIds.TAIWAN, MinecraftMessageKeys.RULE_MODULE_TAIWAN))
    }

    override fun registerGameActionVocabulary(registry: GameActionVocabularyRegistrar) {
        registry.register(
            GameActionVocabularyDefinition(
                ruleModuleId = BuiltInRuleModuleIds.TAIWAN,
                actionId = TaiwanGameAction.RevealFlower.id,
                vocabulary = GameActionVocabulary(
                    labelKey = MinecraftMessageKeys.GAME_ACTION_REVEAL_FLOWER,
                    order = REVEAL_FLOWER_ACTION_ORDER,
                    descriptionKey = GAME_ACTION_REVEAL_FLOWER_DESCRIPTION,
                ),
            ),
        )
        registry.register(
            GameActionVocabularyDefinition(
                ruleModuleId = BuiltInRuleModuleIds.TAIWAN,
                actionId = TaiwanGameAction.DeclareReady.id,
                vocabulary = GameActionVocabulary(
                    labelKey = GAME_ACTION_DECLARE_READY,
                    order = DECLARE_READY_ACTION_ORDER,
                    descriptionKey = GAME_ACTION_DECLARE_READY_DESCRIPTION,
                ),
            ),
        )
        // 台灣麻將把胡別人打出的牌說成「胡牌」，不是「榮和」。
        registry.register(
            GameActionVocabularyDefinition(
                ruleModuleId = BuiltInRuleModuleIds.TAIWAN,
                actionId = BuiltInGameActionIds.RON,
                vocabulary = GameActionVocabulary(labelKey = HUD_ACTION_HU, messageKey = MinecraftMessageKeys.GAME_ACTION_HU, order = HU_ACTION_ORDER),
            ),
        )
    }

    /** 宣告聽牌的種類與莊家連莊次數的公開標示。 */
    override fun registerPublicPlayerIndicatorDisplays(registry: PublicPlayerIndicatorDisplayRegistrar) {
        listOf(
            TaiwanReadyKind.GENERAL.indicatorId to INDICATOR_READY,
            TaiwanReadyKind.HEAVENLY.indicatorId to INDICATOR_HEAVENLY_READY,
            TaiwanReadyKind.EARTHLY.indicatorId to INDICATOR_EARTHLY_READY,
        ).forEach { (indicatorId, translationKey) ->
            registry.register(PublicPlayerIndicatorDisplayDefinition(indicatorId, PublicPlayerIndicatorDisplay(translationKey)))
        }
        registry.register(PublicPlayerIndicatorDisplayDefinition(TaiwanPublicIndicatorIds.DEALER_STREAK, PublicPlayerIndicatorDisplay(INDICATOR_DEALER_STREAK)))
    }

    /** 局況資訊：圈風與局數、連莊一次以上時的連莊次數，以及活牌剩餘張數。 */
    override fun registerRoundInfoPresentations(registry: RoundInfoLineDisplayRegistrar) {
        registry.register(RoundInfoLineDisplayDefinition(TAIWAN_ROUND_TITLE_LINE, RoundInfoLineDisplay(ROUND_INFO_TITLE)))
        registry.register(RoundInfoLineDisplayDefinition(TAIWAN_DEALER_STREAK_LINE, RoundInfoLineDisplay(ROUND_INFO_DEALER_STREAK)))
        registry.register(RoundInfoLineDisplayDefinition(TAIWAN_WALL_REMAINING_LINE, RoundInfoLineDisplay(MinecraftMessageKeys.ROUND_INFO_WALL_REMAINING)))
        registry.register(RoundInfoLineProviderDefinition(BuiltInRuleModuleIds.TAIWAN, RoundInfoLineProvider(::taiwanRoundInfoLines)))
    }

    /** 登記台灣麻將吃、碰、槓、胡牌、自摸與宣告聽牌的宣告語音。 */
    override fun registerGameActionSounds(registry: GameActionSoundPresentationRegistrar) {
        listOf(
            BuiltInGameActionSoundIds.CHII to TaiwanGameActionVoiceSoundIds.CHI,
            BuiltInGameActionSoundIds.PON to TaiwanGameActionVoiceSoundIds.PON,
            BuiltInGameActionSoundIds.KAN to TaiwanGameActionVoiceSoundIds.KAN,
            BuiltInGameActionSoundIds.RON to TaiwanGameActionVoiceSoundIds.HU,
            BuiltInGameActionSoundIds.TSUMO to TaiwanGameActionVoiceSoundIds.TSUMO,
            TaiwanGameAction.DeclareReady.id to TaiwanGameActionVoiceSoundIds.READY,
        ).forEach { (actionId, soundId) ->
            registry.register(GameActionSoundDefinition(BuiltInRuleModuleIds.TAIWAN, actionId, GameActionSoundPresentation(soundId)))
        }
    }

    override fun registerRoundOutcomeDisplayNames(registry: RoundOutcomeDisplayNameRegistrar) {
        registry.register(RoundOutcomeDisplayNameDefinition(TaiwanRoundOutcomeIds.EIGHT_FLOWERS, TaiwanWinSettlementPresentation.EIGHT_FLOWERS))
        registry.register(RoundOutcomeDisplayNameDefinition(TaiwanRoundOutcomeIds.SEVEN_FLOWERS, TaiwanWinSettlementPresentation.SEVEN_FLOWERS))
    }

    override fun registerWinSettlementPresentationTemplates(registry: WinSettlementPresentationTemplateRegistrar) {
        registry.register(TaiwanWinSettlementPresentation.TEMPLATE)
        registry.register(WinSettlementRuleTemplateBindingDefinition(BuiltInRuleModuleIds.TAIWAN, TaiwanWinSettlementPresentation.TEMPLATE_KEY))
        registry.register(
            WinSettlementPresentationFieldProviderDefinition(TaiwanWinSettlementPresentation.FLOWERS_LABEL) {
                PresentationValue.TextValue(TaiwanWinSettlementPresentation.FLOWERS_LABEL_KEY)
            },
        )
        TaiwanWinSettlementPresentation.EXTENSION_FIELDS.forEach { fieldId ->
            registry.register(WinSettlementPresentationFieldProviderDefinition(fieldId) { snapshot -> snapshot.extensionField(fieldId) })
        }
        registry.register(
            WinSettlementDetailTextFormatterDefinition(
                TaiwanWinSettlementIds.TAI_FIELD,
                TaiwanWinSettlementPresentation.taiText,
            ),
        )
        registry.register(
            WinSettlementDetailTextFormatterDefinition(
                TaiwanWinSettlementIds.TAI_TOTAL_FIELD,
                TaiwanWinSettlementPresentation.taiTotalText,
            ),
        )
        registry.register(
            WinSettlementDetailTextFormatterDefinition(
                TaiwanWinSettlementIds.DEALER_TAI_TOTAL_FIELD,
                TaiwanWinSettlementPresentation.dealerTaiTotalText,
            ),
        )
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
}

/**
 * 建立唯讀台麻設定畫面的 schema；台麻目前不能選用，只顯示固定的設定值。
 *
 * @return 不可選取且欄位唯讀的台麻設定呈現定義。
 */
private fun taiwanGameConfigPresentation(): GameConfigPresentationDefinition = GameConfigPresentationDefinition(
    ruleModuleId = BuiltInRuleModuleIds.TAIWAN,
    descriptionTranslationKey = translationKey("rule.taiwan.description"),
    selectable = false,
    unavailableReasonTranslationKey = translationKey("rule.taiwan.unavailable"),
    defaultRuleConfig = ::TaiwanRuleConfig,
    categories = categories(),
    fields = listOf(
        readOnlyBoolean("use_flower_tiles", RULE) { (it.ruleConfig as TaiwanRuleConfig).useFlowerTiles },
        readOnlyInt("minimum_win_constraint", RULE) { it.ruleConfig.minimumWinConstraint },
        readOnlyInt("initial_score", SCORE) { it.ruleConfig.scoreConfig.initialScore },
    ),
)

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

/** 圈風與局數的文字。 */
private const val ROUND_INFO_TITLE: String = "mahjongcraft.taiwan.round_info.title"

/** 連莊次數的文字。 */
private const val ROUND_INFO_DEALER_STREAK: String = "mahjongcraft.taiwan.round_info.dealer_streak"

/** 宣告聽牌的操作卡名稱。 */
private const val GAME_ACTION_DECLARE_READY: String = "mahjongcraft.taiwan.action.declare_ready"

/** 宣告聽牌在操作說明中的文字。 */
private const val GAME_ACTION_DECLARE_READY_DESCRIPTION: String = "mahjongcraft.taiwan.action.declare_ready.description"

/** 宣告聽牌在操作卡中的順序，與日麻立直相同。 */
private const val DECLARE_READY_ACTION_ORDER: Int = 5

/** 一般宣告聽牌的公開標示。 */
private const val INDICATOR_READY: String = "mahjongcraft.taiwan.indicator.ready"

/** 天聽的公開標示。 */
private const val INDICATOR_HEAVENLY_READY: String = "mahjongcraft.taiwan.indicator.heavenly_ready"

/** 地聽的公開標示。 */
private const val INDICATOR_EARTHLY_READY: String = "mahjongcraft.taiwan.indicator.earthly_ready"

/** 連莊標示的文字，數值是連莊次數。 */
private const val INDICATOR_DEALER_STREAK: String = "mahjongcraft.taiwan.indicator.dealer_streak"

/** 公開花牌動作在操作說明中的文字。 */
private const val GAME_ACTION_REVEAL_FLOWER_DESCRIPTION: String = "mahjongcraft.hud.action.reveal_flower.description"

/** 胡牌的操作卡名稱。 */
private const val HUD_ACTION_HU: String = HUD_ACTION_PREFIX + "hu"

/** 胡牌在操作卡中的順序，與其他規則的榮和相同。 */
private const val HU_ACTION_ORDER: Int = 3

/** 公開花牌在操作卡中的順序。 */
private const val REVEAL_FLOWER_ACTION_ORDER: Int = 8
