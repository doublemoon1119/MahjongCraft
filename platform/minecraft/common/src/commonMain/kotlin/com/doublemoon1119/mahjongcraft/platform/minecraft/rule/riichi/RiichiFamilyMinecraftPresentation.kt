package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RIICHI_STICK_POINTS
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardReadinessAnalyzer
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.riichiPotPoints
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabulary
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.DecisionStatusDisplayNameRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.GameActionSoundPresentationRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.GameActionVocabularyRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.MinecraftTileAssetRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.RoundInfoLineDisplayRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.TablePropDescriberRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.WinSettlementPresentationTemplateRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementRuleTemplateBindingDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.BuiltInGameActionSoundIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoArgument
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLine
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineProviderDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropDescriberDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.BuiltInMinecraftTileAppearanceIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftRuleTileAppearanceDefinition

/**
 * 依規則模組 ID 登記的日麻 Minecraft 呈現，讓使用日麻規則元件的規則模組沿用日麻的牌面、結算版面、語音、局況資訊、
 * 動作用語、決策狀態與桌面物件。
 *
 * 這裡只登記綁定到規則模組的項目；它們引用的共用內容，例如日麻結算模板、局況行文字與赤五貼圖，由
 * [BundledRiichiMinecraftExtension] 登記一次。供託由桌況的日麻供託組件提供。
 */
object RiichiFamilyMinecraftPresentation {
    /**
     * 登記預設牌面外觀。
     *
     * @param registry 牌面資源 registry。
     * @param ruleModuleId 要登記的規則模組 ID。
     */
    fun registerTileAppearance(registry: MinecraftTileAssetRegistrar, ruleModuleId: String) {
        registry.register(MinecraftRuleTileAppearanceDefinition(ruleModuleId, BuiltInMinecraftTileAppearanceIds.DEFAULT))
    }

    /**
     * 綁定日麻和牌結算模板。
     *
     * @param registry 和牌結算呈現 registry。
     * @param ruleModuleId 要綁定的規則模組 ID。
     */
    fun registerWinSettlementTemplate(registry: WinSettlementPresentationTemplateRegistrar, ruleModuleId: String) {
        registry.register(WinSettlementRuleTemplateBindingDefinition(ruleModuleId, RiichiWinSettlementTemplates.RIICHI_KEY))
    }

    /**
     * 登記六種暫用宣告語音，依動作 ID 對應。
     *
     * @param registry 動作語音 registry。
     * @param ruleModuleId 要登記的規則模組 ID。
     */
    fun registerGameActionSounds(registry: GameActionSoundPresentationRegistrar, ruleModuleId: String) {
        listOf(
            BuiltInGameActionSoundIds.CHII to RiichiGameActionVoiceSoundIds.CHII,
            BuiltInGameActionSoundIds.PON to RiichiGameActionVoiceSoundIds.PON,
            BuiltInGameActionSoundIds.KAN to RiichiGameActionVoiceSoundIds.KAN,
            RiichiGameAction.Riichi.id to RiichiGameActionVoiceSoundIds.RIICHI,
            BuiltInGameActionSoundIds.RON to RiichiGameActionVoiceSoundIds.RON,
            BuiltInGameActionSoundIds.TSUMO to RiichiGameActionVoiceSoundIds.TSUMO,
        ).forEach { (actionId, soundId) ->
            registry.register(
                definition = GameActionSoundDefinition(
                    ruleModuleId = ruleModuleId,
                    actionId = actionId,
                    presentation = GameActionSoundPresentation(soundId),
                ),
            )
        }
    }

    /**
     * 登記局況資訊：場風、局數與本場數，供託不為零時的立直棒數量，以及活牌剩餘張數。
     *
     * @param registry 局況資訊 registry。
     * @param ruleModuleId 要登記的規則模組 ID。
     */
    fun registerRoundInfo(registry: RoundInfoLineDisplayRegistrar, ruleModuleId: String) {
        registry.register(RoundInfoLineProviderDefinition(ruleModuleId, RoundInfoLineProvider(::buildRiichiRoundInfoLines)))
    }

