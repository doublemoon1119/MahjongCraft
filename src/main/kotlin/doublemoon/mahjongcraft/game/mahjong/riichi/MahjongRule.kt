package doublemoon.mahjongcraft.game.mahjong.riichi

import doublemoon.mahjongcraft.MOD_ID
import doublemoon.mahjongcraft.util.TextFormatting
import doublemoon.mahjongcraft.util.plus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * 麻將的規則,
 * 可以進行選擇與調整,
 * 目前只有古役不能調整而已,
 *
 * 記得如果要進行 Json 編碼到 String, 編碼(encode) 預設值(Default) 的時候, 是不會編碼輸出任何預設值的,
 * 讀的時候沒讀到的值會直接當作預設值處理, 所以基本上沒差,
 * 如果真的要編碼輸出預設值的話, 要進行以下操作:
 * val format = Json { encodeDefaults = true }
 * format.encodeToString("data")
 *
 * @param length 遊戲局數, 預設為 半莊
 * @param thinkingTime 思考時間, 預設為 5 + 20
 * @param startingPoints 起始點數, 預設 25000
 * @param minPointsToWin 1位必要點數, 預設 30000
 * @param minimumHan 翻縛, 預設 ONE
 * @param spectate 旁觀, 這裡的意思在遊戲外的玩家能否看到遊戲內玩家的手牌 (已經打出去的牌或者副露原本就看得見)
 * @param redFive 赤寶牌數量
 * @param openTanyao 食斷
 * @param localYaku 古役,(目前沒有加入)
 *
 * @see <a href="https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/json.md">詳細 Serializable 對 Json 的操作請看這</a>
 * */
