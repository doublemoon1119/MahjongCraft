package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiHistoryDiscardMarkerIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiWinSettlementIds
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardReadinessAnalyzer
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolverRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabulary
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.achievement.RiichiGameAchievementResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.catalogue.RiichiCatalogueProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawSettlementStatusLabels
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundOutcomeDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementTextKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.BuiltInGameActionSoundIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.BuiltInGameActionVoiceSoundIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoArgument
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLine
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropDescriberRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.blackTextOnRedTile

/**
 * 日麻的 Minecraft 呈現整合，與平台無關的日麻 extension 使用同一個 extension ID；日麻在 Minecraft 端的所有登記都寫在這裡。
 *
 * 包含赤五的貼圖、名稱、表情與標籤、規則名稱與設定畫面、規則說明、成就、役滿演出、流局原因名稱、
 * 胡牌結算版面、動作用語、決策狀態、音效、資訊列、桌面物件說明、立直標示，以及歷史畫面的流局滿貫名稱與立直宣告牌。
 */
object BundledRiichiMinecraftExtension : MinecraftMahjongExtension {
    override val id: String = MahjongCraftMetadata.id("riichi")

    override fun registerTileAssets(registry: MinecraftTileAssetRegistry) {
        registry.register(RiichiTileTypes.RED_FIVE_CHARACTER, "m5_red")
        registry.register(RiichiTileTypes.RED_FIVE_DOT, "p5_red")
        registry.register(RiichiTileTypes.RED_FIVE_BAMBOO, "s5_red")
    }

    override fun registerTileDisplayNames(registry: TileDisplayNameRegistry) {
        registry.register(RiichiTileTypes.RED_FIVE_CHARACTER, MinecraftMessageKeys.TILE_RED_FIVE_CHARACTER)
        registry.register(RiichiTileTypes.RED_FIVE_DOT, MinecraftMessageKeys.TILE_RED_FIVE_DOT)
        registry.register(RiichiTileTypes.RED_FIVE_BAMBOO, MinecraftMessageKeys.TILE_RED_FIVE_BAMBOO)
    }

    override fun registerRuleModuleDisplayNames(registry: RuleModuleDisplayNameRegistry) {
        registry.register(BuiltInRuleModuleIds.RIICHI, MinecraftMessageKeys.RULE_MODULE_RIICHI)
    }

    override fun registerTileEmojis(registry: TileEmojiRegistry) {
        registry.register("m5_red", "🀬")
        registry.register("s5_red", "🀭")
        registry.register("p5_red", "🀮")
    }

    /** 赤五牌面印刷成紅色，角落文字用黑色。 */
    override fun registerTileLabels(registry: TileLabelRegistry) {
        registry.register("m5_red", blackTextOnRedTile("5"))
        registry.register("s5_red", blackTextOnRedTile("5"))
        registry.register("p5_red", blackTextOnRedTile("5"))
    }

    override fun registerRuleCatalogues(registry: RuleCatalogueRegistry) {
        registry.register(RiichiCatalogueProvider())
    }

    override fun registerGameAchievementResolvers(registry: GameAchievementResolverRegistry) {
        registry.register(RiichiGameAchievementResolver)
    }

    override fun registerWinCelebrationShowcases(registry: WinCelebrationShowcaseRegistry) {
        riichiWinCelebrationShowcases().forEach(registry::register)
    }

    override fun registerExhaustiveDrawReasonDisplayNames(registry: ExhaustiveDrawReasonDisplayNameRegistry) {
        registry.register(RiichiExhaustiveDrawReason.Normal.id, MinecraftMessageKeys.EXHAUSTIVE_DRAW_REASON_NORMAL)
        registry.register(RiichiExhaustiveDrawReason.KyuushuKyuuhai.id, MinecraftMessageKeys.GAME_ACTION_KYUUSHU_KYUUHAI)
        registry.register(RiichiExhaustiveDrawReason.SuufonRenda.id, MinecraftMessageKeys.GAME_ACTION_SUUFON_RENDA)
        registry.register(RiichiExhaustiveDrawReason.SuukanNagare.id, MinecraftMessageKeys.GAME_ACTION_SUUKAN_NAGARE)
        registry.register(RiichiExhaustiveDrawReason.SuuchaRiichi.id, MinecraftMessageKeys.GAME_ACTION_SUUCHA_RIICHI)
        registry.register(RiichiExhaustiveDrawReason.SanchaHou.id, MinecraftMessageKeys.GAME_ACTION_SANCHA_HOU)
        registry.registerSettlementStatusLabels(
            RiichiExhaustiveDrawReason.Normal.id,
            ExhaustiveDrawSettlementStatusLabels(
                beneficiaryTranslationKey = MinecraftMessageKeys.EXHAUSTIVE_DRAW_SETTLEMENT_STATUS_TENPAI,
                othersTranslationKey = MinecraftMessageKeys.EXHAUSTIVE_DRAW_SETTLEMENT_STATUS_NOTEN,
            ),
        )
    }