    /**
     * 登記立直與九種九牌的動作用語，接在核心動作（自摸為 4）之後；[includesPullNorth] 為 true 時另登記拔北。
     *
     * @param registry 動作用語 registry。
     * @param ruleModuleId 要登記的規則模組 ID。
     * @param includesPullNorth 規則是否可以拔北。
     */
    fun registerGameActionVocabulary(
        registry: GameActionVocabularyRegistrar,
        ruleModuleId: String,
        includesPullNorth: Boolean,
    ) {
        registry.register(
            definition = GameActionVocabularyDefinition(
                ruleModuleId = ruleModuleId,
                actionId = RiichiGameAction.Riichi.id,
                vocabulary = GameActionVocabulary(
                    labelKey = RiichiTranslationKeys.ACTION_RIICHI,
                    order = RIICHI_ACTION_ORDER,
                    descriptionKey = RiichiTranslationKeys.ACTION_RIICHI_DESCRIPTION,
                ),
            ),
        )
        registry.register(
            definition = GameActionVocabularyDefinition(
                ruleModuleId = ruleModuleId,
                actionId = RiichiExhaustiveDrawReason.KyuushuKyuuhai.id,
                vocabulary = GameActionVocabulary(
                    labelKey = RiichiTranslationKeys.ACTION_KYUUSHU_KYUUHAI,
                    order = KYUUSHU_KYUUHAI_ACTION_ORDER,
                    descriptionKey = RiichiTranslationKeys.ACTION_KYUUSHU_KYUUHAI_DESCRIPTION,
                ),
            ),
        )
        if (!includesPullNorth) return
        registry.register(
            definition = GameActionVocabularyDefinition(
                ruleModuleId = ruleModuleId,
                actionId = RiichiGameAction.PullNorth.id,
                vocabulary = GameActionVocabulary(
                    labelKey = RiichiTranslationKeys.ACTION_PULL_NORTH,
                    order = PULL_NORTH_ACTION_ORDER,
                    descriptionKey = RiichiTranslationKeys.ACTION_PULL_NORTH_DESCRIPTION,
                ),
            ),
        )
    }

    /**
     * 登記振聽與和牌限制等捨牌分析狀態的名稱。
     *
     * @param registry 決策狀態名稱 registry。
     * @param ruleModuleId 要登記的規則模組 ID。
     */
    fun registerDecisionStatusDisplayNames(registry: DecisionStatusDisplayNameRegistrar, ruleModuleId: String) {
        listOf(
            RiichiDiscardReadinessAnalyzer.StatusIds.DISCARD_FURITEN to RiichiTranslationKeys.HUD_FURITEN_DISCARD,
            RiichiDiscardReadinessAnalyzer.StatusIds.TEMPORARY_FURITEN to RiichiTranslationKeys.HUD_FURITEN_TEMPORARY,
            RiichiDiscardReadinessAnalyzer.StatusIds.PERMANENT_FURITEN to RiichiTranslationKeys.HUD_FURITEN_PERMANENT,
            RiichiDiscardReadinessAnalyzer.StatusIds.WIN_TSUMO_ONLY to RiichiTranslationKeys.HUD_WIN_TSUMO_ONLY,
            RiichiDiscardReadinessAnalyzer.StatusIds.WIN_NO_YAKU to RiichiTranslationKeys.HUD_WIN_NO_YAKU,
            RiichiDiscardReadinessAnalyzer.StatusIds.WIN_BELOW_MINIMUM to RiichiTranslationKeys.HUD_WIN_BELOW_MINIMUM,
        ).forEach { (statusId, translationKey) ->
            registry.register(DecisionStatusDisplayNameDefinition(ruleModuleId, statusId, translationKey))
        }
    }

    /**
     * 登記立直棒等日麻桌面物件。
     *
     * @param registry 桌面物件說明 registry。
     * @param ruleModuleId 要登記的規則模組 ID。
     */
    fun registerTableProps(registry: TablePropDescriberRegistrar, ruleModuleId: String) {
        registry.register(TablePropDescriberDefinition(ruleModuleId, RiichiTableProps))
    }
}

/** 依目前桌況建立日麻局況行；供託為零時省略供託行。 */
private fun buildRiichiRoundInfoLines(tableState: TableState): List<RoundInfoLine> {
    val stickPotCount = tableState.riichiPotPoints() / RIICHI_STICK_POINTS
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
internal const val RIICHI_TITLE_KEY: String = "mahjongcraft:riichi/round_title"

/** 活牌區剩餘張數行的 key。 */
internal const val RIICHI_WALL_REMAINING_KEY: String = "mahjongcraft:riichi/wall_remaining"

/** 累積供託立直棒數量行的 key。 */
internal const val RIICHI_STICK_POT_KEY: String = "mahjongcraft:riichi/stick_pot"

/** 立直在操作卡中的順序。 */
private const val RIICHI_ACTION_ORDER: Int = 5

/** 九種九牌在操作卡中的順序。 */
private const val KYUUSHU_KYUUHAI_ACTION_ORDER: Int = 6

/** 拔北在操作卡中的順序。 */
private const val PULL_NORTH_ACTION_ORDER: Int = 7
