package com.doublemoon1119.mahjongcraft.platform.minecraft.action

/**
 * 回放時標在進度條上、並讓播放多停一會兒的交易種類。
 *
 * 一筆交易有多個動作時取排在後面的種類，順序代表重要程度。
 */
enum class GameActionReplayMarker {
    /** 鳴牌：吃、碰、槓，以及把特殊牌亮到旁邊再補摸，例如日麻三人麻將的拔北。 */
    MELD,

    /** 改變自己狀態或局勢的宣告，例如日麻的立直。 */
    DECLARATION,

    /** 和牌。 */
    WIN,
}
