package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueCalculator

/**
 * 日本麻將役種識別列舉。
 *
 * 包含所有可計算番數的役種，由 [RiichiHandValueCalculator] 進行檢測與計算。
 *
 * @property isLocal 是否為古役；只有規則設定啟用古役時才會成立。
 */
enum class YakuType(
    val isLocal: Boolean = false,
) {
    // ===== 寶牌 =====
    /** 寶牌 (Dora) */
    Dora,

    /** 裏寶牌 (Ura Dora) */
    UraDora,

    /** 赤寶牌 (Aka Dora) */
    AkaDora,

    /** 拔北寶牌 (Nuki Dora)：三人麻將中每張拔出的北算一張寶牌。 */
    NukiDora,

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

    /** 役牌：白 (Haku) - 1 翻 */
    WhiteDragon,

    /** 役牌：發 (Hatsu) - 1 翻 */
    GreenDragon,

    /** 役牌：中 (Chun) - 1 翻 */
    RedDragon,

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
    Daisuushii,

    // ===== 古役 =====
    /** 燕返 (Tsubame Gaeshi) - 1 翻：以其他玩家的立直宣言牌榮和。 */
    TsubameGaeshi(isLocal = true),

    /** 槓振 (Kanburi) - 1 翻：以其他玩家槓後打出的牌榮和。 */
    Kanburi(isLocal = true),

    /** 十二落抬 (Shiiaru Raotai) - 1 翻：四組面子全部副露後單騎和牌。 */
    ShiiaruRaotai(isLocal = true),

    /** 五門齊 (Uumen Chii) - 2 翻：同時含萬、筒、索、風牌與三元牌。 */
    UumenChii(isLocal = true),

    /** 三連刻 (Sanrenkou) - 2 翻：同花色數字連續的三組刻子。 */
    Sanrenkou(isLocal = true),

    /** 一色三同順 (Isshoku Sanjun) - 2 翻（副露）/ 3 翻（門前清）：同花色同數字的三組順子。 */
    IsshokuSanjun(isLocal = true),

    /** 一筒摸月 (Iipin Moyue) - 5 翻：海底自摸一筒。 */
    IipinMoyue(isLocal = true),

    /** 九筒撈魚 (Chuupin Raoyui) - 5 翻：河底榮和九筒。 */
    ChuupinRaoyui(isLocal = true),

    /** 人和 (Renhou) - 役滿：非莊家在自己第一次摸牌前榮和。 */
    Renhou(isLocal = true),

    /** 大車輪 (Daisharin) - 役滿：筒子 2～8 各兩張。 */
    Daisharin(isLocal = true),

    /** 大竹林 (Daichikurin) - 役滿：索子 2～8 各兩張。 */
    Daichikurin(isLocal = true),

    /** 大數鄰 (Daisuurin) - 役滿：萬子 2～8 各兩張。 */
    Daisuurin(isLocal = true),

    /** 石上三年 (Ishi no Ue ni mo Sannen) - 役滿：雙立直後以海底自摸或河底榮和。 */
    IshinoUeSannen(isLocal = true),

    /** 大七星 (Daichisei) - 雙倍役滿：七種字牌各一對的七對子。 */
    Daichisei(isLocal = true),
}
