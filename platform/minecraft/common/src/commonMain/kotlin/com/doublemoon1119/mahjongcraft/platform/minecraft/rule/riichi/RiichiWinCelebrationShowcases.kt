package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInWinCelebrationCueIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.ShowcasePalette
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseDefinition

/** 所有日麻役滿展示定義；標題 key 與貼圖都放在日麻專屬的命名下，越前面的役滿優先序越高。 */
internal fun riichiWinCelebrationShowcases(): List<WinCelebrationShowcaseDefinition> = YAKUMAN_SHOWCASES.mapIndexed { index, showcase ->
    WinCelebrationShowcaseDefinition(
        cueKey = BuiltInWinCelebrationCueIds.riichiYakuman(showcase.cue),
        titleTranslationKey = showcase.titleTranslationKey,
        titleImageResourceId = showcase.titleImageResourceId,
        palette = ShowcasePalette(primary = 0xFFFFD45A.toInt(), secondary = 0xFFC32128.toInt(), accent = 0xFFFFFFFF.toInt()),
        priority = YAKUMAN_SHOWCASES.size - index,
    )
}

/**
 * 一個日麻役滿的展示資料；標題 key 與貼圖逐一寫明，不從 cue 名稱推導。
 *
 * @property cue 日麻役滿 cue 的役種名稱。
 * @property titleTranslationKey 展示標題的 translation key。
 * @property titleImageResourceId 展示標題貼圖的資源 ID。
 */
private data class RiichiYakumanShowcase(
    val cue: String,
    val titleTranslationKey: String,
    val titleImageResourceId: String,
)

/** 日麻役滿展示，依挑選優先序由高到低排列。 */
private val YAKUMAN_SHOWCASES = listOf(
    RiichiYakumanShowcase("kokushi_musou_13", RiichiShowcaseTranslationKeys.KOKUSHI_MUSOU_13, "mahjongcraft:textures/showcase/riichi/kokushi_musou_13.png"),
    RiichiYakumanShowcase("churen_poto_9", RiichiShowcaseTranslationKeys.CHUREN_POTO_9, "mahjongcraft:textures/showcase/riichi/churen_poto_9.png"),
    RiichiYakumanShowcase("suuankou_tanki", RiichiShowcaseTranslationKeys.SUUANKOU_TANKI, "mahjongcraft:textures/showcase/riichi/suuankou_tanki.png"),
    RiichiYakumanShowcase("daisuushii", RiichiShowcaseTranslationKeys.DAISUUSHII, "mahjongcraft:textures/showcase/riichi/daisuushii.png"),
    RiichiYakumanShowcase("kokushi_musou", RiichiShowcaseTranslationKeys.KOKUSHI_MUSOU, "mahjongcraft:textures/showcase/riichi/kokushi_musou.png"),
    RiichiYakumanShowcase("churen_poto", RiichiShowcaseTranslationKeys.CHUREN_POTO, "mahjongcraft:textures/showcase/riichi/churen_poto.png"),
    RiichiYakumanShowcase("tsuuiisou", RiichiShowcaseTranslationKeys.TSUUIISOU, "mahjongcraft:textures/showcase/riichi/tsuuiisou.png"),
    RiichiYakumanShowcase("ryuuuiisou", RiichiShowcaseTranslationKeys.RYUUUIISOU, "mahjongcraft:textures/showcase/riichi/ryuuuiisou.png"),
    RiichiYakumanShowcase("suuankou", RiichiShowcaseTranslationKeys.SUUANKOU, "mahjongcraft:textures/showcase/riichi/suuankou.png"),
    RiichiYakumanShowcase("sukantsu", RiichiShowcaseTranslationKeys.SUKANTSU, "mahjongcraft:textures/showcase/riichi/sukantsu.png"),
    RiichiYakumanShowcase("shousuushi", RiichiShowcaseTranslationKeys.SHOUSUUSHI, "mahjongcraft:textures/showcase/riichi/shousuushi.png"),
    RiichiYakumanShowcase("daisangen", RiichiShowcaseTranslationKeys.DAISANGEN, "mahjongcraft:textures/showcase/riichi/daisangen.png"),
    RiichiYakumanShowcase("chinroutou", RiichiShowcaseTranslationKeys.CHINROUTOU, "mahjongcraft:textures/showcase/riichi/chinroutou.png"),
    RiichiYakumanShowcase("tenhou", RiichiShowcaseTranslationKeys.TENHOU, "mahjongcraft:textures/showcase/riichi/tenhou.png"),
    RiichiYakumanShowcase("chiihou", RiichiShowcaseTranslationKeys.CHIIHOU, "mahjongcraft:textures/showcase/riichi/chiihou.png"),
    RiichiYakumanShowcase("daichisei", RiichiShowcaseTranslationKeys.DAICHISEI, "mahjongcraft:textures/showcase/riichi/daichisei.png"),
    RiichiYakumanShowcase("renhou", RiichiShowcaseTranslationKeys.RENHOU, "mahjongcraft:textures/showcase/riichi/renhou.png"),
    RiichiYakumanShowcase("daisharin", RiichiShowcaseTranslationKeys.DAISHARIN, "mahjongcraft:textures/showcase/riichi/daisharin.png"),
    RiichiYakumanShowcase("daichikurin", RiichiShowcaseTranslationKeys.DAICHIKURIN, "mahjongcraft:textures/showcase/riichi/daichikurin.png"),
    RiichiYakumanShowcase("daisuurin", RiichiShowcaseTranslationKeys.DAISUURIN, "mahjongcraft:textures/showcase/riichi/daisuurin.png"),
    RiichiYakumanShowcase("ishino_ue_sannen", RiichiShowcaseTranslationKeys.ISHINO_UE_SANNEN, "mahjongcraft:textures/showcase/riichi/ishino_ue_sannen.png"),
)
