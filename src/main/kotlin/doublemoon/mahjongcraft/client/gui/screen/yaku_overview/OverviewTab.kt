package doublemoon.mahjongcraft.client.gui.screen.yaku_overview

import doublemoon.mahjongcraft.MOD_ID
import net.minecraft.text.Text

enum class OverviewTab(
    val title: Text,
    val items: List<OverviewItem>,
) {
    Han1(
        title = Text.translatable("$MOD_ID.gui.yaku_overview.han", 1),
        items = listOf(
            OverviewItem.Riichi,
            OverviewItem.Tanyao,
            OverviewItem.Tsumo,
            OverviewItem.Jikaze,
            OverviewItem.Bakaze,
            OverviewItem.Sangen,
            OverviewItem.Pinfu,
            OverviewItem.Ipeiko,
            OverviewItem.Chankan,
            OverviewItem.Rinshankaihoh,
            OverviewItem.Haitei,
            OverviewItem.Houtei,
            OverviewItem.Ippatsu,
            OverviewItem.Dora,
            OverviewItem.RedFive,
        )
    ),
    Han2(
        title = Text.translatable("$MOD_ID.gui.yaku_overview.han", 2),
        items = listOf(
            OverviewItem.DoubleRiichi,
            OverviewItem.Sanshokudohko,
            OverviewItem.Sankantsu,
            OverviewItem.Toitoiho,
            OverviewItem.Sananko,
            OverviewItem.Shosangen,
            OverviewItem.Honrohtoh,
            OverviewItem.Chitoitsu,
            OverviewItem.Chanta,
            OverviewItem.Ikkitsukan,
            OverviewItem.Sanshokudohjun,
        )
    ),
    Han3(
        title = Text.translatable("$MOD_ID.gui.yaku_overview.han", 3),
        items = listOf(
            OverviewItem.Ryanpeiko,
            OverviewItem.Junchan,
            OverviewItem.Honitsu,
        )
    ),
    Han6(
        title = Text.translatable("$MOD_ID.gui.yaku_overview.han", 6),
        items = listOf(
            OverviewItem.Chinitsu,
        )
    ),
    Mangan(
        title = Text.translatable("$MOD_ID.game.score.mangan"),
        items = listOf(
            OverviewItem.NagashiMangan,
        )
    ),
    Yakuman(
        title = Text.translatable("$MOD_ID.game.score.yakuman_1x"),
        items = listOf(
            OverviewItem.Tenho,
            OverviewItem.Chiho,
            OverviewItem.Daisangen,
            OverviewItem.Suanko,
            OverviewItem.Tsuiso,
            OverviewItem.Ryuiso,
            OverviewItem.Chinroto,
            OverviewItem.Kokushimuso,
            OverviewItem.Shosushi,
            OverviewItem.Sukantsu,
            OverviewItem.Churenpohto,
        )
    ),
    DoubleYakuman(
        title = Text.translatable("$MOD_ID.game.score.yakuman_2x"),
        items = listOf(
            OverviewItem.SuankoTanki,
            OverviewItem.KokushimusoJusanmenmachi,
            OverviewItem.JunseiChurenpohto,
            OverviewItem.Daisushi,
        )
    ),
    Draw(
        title = Text.translatable("$MOD_ID.gui.yaku_overview.draw"),
        items = listOf(
            OverviewItem.SuufonRenda,
            OverviewItem.Suukaikan,
            OverviewItem.KyuushuKyuuhai,
            OverviewItem.SuuchaRiichi,
        )
    ),
    ;
}