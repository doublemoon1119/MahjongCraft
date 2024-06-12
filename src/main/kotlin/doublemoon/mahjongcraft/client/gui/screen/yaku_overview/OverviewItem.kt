package doublemoon.mahjongcraft.client.gui.screen.yaku_overview

import doublemoon.mahjongcraft.MOD_ID
import doublemoon.mahjongcraft.game.mahjong.riichi.model.MahjongTile
import net.minecraft.text.Text


/**
 * 文字的翻譯從 [這裡](https://mahjongsoul.club/yaku/1han?language=en) 複製貼上
 * */
sealed class OverviewItem(
    val title: Text,
    val subtitle: Subtitle = Subtitle.None,
    val description: Text = Text.empty(),
    val tiles: List<List<MahjongTile>> = emptyList(),
) {
    /**
     * 立直
     * */
    object Riichi : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.reach"),
        subtitle = Subtitle.MenzenchinOnly,
        description = Text.translatable("$MOD_ID.gui.yaku_overview.riichi.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M1, MahjongTile.M2, MahjongTile.M3,
                MahjongTile.P4, MahjongTile.P4, MahjongTile.P4,
                MahjongTile.S5, MahjongTile.S6, MahjongTile.S7,
                MahjongTile.EAST, MahjongTile.EAST, MahjongTile.EAST,
                MahjongTile.SOUTH,
            ),
            listOf(MahjongTile.SOUTH)
        )
    )

    /**
     * 斷么九
     * */
    object Tanyao : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.tanyao"),
        description = Text.translatable("$MOD_ID.gui.yaku_overview.tanyao.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M2, MahjongTile.M3, MahjongTile.M4,
                MahjongTile.M5, MahjongTile.M6, MahjongTile.M7,
                MahjongTile.P3, MahjongTile.P3, MahjongTile.P3,
                MahjongTile.S4, MahjongTile.S5, MahjongTile.S6,
                MahjongTile.S8,
            ),
            listOf(MahjongTile.S8)
        )
    )

    /**
     * 門前清自摸和
     * */
    object Tsumo : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.tsumo"),
        subtitle = Subtitle.MenzenchinOnly,
        description = Text.translatable("$MOD_ID.gui.yaku_overview.tsumo.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M1, MahjongTile.M1, MahjongTile.M1,
                MahjongTile.M2, MahjongTile.M3, MahjongTile.M4,
                MahjongTile.P3, MahjongTile.P3, MahjongTile.P3,
                MahjongTile.S7, MahjongTile.S8, MahjongTile.S9,
                MahjongTile.SOUTH,
            ),
            listOf(MahjongTile.SOUTH)
        )
    )

    /**
     * 自風牌
     * */
    object Jikaze : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.jikaze"),
        description = Text.translatable("$MOD_ID.gui.yaku_overview.jikaze.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M3, MahjongTile.M3, MahjongTile.M3,
                MahjongTile.P4, MahjongTile.P5, MahjongTile.P6,
                MahjongTile.S7, MahjongTile.S8, MahjongTile.S9,
                MahjongTile.WEST, MahjongTile.WEST, MahjongTile.WEST,
                MahjongTile.S1,
            ),
            listOf(MahjongTile.S1)
        )
    )

    /**
     * 場風牌
     * */
    object Bakaze : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.bakaze"),
        description = Text.translatable("$MOD_ID.gui.yaku_overview.bakaze.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M3, MahjongTile.M3, MahjongTile.M3,
                MahjongTile.P4, MahjongTile.P5, MahjongTile.P6,
                MahjongTile.S7, MahjongTile.S8, MahjongTile.S9,
                MahjongTile.EAST, MahjongTile.EAST, MahjongTile.EAST,
                MahjongTile.S1,
            ),
            listOf(MahjongTile.S1)
        )
    )

    /**
     * 三元牌：白發中
     * */
    object Sangen : OverviewItem(
        title = Text.translatable("$MOD_ID.gui.yaku_overview.sangen.title"),
        description = Text.translatable("$MOD_ID.gui.yaku_overview.sangen.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M3, MahjongTile.M3, MahjongTile.M3,
                MahjongTile.P4, MahjongTile.P5, MahjongTile.P6,
                MahjongTile.S7, MahjongTile.S8, MahjongTile.S9,
                MahjongTile.WHITE_DRAGON, MahjongTile.WHITE_DRAGON, MahjongTile.WHITE_DRAGON,
                MahjongTile.S1,
            ),
            listOf(MahjongTile.S1)
        )
    )

    /**
     * 平胡
     * */
    object Pinfu : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.pinfu"),
        subtitle = Subtitle.MenzenchinOnly,
        description = Text.translatable("$MOD_ID.gui.yaku_overview.pinfu.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M1, MahjongTile.M2, MahjongTile.M3,
                MahjongTile.M5, MahjongTile.M6, MahjongTile.M7,
                MahjongTile.P2, MahjongTile.P3, MahjongTile.P4,
                MahjongTile.S6, MahjongTile.S7,
                MahjongTile.S9, MahjongTile.S9,
            ),
            listOf(MahjongTile.S5)
        )
    )

    /**
     * 一杯口
     * */
    object Ipeiko : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.ipeiko"),
        subtitle = Subtitle.MenzenchinOnly,
        description = Text.translatable("$MOD_ID.gui.yaku_overview.ipeiko.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M1, MahjongTile.M2, MahjongTile.M3,
                MahjongTile.M1, MahjongTile.M2, MahjongTile.M3,
                MahjongTile.P4, MahjongTile.P4, MahjongTile.P4,
                MahjongTile.P7, MahjongTile.P8, MahjongTile.P9,
                MahjongTile.S3,
            ),
            listOf(MahjongTile.S3)
        )
    )

    /**
     * 搶槓
     * */
    object Chankan : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.chankan"),
        description = Text.translatable("$MOD_ID.gui.yaku_overview.chankan.description"),
        tiles = listOf(
            listOf(
                MahjongTile.P1, MahjongTile.P1, MahjongTile.P1,
            ),
            listOf(MahjongTile.P1)
        )
    )

    /**
     * 嶺上開花
     * */
    object Rinshankaihoh : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.rinshankaihoh"),
        description = Text.translatable("$MOD_ID.gui.yaku_overview.rinshankaihoh.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M2, MahjongTile.M3, MahjongTile.M4,
                MahjongTile.P5, MahjongTile.P5, MahjongTile.P5,
                MahjongTile.S3, MahjongTile.S4, MahjongTile.S5, MahjongTile.S6,
                MahjongTile.S8, MahjongTile.S8, MahjongTile.S8, MahjongTile.S8,
            ),
            listOf(MahjongTile.S3)
        )
    )

    /**
     * 海底撈月
     * */
    object Haitei : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.haitei"),
        description = Text.translatable("$MOD_ID.gui.yaku_overview.haitei.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M2, MahjongTile.M2, MahjongTile.M2,
                MahjongTile.P4, MahjongTile.P5, MahjongTile.P6,
                MahjongTile.S3, MahjongTile.S3, MahjongTile.S3,
                MahjongTile.S9, MahjongTile.S9, MahjongTile.S9,
                MahjongTile.EAST,
            ),
            listOf(MahjongTile.EAST)
        )
    )

    /**
     * 河底摸魚
     * */
    object Houtei : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.houtei"),
        description = Text.translatable("$MOD_ID.gui.yaku_overview.houtei.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M2, MahjongTile.M2, MahjongTile.M2,
                MahjongTile.P4, MahjongTile.P5, MahjongTile.P6,
                MahjongTile.S3, MahjongTile.S3, MahjongTile.S3,
                MahjongTile.S9, MahjongTile.S9, MahjongTile.S9,
                MahjongTile.EAST,
            ),
            listOf(MahjongTile.EAST)
        )
    )

    /**
     * 一發
     * */
    object Ippatsu : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.ippatsu"),
        description = Text.translatable("$MOD_ID.gui.yaku_overview.ippatsu.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M1, MahjongTile.M2, MahjongTile.M3,
                MahjongTile.P4, MahjongTile.P4, MahjongTile.P4,
                MahjongTile.S5, MahjongTile.S6, MahjongTile.S7,
                MahjongTile.EAST, MahjongTile.EAST, MahjongTile.EAST,
                MahjongTile.SOUTH,
            ),
            listOf(MahjongTile.SOUTH)
        )
    )

    /**
     * 寶牌
     * */
    object Dora : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.dora"),
        subtitle = Subtitle.NotYaku,
        description = Text.translatable("$MOD_ID.gui.yaku_overview.dora.description"),
        tiles = listOf(
            listOf(
                MahjongTile.S3,
                MahjongTile.UNKNOWN, MahjongTile.UNKNOWN, MahjongTile.UNKNOWN
            ),
            listOf(MahjongTile.S4)
        )
    )

    /**
     * 赤寶牌
     * */
    object RedFive : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.red_five"),
        subtitle = Subtitle.NotYaku,
        description = Text.translatable("$MOD_ID.gui.yaku_overview.red_five.description"),
        tiles = listOf(
            listOf(MahjongTile.M5_RED),
            listOf(MahjongTile.P5_RED),
            listOf(MahjongTile.S5_RED),
        )
    )

    /**
     * 雙立直
     * */
    object DoubleRiichi : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.double_reach"),
        subtitle = Subtitle.MenzenchinOnly,
        description = Text.translatable("$MOD_ID.gui.yaku_overview.double_reach.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M1, MahjongTile.M2, MahjongTile.M3,
                MahjongTile.M4, MahjongTile.M4, MahjongTile.M4,
                MahjongTile.P5, MahjongTile.P6, MahjongTile.P7,
                MahjongTile.WHITE_DRAGON, MahjongTile.WHITE_DRAGON, MahjongTile.WHITE_DRAGON,
                MahjongTile.GREEN_DRAGON,
            ),
            listOf(MahjongTile.GREEN_DRAGON)
        )
    )

    /**
     * 三色同刻
     * */
    object Sanshokudohko : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.sanshokudohko"),
        description = Text.translatable("$MOD_ID.gui.yaku_overview.sanshokudohko.description"),
        tiles = listOf(
            listOf(MahjongTile.M3, MahjongTile.M3, MahjongTile.M3),
            listOf(MahjongTile.P3, MahjongTile.P3, MahjongTile.P3),
            listOf(MahjongTile.S3, MahjongTile.S3, MahjongTile.S3),
            listOf(MahjongTile.S5, MahjongTile.S6, MahjongTile.S7),
            listOf(MahjongTile.RED_DRAGON, MahjongTile.RED_DRAGON)
        )
    )

    /**
     * 三槓子
     * */
    object Sankantsu : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.sankantsu"),
        description = Text.translatable("$MOD_ID.gui.yaku_overview.sankantsu.description"),
        tiles = listOf(
            listOf(MahjongTile.S5, MahjongTile.S6, MahjongTile.S7),
            listOf(MahjongTile.M3, MahjongTile.M3, MahjongTile.M3, MahjongTile.M3),
            listOf(MahjongTile.P3, MahjongTile.P3, MahjongTile.P3, MahjongTile.P3),
            listOf(MahjongTile.S3, MahjongTile.S3, MahjongTile.S3, MahjongTile.S3),
            listOf(MahjongTile.RED_DRAGON, MahjongTile.RED_DRAGON)
        )
    )

    /**
     * 對對和
     * */
    object Toitoiho : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.toitoiho"),
        description = Text.translatable("$MOD_ID.gui.yaku_overview.toitoiho.description"),
        tiles = listOf(
            listOf(MahjongTile.M3, MahjongTile.M3, MahjongTile.M3),
            listOf(MahjongTile.P4, MahjongTile.P4, MahjongTile.P4),
            listOf(MahjongTile.S3, MahjongTile.S3, MahjongTile.S3),
            listOf(MahjongTile.EAST, MahjongTile.EAST, MahjongTile.EAST),
            listOf(MahjongTile.SOUTH, MahjongTile.SOUTH)
        )
    )

    /**
     * 三暗刻
     * */
    object Sananko : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.sananko"),
        description = Text.translatable("$MOD_ID.gui.yaku_overview.sananko.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M1, MahjongTile.M1, MahjongTile.M1,
                MahjongTile.P1, MahjongTile.P1, MahjongTile.P1,
                MahjongTile.S1, MahjongTile.S1, MahjongTile.S1
            ),
            listOf(MahjongTile.S3, MahjongTile.S4, MahjongTile.S5),
            listOf(MahjongTile.EAST),
            listOf(MahjongTile.EAST)
        )
    )

    /**
     * 小三元
     * */
    object Shosangen : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.shosangen"),
        description = Text.translatable("$MOD_ID.gui.yaku_overview.shosangen.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M2, MahjongTile.M3, MahjongTile.M4,
                MahjongTile.P5, MahjongTile.P6, MahjongTile.P7
            ),
            listOf(MahjongTile.WHITE_DRAGON, MahjongTile.WHITE_DRAGON, MahjongTile.WHITE_DRAGON),
            listOf(MahjongTile.GREEN_DRAGON, MahjongTile.GREEN_DRAGON, MahjongTile.GREEN_DRAGON),
            listOf(MahjongTile.RED_DRAGON),
            listOf(MahjongTile.RED_DRAGON)
        )
    )

    /**
     * 混老頭
     * */
    object Honrohtoh : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.honrohtoh"),
        description = Text.translatable("$MOD_ID.gui.yaku_overview.honrohtoh.description"),
        tiles = listOf(
            listOf(MahjongTile.M1, MahjongTile.M1, MahjongTile.M1),
            listOf(MahjongTile.M9, MahjongTile.M9, MahjongTile.M9),
            listOf(MahjongTile.P1, MahjongTile.P1, MahjongTile.P1),
            listOf(MahjongTile.S1, MahjongTile.S1, MahjongTile.S1),
            listOf(MahjongTile.EAST),
            listOf(MahjongTile.EAST)
        )
    )

    /**
     * 七對子
     * */
    object Chitoitsu : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.chitoitsu"),
        subtitle = Subtitle.MenzenchinOnly,
        description = Text.translatable("$MOD_ID.gui.yaku_overview.chitoitsu.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M1, MahjongTile.M1,
                MahjongTile.M2, MahjongTile.M2,
                MahjongTile.P3, MahjongTile.P3,
                MahjongTile.P4, MahjongTile.P4,
                MahjongTile.P6, MahjongTile.P6,
                MahjongTile.S7, MahjongTile.S7,
                MahjongTile.NORTH,
            ),
            listOf(MahjongTile.NORTH)
        )
    )

    /**
     * 混全帶么九
     * */
    object Chanta : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.chanta"),
        subtitle = Subtitle.MinusOneHanAfterMakingACall,
        description = Text.translatable("$MOD_ID.gui.yaku_overview.chanta.description"),
        tiles = listOf(
            listOf(MahjongTile.M1, MahjongTile.M2, MahjongTile.M3),
            listOf(MahjongTile.M7, MahjongTile.M8, MahjongTile.M9),
            listOf(MahjongTile.P1, MahjongTile.P2, MahjongTile.P3),
            listOf(MahjongTile.EAST, MahjongTile.EAST, MahjongTile.EAST),
            listOf(MahjongTile.GREEN_DRAGON, MahjongTile.GREEN_DRAGON),
        )
    )

    /**
     * 一氣通貫
     * */
    object Ikkitsukan : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.ikkitsukan"),
        subtitle = Subtitle.MinusOneHanAfterMakingACall,
        description = Text.translatable("$MOD_ID.gui.yaku_overview.ikkitsukan.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M1, MahjongTile.M2, MahjongTile.M3,
                MahjongTile.M4, MahjongTile.M5, MahjongTile.M6,
                MahjongTile.M7, MahjongTile.M8, MahjongTile.M9,
            ),
            listOf(MahjongTile.P1, MahjongTile.P1, MahjongTile.P1),
            listOf(MahjongTile.EAST, MahjongTile.EAST),
        )
    )

    /**
     * 三色同順
     * */
    object Sanshokudohjun : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.sanshokudohjun"),
        subtitle = Subtitle.MinusOneHanAfterMakingACall,
        description = Text.translatable("$MOD_ID.gui.yaku_overview.sanshokudohjun.description"),
        tiles = listOf(
            listOf(MahjongTile.M1, MahjongTile.M2, MahjongTile.M3),
            listOf(MahjongTile.P1, MahjongTile.P2, MahjongTile.P3),
            listOf(MahjongTile.S1, MahjongTile.S2, MahjongTile.S3),
            listOf(MahjongTile.S6, MahjongTile.S6, MahjongTile.S6),
            listOf(MahjongTile.WEST, MahjongTile.WEST),
        )
    )

    /**
     * 兩杯口
     * */
    object Ryanpeiko : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.ryanpeiko"),
        subtitle = Subtitle.MenzenchinOnly,
        description = Text.translatable("$MOD_ID.gui.yaku_overview.ryanpeiko.description"),
        tiles = listOf(
            listOf(
                MahjongTile.S1, MahjongTile.S2, MahjongTile.S3,
                MahjongTile.S1, MahjongTile.S2, MahjongTile.S3,
            ),
            listOf(
                MahjongTile.P2, MahjongTile.P2, MahjongTile.P4,
                MahjongTile.P2, MahjongTile.P2, MahjongTile.P4,
            ),
            listOf(MahjongTile.EAST, MahjongTile.EAST),
        )
    )

    /**
     * 純全帶么九
     * */
    object Junchan : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.junchan"),
        subtitle = Subtitle.MinusOneHanAfterMakingACall,
        description = Text.translatable("$MOD_ID.gui.yaku_overview.junchan.description"),
        tiles = listOf(
            listOf(MahjongTile.M1, MahjongTile.M2, MahjongTile.M3),
            listOf(MahjongTile.M7, MahjongTile.M8, MahjongTile.M9),
            listOf(MahjongTile.P1, MahjongTile.P2, MahjongTile.P3),
            listOf(MahjongTile.S9, MahjongTile.S9, MahjongTile.S9),
            listOf(MahjongTile.S1, MahjongTile.S1),
        )
    )

    /**
     * 混一色
     * */
    object Honitsu : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.honitsu"),
        subtitle = Subtitle.MinusOneHanAfterMakingACall,
        description = Text.translatable("$MOD_ID.gui.yaku_overview.honitsu.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M1, MahjongTile.M1, MahjongTile.M1,
                MahjongTile.M2, MahjongTile.M3, MahjongTile.M4,
                MahjongTile.M5, MahjongTile.M6, MahjongTile.M7,
                MahjongTile.SOUTH, MahjongTile.SOUTH, MahjongTile.SOUTH,
            ),
            listOf(MahjongTile.WHITE_DRAGON, MahjongTile.WHITE_DRAGON),
        )
    )

    /**
     * 清一色
     * */
    object Chinitsu : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.chinitsu"),
        subtitle = Subtitle.MinusOneHanAfterMakingACall,
        description = Text.translatable("$MOD_ID.gui.yaku_overview.chinitsu.description"),
        tiles = listOf(
            listOf(MahjongTile.M1, MahjongTile.M2, MahjongTile.M3),
            listOf(MahjongTile.M2, MahjongTile.M3, MahjongTile.M4),
            listOf(MahjongTile.M3, MahjongTile.M4, MahjongTile.M5),
            listOf(MahjongTile.M6, MahjongTile.M6, MahjongTile.M6),
            listOf(MahjongTile.M9, MahjongTile.M9),
        )
    )

    /**
     * 流局滿貫
     * */
    object NagashiMangan : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.nagashi_mangan"),
        description = Text.translatable("$MOD_ID.gui.yaku_overview.nagashi_mangan.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M9, MahjongTile.M1,
                MahjongTile.EAST, MahjongTile.EAST,
                MahjongTile.SOUTH, MahjongTile.SOUTH,
                MahjongTile.RED_DRAGON, MahjongTile.SOUTH,
                MahjongTile.WEST, MahjongTile.WEST, MahjongTile.WEST,
                MahjongTile.P9, MahjongTile.P9, MahjongTile.P9,
                MahjongTile.S9, MahjongTile.GREEN_DRAGON, MahjongTile.NORTH, MahjongTile.M9
            ),
        )
    )

    /**
     * 天和
     * */
    object Tenho : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.tenho"),
        subtitle = Subtitle.DealerOnly,
        description = Text.translatable("$MOD_ID.gui.yaku_overview.tenho.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M1, MahjongTile.M2, MahjongTile.M3,
                MahjongTile.P2, MahjongTile.P3, MahjongTile.P4,
                MahjongTile.S5, MahjongTile.S6, MahjongTile.S7,
                MahjongTile.EAST, MahjongTile.EAST, MahjongTile.EAST,
                MahjongTile.SOUTH, MahjongTile.SOUTH,
            ),
        )
    )

    /**
     * 地和
     * */
    object Chiho : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.chiho"),
        subtitle = Subtitle.NonDealerOnly,
        description = Text.translatable("$MOD_ID.gui.yaku_overview.chiho.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M1, MahjongTile.M2, MahjongTile.M3,
                MahjongTile.P2, MahjongTile.P3, MahjongTile.P4,
                MahjongTile.S5, MahjongTile.S6, MahjongTile.S7,
                MahjongTile.EAST, MahjongTile.EAST, MahjongTile.EAST,
                MahjongTile.SOUTH
            ),
            listOf(MahjongTile.SOUTH)
        )
    )

    /**
     * 大三元
     * */
    object Daisangen : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.daisangen"),
        description = Text.translatable("$MOD_ID.gui.yaku_overview.daisangen.description"),
        tiles = listOf(
            listOf(
                MahjongTile.S1, MahjongTile.S2, MahjongTile.S3,
                MahjongTile.WHITE_DRAGON, MahjongTile.WHITE_DRAGON, MahjongTile.WHITE_DRAGON,
                MahjongTile.GREEN_DRAGON, MahjongTile.GREEN_DRAGON, MahjongTile.GREEN_DRAGON,
                MahjongTile.RED_DRAGON, MahjongTile.RED_DRAGON, MahjongTile.RED_DRAGON,
                MahjongTile.S9, MahjongTile.S9,
            ),
        )
    )

    /**
     * 四暗刻
     * */
    object Suanko : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.suanko"),
        subtitle = Subtitle.MenzenchinOnly,
        description = Text.translatable("$MOD_ID.gui.yaku_overview.suanko.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M1, MahjongTile.M1, MahjongTile.M1,
                MahjongTile.M2, MahjongTile.M2, MahjongTile.M2,
                MahjongTile.P3, MahjongTile.P3, MahjongTile.P3,
                MahjongTile.P4, MahjongTile.P4, MahjongTile.P4,
            ),
            listOf(MahjongTile.S5, MahjongTile.S5)
        )
    )

    /**
     * 字一色
     * */
    object Tsuiso : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.tsuiso"),
        description = Text.translatable("$MOD_ID.gui.yaku_overview.tsuiso.description"),
        tiles = listOf(
            listOf(MahjongTile.EAST, MahjongTile.EAST, MahjongTile.EAST),
            listOf(MahjongTile.SOUTH, MahjongTile.SOUTH, MahjongTile.SOUTH),
            listOf(MahjongTile.WEST, MahjongTile.WEST, MahjongTile.WEST),
            listOf(MahjongTile.WHITE_DRAGON, MahjongTile.WHITE_DRAGON, MahjongTile.WHITE_DRAGON),
            listOf(MahjongTile.GREEN_DRAGON, MahjongTile.GREEN_DRAGON)
        )
    )

    /**
     * 綠一色
     * */
    object Ryuiso : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.ryuiso"),
        subtitle = Subtitle.GreenDragonIsNotNecessary,
        description = Text.translatable("$MOD_ID.gui.yaku_overview.ryuiso.description"),
        tiles = listOf(
            listOf(MahjongTile.S2, MahjongTile.S2, MahjongTile.S2),
            listOf(MahjongTile.S3, MahjongTile.S3, MahjongTile.S3),
            listOf(MahjongTile.S4, MahjongTile.S4, MahjongTile.S4),
            listOf(MahjongTile.S6, MahjongTile.S6, MahjongTile.S6),
            listOf(MahjongTile.GREEN_DRAGON, MahjongTile.GREEN_DRAGON)
        )
    )

    /**
     * 清老頭
     * */
    object Chinroto : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.chinroto"),
        description = Text.translatable("$MOD_ID.gui.yaku_overview.chinroto.description"),
        tiles = listOf(
            listOf(MahjongTile.M1, MahjongTile.M1, MahjongTile.M1),
            listOf(MahjongTile.M9, MahjongTile.M9, MahjongTile.M9),
            listOf(MahjongTile.P1, MahjongTile.P1, MahjongTile.P1),
            listOf(MahjongTile.P9, MahjongTile.P9, MahjongTile.P9),
            listOf(MahjongTile.S1, MahjongTile.S1)
        )
    )

    /**
     * 國士無雙
     * */
    object Kokushimuso : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.kokushimuso"),
        subtitle = Subtitle.MenzenchinOnly,
        description = Text.translatable("$MOD_ID.gui.yaku_overview.kokushimuso.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M1, MahjongTile.M9,
                MahjongTile.P1, MahjongTile.P9, MahjongTile.P9,
                MahjongTile.S1, MahjongTile.S9,
                MahjongTile.EAST, MahjongTile.SOUTH, MahjongTile.WEST, MahjongTile.NORTH,
                MahjongTile.WHITE_DRAGON, MahjongTile.GREEN_DRAGON,
            ),
            listOf(MahjongTile.RED_DRAGON)
        )
    )

    /**
     * 小四喜
     * */
    object Shosushi : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.shosushi"),
        description = Text.translatable("$MOD_ID.gui.yaku_overview.shosushi.description"),
        tiles = listOf(
            listOf(MahjongTile.P1, MahjongTile.P2, MahjongTile.P3),
            listOf(MahjongTile.EAST, MahjongTile.EAST, MahjongTile.EAST),
            listOf(MahjongTile.SOUTH, MahjongTile.SOUTH, MahjongTile.SOUTH),
            listOf(MahjongTile.WEST, MahjongTile.WEST, MahjongTile.WEST),
            listOf(MahjongTile.NORTH, MahjongTile.NORTH)
        )
    )

    /**
     * 四槓子
     * */
    object Sukantsu : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.sukantsu"),
        description = Text.translatable("$MOD_ID.gui.yaku_overview.sukantsu.description"),
        tiles = listOf(
            listOf(MahjongTile.M1, MahjongTile.M1, MahjongTile.M1, MahjongTile.M1),
            listOf(MahjongTile.P2, MahjongTile.P2, MahjongTile.P2, MahjongTile.P2),
            listOf(MahjongTile.S3, MahjongTile.S3, MahjongTile.S3, MahjongTile.S3),
            listOf(MahjongTile.EAST, MahjongTile.EAST, MahjongTile.EAST, MahjongTile.EAST),
            listOf(MahjongTile.GREEN_DRAGON, MahjongTile.GREEN_DRAGON)
        )
    )

    /**
     * 九蓮寶燈
     * */
    object Churenpohto : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.churenpohto"),
        subtitle = Subtitle.MenzenchinOnly,
        description = Text.translatable("$MOD_ID.gui.yaku_overview.churenpohto.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M1, MahjongTile.M1, MahjongTile.M1, MahjongTile.M1,
                MahjongTile.M2, MahjongTile.M3, MahjongTile.M4,
                MahjongTile.M5, MahjongTile.M6, MahjongTile.M7,
                MahjongTile.M8, MahjongTile.M9, MahjongTile.M9,
            ),
            listOf(MahjongTile.M9)
        )
    )

    /**
     * 四暗刻單騎
     * */
    object SuankoTanki : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.suanko_tanki"),
        subtitle = Subtitle.MenzenchinOnly,
        description = Text.translatable("$MOD_ID.gui.yaku_overview.suanko_tanki.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M1, MahjongTile.M1, MahjongTile.M1,
                MahjongTile.P2, MahjongTile.P2, MahjongTile.P2,
                MahjongTile.P5, MahjongTile.P5, MahjongTile.P5,
                MahjongTile.S7, MahjongTile.S7, MahjongTile.S7,
                MahjongTile.NORTH,
            ),
            listOf(MahjongTile.NORTH)
        )
    )

    /**
     * 國士無雙十三面聽
     * */
    object KokushimusoJusanmenmachi : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.kokushimuso_jusanmenmachi"),
        subtitle = Subtitle.MenzenchinOnly,
        description = Text.translatable("$MOD_ID.gui.yaku_overview.kokushimuso_jusanmenmachi.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M1, MahjongTile.M9,
                MahjongTile.P1, MahjongTile.P9,
                MahjongTile.S1, MahjongTile.S9,
                MahjongTile.EAST, MahjongTile.SOUTH, MahjongTile.WEST, MahjongTile.NORTH,
                MahjongTile.WHITE_DRAGON, MahjongTile.GREEN_DRAGON, MahjongTile.RED_DRAGON,
            ),
            listOf(MahjongTile.M1)
        )
    )

    /**
     * 純正九蓮寶燈
     * */
    object JunseiChurenpohto : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.junsei_churenpohto"),
        subtitle = Subtitle.MenzenchinOnly,
        description = Text.translatable("$MOD_ID.gui.yaku_overview.junsei_churenpohto.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M1, MahjongTile.M1, MahjongTile.M1,
                MahjongTile.M2, MahjongTile.M3, MahjongTile.M4,
                MahjongTile.M5, MahjongTile.M6, MahjongTile.M7,
                MahjongTile.M8,
                MahjongTile.M9, MahjongTile.M9, MahjongTile.M9,
            ),
            listOf(MahjongTile.M5)
        )
    )

    /**
     * 大四喜
     * */
    object Daisushi : OverviewItem(
        title = Text.translatable("$MOD_ID.game.yaku.daisushi"),
        description = Text.translatable("$MOD_ID.gui.yaku_overview.daisushi.description"),
        tiles = listOf(
            listOf(MahjongTile.EAST, MahjongTile.EAST, MahjongTile.EAST),
            listOf(MahjongTile.SOUTH, MahjongTile.SOUTH, MahjongTile.SOUTH),
            listOf(MahjongTile.WEST, MahjongTile.WEST, MahjongTile.WEST),
            listOf(MahjongTile.NORTH, MahjongTile.NORTH, MahjongTile.NORTH),
            listOf(MahjongTile.P5, MahjongTile.P5),
        )
    )

    /**
     * 四風連打
     * */
    object SuufonRenda : OverviewItem(
        title = Text.translatable("$MOD_ID.game.exhaustive_draw.suufon_renda"),
        description = Text.translatable("$MOD_ID.gui.yaku_overview.suufon_renda.description"),
        tiles = listOf(
            listOf(MahjongTile.NORTH),
            listOf(MahjongTile.NORTH),
            listOf(MahjongTile.NORTH),
            listOf(MahjongTile.NORTH),
        )
    )

    /**
     * 四開槓
     * */
    object Suukaikan : OverviewItem(
        title = Text.translatable("$MOD_ID.game.exhaustive_draw.suukaikan"),
        description = Text.translatable("$MOD_ID.gui.yaku_overview.suukaikan.description"),
        tiles = listOf(
            listOf(MahjongTile.M1, MahjongTile.M1, MahjongTile.M1, MahjongTile.M1),
            listOf(MahjongTile.P2, MahjongTile.P2, MahjongTile.P2, MahjongTile.P2),
            listOf(MahjongTile.P5, MahjongTile.P5, MahjongTile.P5, MahjongTile.P5),
            listOf(MahjongTile.S9, MahjongTile.S9, MahjongTile.S9, MahjongTile.S9),
        )
    )

    /**
     * 九種九牌
     * */
    object KyuushuKyuuhai : OverviewItem(
        title = Text.translatable("$MOD_ID.game.exhaustive_draw.kyuushu_kyuuhai"),
        description = Text.translatable("$MOD_ID.gui.yaku_overview.kyuushu_kyuuhai.description"),
        tiles = listOf(
            listOf(
                MahjongTile.M1, MahjongTile.M9,
                MahjongTile.P1, MahjongTile.P8, MahjongTile.P9,
                MahjongTile.S3, MahjongTile.S3, MahjongTile.S4, MahjongTile.S7, MahjongTile.S9,
                MahjongTile.EAST, MahjongTile.WEST, MahjongTile.GREEN_DRAGON,
            ),
            listOf(MahjongTile.RED_DRAGON),
        )
    )

    /**
     * 四家立直
     * */
    object SuuchaRiichi : OverviewItem(
        title = Text.translatable("$MOD_ID.game.exhaustive_draw.suucha_riichi"),
        description = Text.translatable("$MOD_ID.gui.yaku_overview.suucha_riichi.description"),
    )

    enum class Subtitle(val text: Text) {
        None(Text.empty()),
        MenzenchinOnly(Text.translatable("$MOD_ID.gui.yaku_overview.menzenchin_only")),
        NotYaku(Text.translatable("$MOD_ID.gui.yaku_overview.not_yaku")),
        DealerOnly(Text.translatable("$MOD_ID.gui.yaku_overview.dealer_only")),
        NonDealerOnly(Text.translatable("$MOD_ID.gui.yaku_overview.non_dealer_only")),
        MinusOneHanAfterMakingACall(Text.translatable("$MOD_ID.gui.yaku_overview.minus_one_han_after_making_a_call")),
        GreenDragonIsNotNecessary(Text.translatable("$MOD_ID.gui.yaku_overview.green_dragon_is_not_necessary")),
        ;
    }
}