    override fun registerRoundOutcomeDisplayNames(registry: RoundOutcomeDisplayNameRegistry) {
        registry.register(RiichiRoundOutcomeIds.NAGASHI_MANGAN, MinecraftHistoryScreenKeys.ROUND_OUTCOME_NAGASHI_MANGAN)
    }

    /** 立直宣告的牌在歷史牌河上橫擺，提示顯示「立直」。 */
    override fun registerHistoryDiscardMarkerDisplays(registry: HistoryDiscardMarkerDisplayRegistry) {
        registry.register(
            RiichiHistoryDiscardMarkerIds.RIICHI_DECLARED,
            HistoryDiscardMarkerDisplay(labelTranslationKey = MinecraftMessageKeys.PLAYER_INDICATOR_RIICHI, sideways = true),
        )
    }

    /** 日麻完整模板綁定日麻規則；標題與胡牌者摘要涵蓋流局滿貫，役種、翻符與役滿倍數直接顯示規則提供的內容，寶牌與裏寶牌指示牌固定顯示五個位置。 */
    override fun registerWinSettlementPresentationTemplates(registry: WinSettlementPresentationTemplateRegistry) {
        registry.registerTemplate(RiichiWinSettlementTemplates.RIICHI)
        registry.bindRuleTemplate(BuiltInRuleModuleIds.RIICHI, RiichiWinSettlementTemplates.RIICHI_KEY)
        registry.registerFieldProvider(RiichiWinSettlementTemplates.OUTCOME_TITLE) { snapshot ->
            PresentationValue.TextValue(
                when {
                    snapshot.outcomeId == RiichiRoundOutcomeIds.NAGASHI_MANGAN -> WinSettlementTextKeys.NAGASHI_MANGAN
                    snapshot.isTsumo -> WinSettlementTextKeys.TSUMO
                    else -> WinSettlementTextKeys.RON
                },
            )
        }
        registry.registerFieldProvider(RiichiWinSettlementTemplates.WINNER_SUMMARY) { snapshot ->
            if (snapshot.isTsumo || snapshot.outcomeId == RiichiRoundOutcomeIds.NAGASHI_MANGAN) {
                PresentationValue.TextValue("%s", listOf(snapshot.winnerDisplayName))
            } else {
                PresentationValue.TextValue(
                    WinSettlementTextKeys.RON_RELATIONSHIP,
                    listOf(snapshot.winnerDisplayName, snapshot.responsiblePlayerDisplayName.orEmpty()),
                )
            }
        }
        registry.registerDetailTextFormatter(RiichiWinSettlementIds.YAKU_FIELD, RiichiWinSettlementDetailTexts.yaku)
        registry.registerDetailTextFormatter(RiichiWinSettlementIds.HAN_FU_FIELD, RiichiWinSettlementDetailTexts.hanFu)
        registry.registerDetailTextFormatter(RiichiWinSettlementIds.YAKUMAN_TOTAL_FIELD, RiichiWinSettlementDetailTexts.yakumanTotal)
        RiichiWinSettlementTemplates.ENTRY_FIELDS.forEach { fieldId ->
            registry.registerFieldProvider(fieldId) { snapshot -> snapshot.extensionField(fieldId) }
        }
        RiichiWinSettlementTemplates.INDICATOR_FIELDS.forEach { fieldId ->
            registry.registerFieldProvider(fieldId) { snapshot -> RiichiWinSettlementTemplates.indicatorTiles(snapshot, fieldId) }
        }
    }

