package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku

/**
 * 日本麻將役種識別列舉。
 *
 * 包含所有可計算番數的役種，由 [RiichiHandValueCalculator] 進行檢測與計算。
 */
enum class YakuType {
    // ===== 寶牌 =====
    /** 寶牌 (Dora) */
    Dora,
    /** 裏寶牌 (Ura Dora) */
    UraDora,
    /** 赤寶牌 (Aka Dora) */
    AkaDora,

    // ===== 一般役 (1-6 翻) =====
    /** 斷么九 (Tanyao) - 1 翻 */
    Tanyao,
    /** 平和 (Pinfu) - 1 翻 */
    Pinfu,
    /** 一杯口 (Iipeikou) - 1 翻 */
    Iipeikou,
    /** 立直 (Riichi) - 1 翻 */
    Riichi,
    /** 雙立直 (Riichi) - 2 翻 */
    DoubleRiichi,
    /** 一發 (Ippatsu) - 1 翻 */
    Ippatsu,
    /** 嶺上花 (Rinshan Kaihou) - 1 翻 */
    RinshanKaihou,
    /** 海底撈月 (Haitei Raoyue) - 1 翻 */
    Haitei,
    /** 河底撈魚 (Houtei Raoyue) - 1 翻 */
    Houtei,
    /** 搶槓 (Chankan) - 1 翻 */
    Chankan,
    /** 門前清自摸 (Menzentsumo) - 1 翻 */
    Menzentsumo,

    /** 對對胡 (Toitoi) - 2 翻 */
    Toitoi,
    /** 三暗刻 (Sanankou) - 2 翻 */
    Sanankou,
    /** 三杠子 (Sankantsu) - 2 翻 */
    Sankantsu,
    /** 三色同刻 (Sanshoku Dokoku) - 2 翻 */
    SanshokuDokoku,
    /** 三色同順 (Sanshoku Doujun) - 1 翻（副露）/ 2 翻（門前清） */
    SanshokuDoujun,
    /** 混全帶么九 (Honchan) - 1 翻（副露）/ 2 翻（門前清） */
    Honchan,
    /** 純全帶么九 (Junchan) - 2 翻（副露）/ 3 翻（門前清） */
    Junchan,
    /** 混一色 (Honitsu) - 2 翻（副露）/ 3 翻（門前清） */
    Honitsu,
    /** 兩杯口 (Ryanpeikou) - 3 翻 */
    Ryanpeikou,
    /** 一氣通貫 (Ittuitsu) - 1 翻（副露）/ 2 翻（門前清） */
    Ittuitsu,

    /** 混老頭 (Honroutou) - 2 翻 */
    Honroutou,

    /** 清一色 (Chinitsu) - 5 翻（副露）/ 6 翻（門前清） */
    Chinitsu,

    /** 小三元 (Shousangen) - 2 翻 */
    Shousangen,

    // ===== 特殊胡牌型 =====
    /** 七對子 (Chiitoitsu) - 2 翻 */
    Chiitoitsu,

    // ===== 字牌役 =====
    /** 場風 (Bakaze) - 1 翻 */
    RoundWind,
    /** 自風 (Tonmyakze) - 1 翻 */
    SeatWind,
    /** 役牌 (Yakuhai) - 1 翻 */
    Dragon,

    // ===== 役滿 =====
    /** 國士無雙 (Kokushi Musou) - 役滿 */
    KokushiMusou,
    /** 九蓮寶燈 (Churen Poto) - 役滿 */
    ChurenPoto,
    /** 字一色 (Tsuuiisou) - 役滿 */
    Tsuuiisou,
    /** 綠一色 (Ryuuuiisou) - 役滿 */
    Ryuuuiisou,
    /** 四暗刻 (Suuankou) - 役滿 */
    Suuankou,
    /** 四杠子 (Sukantsu) - 役滿 */
    Sukantsu,
    /** 小四喜 (Shousuushi) - 役滿 */
    Shousuushi,
    /** 大三元 (Daisangen) - 役滿 */
    Daisangen,
    /** 清老頭 (Chinroutou) - 役滿 */
    Chinroutou,
    /** 天和 (Tenhou) - 役滿 */
    Tenhou,
    /** 地和 (Chiihou) - 役滿 */
    Chiihou,

    // ===== 雙倍役滿 =====
    /** 國士無雙十三面 (Kokushi Musou 13-men) - 雙倍役滿 */
    KokushiMusou13,
    /** 九蓮寶燈九面 (Churen Poto 9-men) - 雙倍役滿 */
    ChurenPoto9,
    /** 四暗刻單騎 (Suuankou Tanki) - 雙倍役滿 */
    SuuankouTanki,
    /** 大四喜 (Daisuushii) - 雙倍役滿 */
    Daisuushii
}
