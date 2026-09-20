package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardReadinessAnalyzer
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabulary
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.BuiltInGameActionSoundIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.BuiltInGameActionVoiceSoundIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineArgumentKind
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys

/**
 * 登記內建日麻動作的用語與操作卡順序。
 *
 * 立直與九種九牌接在核心動作（自摸為 4）之後，順序與目前畫面相同。
 */
fun GameActionVocabularyRegistry.registerRiichiGameActionVocabulary() {
    register(
        BuiltInRuleModuleIds.RIICHI,
        RiichiGameAction.Riichi.id,
        GameActionVocabulary(MinecraftMessageKeys.GAME_ACTION_RIICHI, order = RIICHI_ACTION_ORDER),
    )
    register(
        BuiltInRuleModuleIds.RIICHI,
        RiichiExhaustiveDrawReason.KyuushuKyuuhai.id,
        GameActionVocabulary(MinecraftMessageKeys.GAME_ACTION_KYUUSHU_KYUUHAI, order = KYUUSHU_KYUUHAI_ACTION_ORDER),
    )
}

/** 登記內建日麻的捨牌分析狀態顯示名稱。 */
fun DecisionStatusDisplayNameRegistry.registerRiichiDecisionStatusDisplayNames() {
    register(BuiltInRuleModuleIds.RIICHI, RiichiDiscardReadinessAnalyzer.StatusIds.DISCARD_FURITEN, HUD_FURITEN_DISCARD)
    register(BuiltInRuleModuleIds.RIICHI, RiichiDiscardReadinessAnalyzer.StatusIds.TEMPORARY_FURITEN, HUD_FURITEN_TEMPORARY)
    register(BuiltInRuleModuleIds.RIICHI, RiichiDiscardReadinessAnalyzer.StatusIds.PERMANENT_FURITEN, HUD_FURITEN_PERMANENT)
    register(BuiltInRuleModuleIds.RIICHI, RiichiDiscardReadinessAnalyzer.StatusIds.WIN_TSUMO_ONLY, HUD_WIN_TSUMO_ONLY)
    register(BuiltInRuleModuleIds.RIICHI, RiichiDiscardReadinessAnalyzer.StatusIds.WIN_NO_YAKU, HUD_WIN_NO_YAKU)
    register(BuiltInRuleModuleIds.RIICHI, RiichiDiscardReadinessAnalyzer.StatusIds.WIN_BELOW_MINIMUM, HUD_WIN_BELOW_MINIMUM)
}

/** 日麻捨牌分析狀態使用的 translation key 前綴。 */
private const val RIICHI_HUD_PREFIX: String = MinecraftModMetadata.MOD_ID + ".hud."

/** 捨牌振聽。 */
private const val HUD_FURITEN_DISCARD: String = RIICHI_HUD_PREFIX + "furiten.discard"

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

/** 登記內建日麻流局原因。 */
fun ExhaustiveDrawReasonDisplayNameRegistry.registerBuiltInRiichiReasons() {
    register(RiichiExhaustiveDrawReason.Normal.id, MinecraftMessageKeys.EXHAUSTIVE_DRAW_REASON_NORMAL)
    register(RiichiExhaustiveDrawReason.KyuushuKyuuhai.id, MinecraftMessageKeys.GAME_ACTION_KYUUSHU_KYUUHAI)
    register(RiichiExhaustiveDrawReason.SuufonRenda.id, MinecraftMessageKeys.GAME_ACTION_SUUFON_RENDA)
    register(RiichiExhaustiveDrawReason.SuukanNagare.id, MinecraftMessageKeys.GAME_ACTION_SUUKAN_NAGARE)
    register(RiichiExhaustiveDrawReason.SuuchaRiichi.id, MinecraftMessageKeys.GAME_ACTION_SUUCHA_RIICHI)
    register(RiichiExhaustiveDrawReason.SanchaHou.id, MinecraftMessageKeys.GAME_ACTION_SANCHA_HOU)
}

/** 登記內建日本麻將使用的六種暫用宣告語音。 */
fun GameActionSoundPresentationRegistry.registerBuiltInRiichiActionSounds() {
    val definitions = listOf(
        BuiltInGameActionSoundIds.CHII to BuiltInGameActionVoiceSoundIds.CHII,
        BuiltInGameActionSoundIds.PON to BuiltInGameActionVoiceSoundIds.PON,
        BuiltInGameActionSoundIds.KAN to BuiltInGameActionVoiceSoundIds.KAN,
        RiichiGameAction.Riichi.id to BuiltInGameActionVoiceSoundIds.RIICHI,
        BuiltInGameActionSoundIds.RON to BuiltInGameActionVoiceSoundIds.RON,
        BuiltInGameActionSoundIds.TSUMO to BuiltInGameActionVoiceSoundIds.TSUMO,
    )
    definitions.forEach { (actionId, soundId) ->
        register(
            GameActionSoundDefinition(
                ruleModuleId = BuiltInRuleModuleIds.RIICHI,
                actionId = actionId,
                presentation = GameActionSoundPresentation(soundId),
            ),
        )
    }
}

/** 登記內建日麻的局況顯示行。 */
fun RoundInfoLineDisplayRegistry.registerBuiltInRiichiRoundInfoLineDisplays() {
    register(
        RiichiRuleModule.TITLE_KEY,
        RoundInfoLineDisplay(
            MinecraftMessageKeys.ROUND_INFO_TITLE,
            listOf(RoundInfoLineArgumentKind.WIND, RoundInfoLineArgumentKind.NUMBER, RoundInfoLineArgumentKind.NUMBER),
        ),
    )
    register(RiichiRuleModule.WALL_REMAINING_KEY, RoundInfoLineDisplay(MinecraftMessageKeys.ROUND_INFO_WALL_REMAINING))
    register(RiichiRuleModule.STICK_POT_KEY, RoundInfoLineDisplay(MinecraftMessageKeys.ROUND_INFO_STICK_POT))
}