    override fun registerPublicPlayerIndicatorDisplays(registry: PublicPlayerIndicatorDisplayRegistry) {
        registry.register(RiichiRuleModule.RIICHI_INDICATOR_ID, PublicPlayerIndicatorDisplay(MinecraftMessageKeys.PLAYER_INDICATOR_RIICHI))
    }

    override fun registerGameConfigPresentations(registry: GameConfigPresentationRegistry) {
        registry.register(riichiGameConfigPresentation())
    }

    /** 六種暫用宣告語音，依動作 ID 對應。 */
    override fun registerGameActionSounds(registry: GameActionSoundPresentationRegistry) {
        listOf(
            BuiltInGameActionSoundIds.CHII to BuiltInGameActionVoiceSoundIds.CHII,
            BuiltInGameActionSoundIds.PON to BuiltInGameActionVoiceSoundIds.PON,
            BuiltInGameActionSoundIds.KAN to BuiltInGameActionVoiceSoundIds.KAN,
            RiichiGameAction.Riichi.id to BuiltInGameActionVoiceSoundIds.RIICHI,
            BuiltInGameActionSoundIds.RON to BuiltInGameActionVoiceSoundIds.RON,
            BuiltInGameActionSoundIds.TSUMO to BuiltInGameActionVoiceSoundIds.TSUMO,
        ).forEach { (actionId, soundId) ->
            registry.register(
                GameActionSoundDefinition(
                    ruleModuleId = BuiltInRuleModuleIds.RIICHI,
                    actionId = actionId,
                    presentation = GameActionSoundPresentation(soundId),
                ),
            )
        }
    }

    override fun registerRoundInfoPresentations(registry: RoundInfoLineDisplayRegistry) {
        registry.register(RIICHI_TITLE_KEY, RoundInfoLineDisplay(MinecraftMessageKeys.ROUND_INFO_TITLE))
        registry.register(RIICHI_WALL_REMAINING_KEY, RoundInfoLineDisplay(MinecraftMessageKeys.ROUND_INFO_WALL_REMAINING))
        registry.register(RIICHI_STICK_POT_KEY, RoundInfoLineDisplay(MinecraftMessageKeys.ROUND_INFO_STICK_POT))
        registry.register(BuiltInRuleModuleIds.RIICHI, RoundInfoLineProvider(::buildRiichiRoundInfoLines))
    }

    /** 立直與九種九牌接在核心動作（自摸為 4）之後。 */
    override fun registerGameActionVocabulary(registry: GameActionVocabularyRegistry) {
        registry.register(
            BuiltInRuleModuleIds.RIICHI,
            RiichiGameAction.Riichi.id,
            GameActionVocabulary(
                labelKey = MinecraftMessageKeys.GAME_ACTION_RIICHI,
                order = RIICHI_ACTION_ORDER,
                descriptionKey = HUD_ACTION_RIICHI_DESCRIPTION,
            ),
        )
        registry.register(
            BuiltInRuleModuleIds.RIICHI,
            RiichiExhaustiveDrawReason.KyuushuKyuuhai.id,
            GameActionVocabulary(
                labelKey = MinecraftMessageKeys.GAME_ACTION_KYUUSHU_KYUUHAI,
                order = KYUUSHU_KYUUHAI_ACTION_ORDER,
                descriptionKey = HUD_ACTION_KYUUSHU_KYUUHAI_DESCRIPTION,
            ),
        )
    }

    override fun registerDecisionStatusDisplayNames(registry: DecisionStatusDisplayNameRegistry) {
        registry.register(BuiltInRuleModuleIds.RIICHI, RiichiDiscardReadinessAnalyzer.StatusIds.DISCARD_FURITEN, HUD_FURITEN_DISCARD)
        registry.register(BuiltInRuleModuleIds.RIICHI, RiichiDiscardReadinessAnalyzer.StatusIds.TEMPORARY_FURITEN, HUD_FURITEN_TEMPORARY)
        registry.register(BuiltInRuleModuleIds.RIICHI, RiichiDiscardReadinessAnalyzer.StatusIds.PERMANENT_FURITEN, HUD_FURITEN_PERMANENT)
        registry.register(BuiltInRuleModuleIds.RIICHI, RiichiDiscardReadinessAnalyzer.StatusIds.WIN_TSUMO_ONLY, HUD_WIN_TSUMO_ONLY)
        registry.register(BuiltInRuleModuleIds.RIICHI, RiichiDiscardReadinessAnalyzer.StatusIds.WIN_NO_YAKU, HUD_WIN_NO_YAKU)
        registry.register(BuiltInRuleModuleIds.RIICHI, RiichiDiscardReadinessAnalyzer.StatusIds.WIN_BELOW_MINIMUM, HUD_WIN_BELOW_MINIMUM)
    }

