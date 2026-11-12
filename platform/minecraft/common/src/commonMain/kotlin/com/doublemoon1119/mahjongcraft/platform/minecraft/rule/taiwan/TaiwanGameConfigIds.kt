package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan

/** 台灣麻將房間設定欄位與選項的完整 ID；選項放在所屬欄位的路徑底下。 */
internal object TaiwanGameConfigIds {
    /** 賽程長度欄位。 */
    const val GAME_LENGTH: String = "mahjongcraft:taiwan/config/game_length"

    /** 賽程長度的一局選項。 */
    const val GAME_LENGTH_ONE_GAME: String = "mahjongcraft:taiwan/config/game_length/one_game"

    /** 賽程長度的東風圈選項。 */
    const val GAME_LENGTH_EAST: String = "mahjongcraft:taiwan/config/game_length/east"

    /** 賽程長度的東南圈選項。 */
    const val GAME_LENGTH_TWO_WINDS: String = "mahjongcraft:taiwan/config/game_length/two_winds"

    /** 賽程長度的東南西北圈選項。 */
    const val GAME_LENGTH_FOUR_WINDS: String = "mahjongcraft:taiwan/config/game_length/four_winds"

    /** 使用花牌欄位。 */
    const val USE_FLOWER_TILES: String = "mahjongcraft:taiwan/config/use_flower_tiles"

    /** 四風連打流局欄位。 */
    const val FOUR_WINDS_ABORTIVE_DRAW: String = "mahjongcraft:taiwan/config/four_winds_abortive_draw"

    /** 槓牌解除過水欄位。 */
    const val KAN_CLEARS_PASSED_WIN: String = "mahjongcraft:taiwan/config/kan_clears_passed_win"

    /** 起胡台數欄位。 */
    const val MINIMUM_TAI: String = "mahjongcraft:taiwan/config/minimum_tai"

    /** 雙響處理欄位。 */
    const val DOUBLE_WIN_RESOLUTION: String = "mahjongcraft:taiwan/config/double_win_resolution"

    /** 雙響處理的最近順位優先選項。 */
    const val DOUBLE_WIN_RESOLUTION_NEAREST_WINNER: String = "mahjongcraft:taiwan/config/double_win_resolution/nearest_winner"

    /** 雙響處理的全部成立選項。 */
    const val DOUBLE_WIN_RESOLUTION_ALL_WINNERS: String = "mahjongcraft:taiwan/config/double_win_resolution/all_winners"

    /** 三響處理欄位。 */
    const val TRIPLE_WIN_RESOLUTION: String = "mahjongcraft:taiwan/config/triple_win_resolution"

    /** 三響處理的最近順位優先選項。 */
    const val TRIPLE_WIN_RESOLUTION_NEAREST_WINNER: String = "mahjongcraft:taiwan/config/triple_win_resolution/nearest_winner"

    /** 三響處理的全部成立選項。 */
    const val TRIPLE_WIN_RESOLUTION_ALL_WINNERS: String = "mahjongcraft:taiwan/config/triple_win_resolution/all_winners"

    /** 底分欄位。 */
    const val BASE_SCORE: String = "mahjongcraft:taiwan/config/base_score"

    /** 每台分數欄位。 */
    const val POINT_PER_TAI: String = "mahjongcraft:taiwan/config/point_per_tai"

    /** 起始點數欄位。 */
    const val INITIAL_SCORE: String = "mahjongcraft:taiwan/config/initial_score"
}
