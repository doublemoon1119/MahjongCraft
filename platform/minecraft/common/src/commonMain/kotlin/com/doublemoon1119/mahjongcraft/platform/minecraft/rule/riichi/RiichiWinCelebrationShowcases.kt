package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiWinCelebrationCueIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiYakuType
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.ShowcasePalette
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseDefinition

/** 所有日麻役滿展示定義；標題 key 與貼圖都放在日麻專屬的命名下，越前面的役滿優先序越高。 */
internal fun riichiWinCelebrationShowcases(): List<WinCelebrationShowcaseDefinition> = YAKUMAN_SHOWCASES.mapIndexed { index, showcase ->
    WinCelebrationShowcaseDefinition(
        cueKey = RiichiWinCelebrationCueIds.yakuman(showcase.type),
        titleTranslationKey = showcase.titleTranslationKey,
        titleImageResourceId = showcase.titleImageResourceId,
        palette = ShowcasePalette(primary = 0xFFFFD45A.toInt(), secondary = 0xFFC32128.toInt(), accent = 0xFFFFFFFF.toInt()),
        priority = YAKUMAN_SHOWCASES.size - index,
    )
}

/**
 * 一個日麻役滿的展示資料；標題 key 與貼圖逐一寫明，不從役種名稱推導。
 *
 * @property type 展示的役滿役種。
 * @property titleTranslationKey 展示標題的 translation key。
 * @property titleImageResourceId 展示標題貼圖的資源 ID。
 */
private data class RiichiYakumanShowcase(
    val type: RiichiYakuType,
    val titleTranslationKey: String,
    val titleImageResourceId: String,
)

/** 日麻役滿展示，依挑選優先序由高到低排列。 */
private val YAKUMAN_SHOWCASES = listOf(
    RiichiYakumanShowcase(RiichiYakuType.KokushiMusou13, RiichiShowcaseTranslationKeys.KOKUSHI_MUSOU_13, "mahjongcraft:textures/showcase/riichi/kokushi_musou_13.png"),
    RiichiYakumanShowcase(RiichiYakuType.ChurenPoto9, RiichiShowcaseTranslationKeys.CHUREN_POTO_9, "mahjongcraft:textures/showcase/riichi/churen_poto_9.png"),
    RiichiYakumanShowcase(RiichiYakuType.SuuankouTanki, RiichiShowcaseTranslationKeys.SUUANKOU_TANKI, "mahjongcraft:textures/showcase/riichi/suuankou_tanki.png"),
    RiichiYakumanShowcase(RiichiYakuType.Daisuushii, RiichiShowcaseTranslationKeys.DAISUUSHII, "mahjongcraft:textures/showcase/riichi/daisuushii.png"),
    RiichiYakumanShowcase(RiichiYakuType.KokushiMusou, RiichiShowcaseTranslationKeys.KOKUSHI_MUSOU, "mahjongcraft:textures/showcase/riichi/kokushi_musou.png"),
    RiichiYakumanShowcase(RiichiYakuType.ChurenPoto, RiichiShowcaseTranslationKeys.CHUREN_POTO, "mahjongcraft:textures/showcase/riichi/churen_poto.png"),
    RiichiYakumanShowcase(RiichiYakuType.Tsuuiisou, RiichiShowcaseTranslationKeys.TSUUIISOU, "mahjongcraft:textures/showcase/riichi/tsuuiisou.png"),
    RiichiYakumanShowcase(RiichiYakuType.Ryuuuiisou, RiichiShowcaseTranslationKeys.RYUUUIISOU, "mahjongcraft:textures/showcase/riichi/ryuuuiisou.png"),
    RiichiYakumanShowcase(RiichiYakuType.Suuankou, RiichiShowcaseTranslationKeys.SUUANKOU, "mahjongcraft:textures/showcase/riichi/suuankou.png"),
    RiichiYakumanShowcase(RiichiYakuType.Sukantsu, RiichiShowcaseTranslationKeys.SUKANTSU, "mahjongcraft:textures/showcase/riichi/sukantsu.png"),
    RiichiYakumanShowcase(RiichiYakuType.Shousuushi, RiichiShowcaseTranslationKeys.SHOUSUUSHI, "mahjongcraft:textures/showcase/riichi/shousuushi.png"),
    RiichiYakumanShowcase(RiichiYakuType.Daisangen, RiichiShowcaseTranslationKeys.DAISANGEN, "mahjongcraft:textures/showcase/riichi/daisangen.png"),
    RiichiYakumanShowcase(RiichiYakuType.Chinroutou, RiichiShowcaseTranslationKeys.CHINROUTOU, "mahjongcraft:textures/showcase/riichi/chinroutou.png"),
    RiichiYakumanShowcase(RiichiYakuType.Tenhou, RiichiShowcaseTranslationKeys.TENHOU, "mahjongcraft:textures/showcase/riichi/tenhou.png"),
    RiichiYakumanShowcase(RiichiYakuType.Chiihou, RiichiShowcaseTranslationKeys.CHIIHOU, "mahjongcraft:textures/showcase/riichi/chiihou.png"),
    RiichiYakumanShowcase(RiichiYakuType.Daichisei, RiichiShowcaseTranslationKeys.DAICHISEI, "mahjongcraft:textures/showcase/riichi/daichisei.png"),
    RiichiYakumanShowcase(RiichiYakuType.Renhou, RiichiShowcaseTranslationKeys.RENHOU, "mahjongcraft:textures/showcase/riichi/renhou.png"),
    RiichiYakumanShowcase(RiichiYakuType.Daisharin, RiichiShowcaseTranslationKeys.DAISHARIN, "mahjongcraft:textures/showcase/riichi/daisharin.png"),
    RiichiYakumanShowcase(RiichiYakuType.Daichikurin, RiichiShowcaseTranslationKeys.DAICHIKURIN, "mahjongcraft:textures/showcase/riichi/daichikurin.png"),
    RiichiYakumanShowcase(RiichiYakuType.Daisuurin, RiichiShowcaseTranslationKeys.DAISUURIN, "mahjongcraft:textures/showcase/riichi/daisuurin.png"),
    RiichiYakumanShowcase(RiichiYakuType.IshinoUeSannen, RiichiShowcaseTranslationKeys.ISHINO_UE_SANNEN, "mahjongcraft:textures/showcase/riichi/ishino_ue_sannen.png"),
)
