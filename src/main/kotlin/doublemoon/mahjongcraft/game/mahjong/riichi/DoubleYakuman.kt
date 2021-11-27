package doublemoon.mahjongcraft.game.mahjong.riichi

import doublemoon.mahjongcraft.MOD_ID
import doublemoon.mahjongcraft.util.TextFormatting
import net.minecraft.text.TranslatableText


/**
 * 雙倍役滿 (大四喜, 四暗刻單騎, 純正九蓮寶燈, 國士無雙十三面)
 * */
enum class DoubleYakuman : TextFormatting {
    DAISUSHI,                   //大四喜
    SUANKO_TANKI,               //四暗刻單騎
    JUNSEI_CHURENPOHTO,         //純正九蓮寶燈
    KOKUSHIMUSO_JUSANMENMACHI //國士無雙十三面
    ;

    override fun toText() = TranslatableText("$MOD_ID.game.yaku.${name.lowercase()}")
}