    override fun registerTablePropDescribers(registry: TablePropDescriberRegistry) {
        registry.register(BuiltInRuleModuleIds.RIICHI, RiichiTableProps)
    }
}

/** 依目前桌況建立日麻局況行；供託為零時省略供託行。 */
private fun buildRiichiRoundInfoLines(tableState: TableState): List<RoundInfoLine> {
    val stickPotCount = (tableState.dynamicRuleState as? RiichiDynamicState)?.riichiStickCount ?: 0
    return listOfNotNull(
        RoundInfoLine(
            RIICHI_TITLE_KEY,
            listOf(
                RoundInfoArgument.WindValue(tableState.prevalentWind),
                RoundInfoArgument.Number(tableState.localRoundNumber),
                RoundInfoArgument.Number(tableState.comboCount),
            ),
        ),
        if (stickPotCount > 0) {
            RoundInfoLine(RIICHI_STICK_POT_KEY, listOf(RoundInfoArgument.Number(stickPotCount)))
        } else {
            null
        },
        RoundInfoLine(
            RIICHI_WALL_REMAINING_KEY,
            listOf(RoundInfoArgument.Number(tableState.tileWall.remainingCount)),
        ),
    )
}

/** 場風、局數與本場數組成的標題行 key。 */
private const val RIICHI_TITLE_KEY: String = "mahjongcraft:riichi/round_title"

/** 活牌區剩餘張數行的 key。 */
private const val RIICHI_WALL_REMAINING_KEY: String = "mahjongcraft:riichi/wall_remaining"

/** 累積供託立直棒數量行的 key。 */
private const val RIICHI_STICK_POT_KEY: String = "mahjongcraft:riichi/stick_pot"

/** 日麻捨牌分析狀態使用的 translation key 前綴。 */
private const val RIICHI_HUD_PREFIX: String = MinecraftModMetadata.MOD_ID + ".hud."

/** 捨牌振聽。 */
private const val HUD_FURITEN_DISCARD: String = RIICHI_HUD_PREFIX + "furiten.discard"

/** 立直操作卡的說明。 */
private const val HUD_ACTION_RIICHI_DESCRIPTION: String = RIICHI_HUD_PREFIX + "action.riichi.description"

/** 九種九牌操作卡的說明。 */
private const val HUD_ACTION_KYUUSHU_KYUUHAI_DESCRIPTION: String = RIICHI_HUD_PREFIX + "action.kyuushu_kyuuhai.description"

/** 同巡振聽。 */
private const val HUD_FURITEN_TEMPORARY: String = RIICHI_HUD_PREFIX + "furiten.temporary"

/** 立直後振聽。 */
private const val HUD_FURITEN_PERMANENT: String = RIICHI_HUD_PREFIX + "furiten.permanent"

/** 只能自摸和牌。 */
private const val HUD_WIN_TSUMO_ONLY: String = RIICHI_HUD_PREFIX + "win_availability.tsumo_only"

/** 無役，不能和牌。 */
private const val HUD_WIN_NO_YAKU: String = RIICHI_HUD_PREFIX + "win_availability.no_yaku"

/** 未達最低翻符要求。 */
private const val HUD_WIN_BELOW_MINIMUM: String = RIICHI_HUD_PREFIX + "win_availability.below_minimum"

/** 立直在操作卡中的順序。 */
private const val RIICHI_ACTION_ORDER: Int = 5

/** 九種九牌在操作卡中的順序。 */
private const val KYUUSHU_KYUUHAI_ACTION_ORDER: Int = 6
