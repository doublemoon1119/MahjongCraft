package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

/** 日麻（四人與三人）房間設定欄位與選項的完整 ID；選項放在所屬欄位的路徑底下。 */
internal object RiichiGameConfigIds {
    /** 局長欄位。 */
    const val GAME_LENGTH: String = "mahjongcraft:riichi/config/game_length"

    /** 局長的一局戰選項。 */
    const val GAME_LENGTH_ONE_GAME: String = "mahjongcraft:riichi/config/game_length/one_game"

    /** 局長的東風戰選項。 */
    const val GAME_LENGTH_EAST: String = "mahjongcraft:riichi/config/game_length/east"

    /** 局長的半莊戰選項。 */
    const val GAME_LENGTH_TWO_WINDS: String = "mahjongcraft:riichi/config/game_length/two_winds"

    /** 赤寶牌數量欄位。 */
    const val RED_DORA_COUNT: String = "mahjongcraft:riichi/config/red_dora_count"

    /** 赤寶牌 0 張選項。 */
    const val RED_DORA_COUNT_0: String = "mahjongcraft:riichi/config/red_dora_count/0"

    /** 赤寶牌 2 張選項。 */
    const val RED_DORA_COUNT_2: String = "mahjongcraft:riichi/config/red_dora_count/2"

    /** 赤寶牌 3 張選項。 */
    const val RED_DORA_COUNT_3: String = "mahjongcraft:riichi/config/red_dora_count/3"

    /** 赤寶牌 4 張選項。 */
    const val RED_DORA_COUNT_4: String = "mahjongcraft:riichi/config/red_dora_count/4"

    /** 食斷欄位。 */
    const val ALLOW_OPEN_TANYAO: String = "mahjongcraft:riichi/config/allow_open_tanyao"

    /** 古役欄位。 */
    const val USE_LOCAL_YAKU: String = "mahjongcraft:riichi/config/use_local_yaku"

    /** 起胡限制欄位。 */
    const val MINIMUM_WIN_CONSTRAINT: String = "mahjongcraft:riichi/config/minimum_win_constraint"

    /** 起始點數欄位。 */
    const val INITIAL_SCORE: String = "mahjongcraft:riichi/config/initial_score"

    /** 擊飛門檻欄位。 */
    const val BUST_THRESHOLD: String = "mahjongcraft:riichi/config/bust_threshold"

    /** 返點欄位。 */
    const val MIN_POINTS_TO_WIN: String = "mahjongcraft:riichi/config/min_points_to_win"

    /** 不聽罰符單位欄位。 */
    const val NOTEN_PENALTY_UNIT: String = "mahjongcraft:riichi/config/noten_penalty_unit"

    /** 雙響處理欄位。 */
    const val DOUBLE_RON_RESOLUTION: String = "mahjongcraft:riichi/config/double_ron_resolution"

    /** 雙響處理的頭跳選項。 */
    const val DOUBLE_RON_RESOLUTION_NEAREST_WINNER: String = "mahjongcraft:riichi/config/double_ron_resolution/nearest_winner"

    /** 雙響處理的全部成立選項。 */
    const val DOUBLE_RON_RESOLUTION_ALL_WINNERS: String = "mahjongcraft:riichi/config/double_ron_resolution/all_winners"

    /** 雙響處理的途中流局選項。 */
    const val DOUBLE_RON_RESOLUTION_ABORTIVE_DRAW: String = "mahjongcraft:riichi/config/double_ron_resolution/abortive_draw"

    /** 三響處理欄位。 */
    const val TRIPLE_RON_RESOLUTION: String = "mahjongcraft:riichi/config/triple_ron_resolution"

    /** 三響處理的頭跳選項。 */
    const val TRIPLE_RON_RESOLUTION_NEAREST_WINNER: String = "mahjongcraft:riichi/config/triple_ron_resolution/nearest_winner"

    /** 三響處理的全部成立選項。 */
    const val TRIPLE_RON_RESOLUTION_ALL_WINNERS: String = "mahjongcraft:riichi/config/triple_ron_resolution/all_winners"

    /** 三響處理的途中流局選項。 */
    const val TRIPLE_RON_RESOLUTION_ABORTIVE_DRAW: String = "mahjongcraft:riichi/config/triple_ron_resolution/abortive_draw"
}
