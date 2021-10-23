package doublemoon.mahjongcraft.game.mahjong.riichi

import doublemoon.mahjongcraft.MOD_ID


/**
 * 流局,
 * 不包含 sanchahou (三家和)
 * */
enum class ExhaustiveDraw {
    NORMAL,         //一般流局
    KYUUSHU_KYUUHAI,//九種九牌
    SUUFON_RENDA,   //四風連打
    SUUCHA_RIICHI,  //四家立直
    SUUKAIKAN,      //四開槓(四槓散了)
    ;

    val lang: String = "$MOD_ID.game.exhaustive_draw.${name.lowercase()}"
}