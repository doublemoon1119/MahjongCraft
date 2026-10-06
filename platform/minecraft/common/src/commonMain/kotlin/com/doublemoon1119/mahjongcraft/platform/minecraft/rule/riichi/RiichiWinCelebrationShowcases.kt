package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInWinCelebrationCueIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.ShowcasePalette
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftShowcaseKeys

/** 所有日麻役滿展示定義；標題 key 與貼圖都放在日麻專屬的命名下，越前面的役滿優先序越高。 */
internal fun riichiWinCelebrationShowcases(): List<WinCelebrationShowcaseDefinition> = YAKUMAN_CUES.mapIndexed { index, cue ->
    WinCelebrationShowcaseDefinition(
        cueKey = BuiltInWinCelebrationCueIds.riichiYakuman(cue),
        titleTranslationKey = MinecraftShowcaseKeys.riichiYakuman(cue),
        titleImageResourceId = "${MinecraftModMetadata.MOD_ID}:textures/showcase/riichi/$cue.png",
        palette = ShowcasePalette(primary = 0xFFFFD45A.toInt(), secondary = 0xFFC32128.toInt(), accent = 0xFFFFFFFF.toInt()),
        priority = YAKUMAN_CUES.size - index,
    )
}

/** 日麻役滿 cue 的役種名稱，依挑選優先序由高到低排列。 */
private val YAKUMAN_CUES = listOf(
    "kokushi_musou_13",
    "churen_poto_9",
    "suuankou_tanki",
    "daisuushii",
    "kokushi_musou",
    "churen_poto",
    "tsuuiisou",
    "ryuuuiisou",
    "suuankou",
    "sukantsu",
    "shousuushi",
    "daisangen",
    "chinroutou",
    "tenhou",
    "chiihou",
)
