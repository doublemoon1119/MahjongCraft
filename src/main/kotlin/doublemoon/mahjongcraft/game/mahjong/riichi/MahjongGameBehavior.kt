package doublemoon.mahjongcraft.game.mahjong.riichi

import doublemoon.mahjongcraft.MOD_ID
import doublemoon.mahjongcraft.network.MahjongGamePacketHandler
import doublemoon.mahjongcraft.util.TextFormatting
import kotlinx.serialization.Serializable
import net.minecraft.text.TranslatableText

/**
 * 麻將遊戲行為, 發送數據包 [MahjongGamePacketHandler] 用,
 * 伺服端->玩家端 表示: 詢問玩家是否要執行,
 * 玩家端->伺服端 表示: 要執行
 * */
@Serializable
enum class MahjongGameBehavior : TextFormatting {
    CHII,//吃
    PON_OR_CHII,//碰或吃
    PON,//碰
    KAN,//槓
    MINKAN,//明槓(明槓時可以選擇要碰還是要槓)
    ANKAN,//暗槓
    ANKAN_OR_KAKAN,//暗槓或加槓
    KAKAN,//加槓
    CHAN_KAN,//搶槓
    RIICHI,//立直
    DOUBLE_RIICHI,//雙立直
    RON,//榮和
    TSUMO,//自摸
    KYUUSHU_KYUUHAI,//九種九牌

    EXHAUSTIVE_DRAW,//流局
    DISCARD,//丟牌
    SKIP,//跳過
    GAME_OVER,//遊戲結束
    SCORE_SETTLEMENT,//分數結算畫面用
    YAKU_SETTLEMENT,//役結算畫面用
    COUNTDOWN_TIME,//倒數時間用
    AUTO_ARRANGE,//詢問自動理牌用
    ;

    override fun toText() = TranslatableText("$MOD_ID.game.behavior.${name.lowercase()}")
}