@Serializable
data class MahjongRule(
    var length: GameLength = GameLength.TWO_WIND,
    var thinkingTime: ThinkingTime = ThinkingTime.NORMAL,
    var startingPoints: Int = 25000,
    var minPointsToWin: Int = 30000,
    var minimumHan: MinimumHan = MinimumHan.ONE,
    var spectate: Boolean = true,
    var redFive: RedFive = RedFive.NONE,
    var openTanyao: Boolean = true,
    var localYaku: Boolean = false
) {
    //將 MahjongRule 編碼成 json 格式的 String
    fun toJsonString(): String = Json.encodeToString(serializer(), this)

    fun toTexts(
        color1: Formatting = Formatting.RED,
        color2: Formatting = Formatting.YELLOW,
        color3: Formatting = Formatting.GREEN,
        color4: Formatting = Formatting.AQUA,
        color5: Formatting = Formatting.WHITE
    ): List<Text> {
        val colon = Text.literal(": ").formatted(color5)
        val rules =
            Text.translatable("$MOD_ID.game.rules").formatted(color1).formatted(Formatting.BOLD)
        val lengthText =
            (Text.translatable("$MOD_ID.game.length") + colon).formatted(color2)
        val thinkingTimeText =
            (Text.translatable("$MOD_ID.game.thinking_time") + colon).formatted(color2)
        val startingPointsText =
            (Text.translatable("$MOD_ID.game.starting_points") + colon).formatted(color2)
        val minPointsToWinText =
            (Text.translatable("$MOD_ID.game.min_points_to_win") + colon).formatted(color2)
        val minimumHanText =
            (Text.translatable("$MOD_ID.game.minimum_han") + colon).formatted(color2)
        val spectateText =
            (Text.translatable("$MOD_ID.game.spectate") + colon).formatted(color2)
        val redFiveText =
            (Text.translatable("$MOD_ID.game.red_five") + colon).formatted(color2)
        val openTanyaoText =
            (Text.translatable("$MOD_ID.game.open_tanyao") + colon).formatted(color2)
        val enable = Text.translatable("$MOD_ID.game.enabled").formatted(color3)
        val disable = Text.translatable("$MOD_ID.game.disabled").formatted(color3)
        val spectateStatus = if (spectate) enable else disable
        val openTanyaoStatus = if (openTanyao) enable else disable
        val second = Text.translatable("$MOD_ID.game.seconds").formatted(color3)
        return listOf(
            rules,
            Text.literal("§3 - ") + lengthText + (length.toText() as MutableText).formatted(color3),
            Text.literal("§3 - ")
                    + thinkingTimeText
                    + Text.literal("${thinkingTime.base}").formatted(color4)
                    + Text.literal(" + ").formatted(color1)
                    + Text.literal("${thinkingTime.extra}").formatted(color4)
                    + " " + second,
            Text.literal("§3 - ") + startingPointsText + Text.literal("$startingPoints").formatted(color3),
            Text.literal("§3 - ") + minPointsToWinText + Text.literal("$minPointsToWin").formatted(color3),
            Text.literal("§3 - ") + minimumHanText + Text.literal("${minimumHan.han}").formatted(color3),
            Text.literal("§3 - ") + spectateText + spectateStatus,
            Text.literal("§3 - ") + redFiveText + Text.literal("${redFive.quantity}").formatted(color3),
            Text.literal("§3 - ") + openTanyaoText + openTanyaoStatus
        )
    }

    companion object {
        //從 json 格式的 String 解碼回 MahjongRule
        fun fromJsonString(jsonString: String): MahjongRule = Json.decodeFromString(serializer(), jsonString)

        const val MAX_POINTS = 200000
        const val MIN_POINTS = 100
    }

    /**
     * 麻將遊戲長度,
     * 以局為最小單位,
     * 以 "東 1 局" 為 0 開始算, "北 4 局" 為 15
     *
     * @param rounds 局數
     * @param finalRound 真的最後一局, 就算未滿 1 位必要點數也必須結束(ex: 西入後的 西 4 局, 只要沒連莊, 打完就是結束了)
     * */
    enum class GameLength(
        private val startingWind: Wind,
        val rounds: Int,
        val finalRound: Pair<Wind, Int>
    ) : TextFormatting {
        ONE_GAME(Wind.EAST, 1, Wind.EAST to 3),
        EAST(Wind.EAST, 4, Wind.SOUTH to 3),
        SOUTH(Wind.SOUTH, 4, Wind.WEST to 3),
        TWO_WIND(Wind.EAST, 8, Wind.WEST to 3);
//        FOUR_WIND(Wind.EAST, 16, "game.$MOD_ID.mahjong.length.four_wind");

        /**
         * 取得開始遊戲的回合
         * */
        fun getStartingRound(): MahjongRound = MahjongRound(wind = startingWind)

        override fun toText() = Text.translatable("$MOD_ID.game.length.${name.lowercase()}")
    }

    /**
     * 翻縛,
     * 最小 飜(翻) 數限制
     * */
    enum class MinimumHan(val han: Int) : TextFormatting {
        ONE(1),      // 1 翻
        TWO(2),         // 2 翻
        FOUR(4),         // 2 翻
        YAKUMAN(13);    //13 翻(役滿限定)

        override fun toText(): Text = Text.of(han.toString())
    }

    /**
     * 麻將的思考時間,
     * 和雀魂的長考時間相當
     * */
    enum class ThinkingTime(
        val base: Int,
        val extra: Int
    ) : TextFormatting {
        VERY_SHORT(3, 5),
        SHORT(5, 10),
        NORMAL(5, 20),
        LONG(60, 0),
        VERY_LONG(300, 0);

        override fun toText(): Text = Text.of("$base + $extra s")
    }

    /**
     * 赤寶牌,
     * 4 張紅寶牌是 5筒 2 張,剩下的花色各 1 張
     * */
    enum class RedFive(
        val quantity: Int,
    ) : TextFormatting {
        NONE(0),
        THREE(3),
        FOUR(4);

        override fun toText(): Text = Text.of(quantity.toString())
    }
}