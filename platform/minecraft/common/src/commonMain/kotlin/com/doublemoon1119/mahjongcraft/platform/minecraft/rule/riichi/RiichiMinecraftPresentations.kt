package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionDisplayNameRegistry
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

/** 登記內建日麻擴充動作的顯示名稱。 */
fun GameActionDisplayNameRegistry.registerRiichiGameActionDisplayName() {
    register(RiichiGameAction.Riichi.id, MinecraftMessageKeys.GAME_ACTION_RIICHI)
}

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
