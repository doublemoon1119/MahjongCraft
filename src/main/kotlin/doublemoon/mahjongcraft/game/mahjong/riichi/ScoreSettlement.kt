package doublemoon.mahjongcraft.game.mahjong.riichi

import doublemoon.mahjongcraft.MOD_ID
import kotlinx.serialization.Serializable
import net.minecraft.text.LiteralText
import net.minecraft.text.Text

/**
 * 結算遊戲分數用, 會發給客戶端, 用來顯示結算分數畫面,
 * 並不會顯示和處理玩家的手牌, 單純顯示 結算的原因 以及 分數的加減 和 排行,
 * 原因包括: 流局, 榮和, 自摸, 遊戲結束 共 4 種情況
 *
 * @param titleTranslateKey 這個結算的標題的 TranslateText 的 key
 * */
@Serializable
sealed class ScoreSettlement(
    val titleTranslateKey: String,
    private val scoreListInput: List<ScoreItem>
) {

    /**
     * 根據傳入的 [scoreListInput] 進行排序後顯示分數排行用的 [RankedScoreItem] 列表,
     * 照得分後總分排名由高至低排列,
     * */
    val rankedScoreList: List<RankedScoreItem> = mutableListOf<RankedScoreItem>().apply {
        val origin = scoreListInput.sortedWith(originalScoreComparator).reversed() //用原始分數排列後倒過來 (即用原始分數降序排列)
        val after = scoreListInput.sortedWith(totalScoreComparator).reversed() //用總分降序排列後倒過來 (即用總分降序排列)
        after.forEachIndexed { index, playerScore ->
            val rankOrigin = origin.indexOf(playerScore)    //玩家原本的排名
            val rankFloat = if (index < rankOrigin) "↑" else if (index > rankOrigin) "↓" else ""
            val scoreChangeString = playerScore.scoreChange.let { //這邊檢查一下這個值是不是 0
                when {
                    it == 0 -> "" //如果 == 0->當作空白
                    it > 0 -> "+$it" //如果 > 0->加上 "+" 號
                    else -> "$it"
                }
            }
            this += RankedScoreItem(
                scoreItem = playerScore,
                scoreTotal = playerScore.scoreOrigin + playerScore.scoreChange,
                scoreChangeText = LiteralText(scoreChangeString),
                rankFloatText = LiteralText(rankFloat)
            )
        }
    }

    @Serializable
    data class ExhaustiveDraw(
        val exhaustiveDraw: doublemoon.mahjongcraft.game.mahjong.riichi.ExhaustiveDraw,
        val scoreList: List<ScoreItem>,
    ) : ScoreSettlement(titleTranslateKey = exhaustiveDraw.toText().key, scoreListInput = scoreList)

    @Serializable
    data class Ron(
        val scoreList: List<ScoreItem>,
    ) : ScoreSettlement(titleTranslateKey = MahjongGameBehavior.RON.toText().key, scoreListInput = scoreList)

    @Serializable
    data class Tsumo(
        val scoreList: List<ScoreItem>,
    ) : ScoreSettlement(titleTranslateKey = MahjongGameBehavior.TSUMO.toText().key, scoreListInput = scoreList)

    @Serializable
    data class GameOver(
        val scoreList: List<ScoreItem>
    ) : ScoreSettlement(titleTranslateKey = "$MOD_ID.game.game_over", scoreListInput = scoreList)

    companion object {
        private val originalScoreComparator = Comparator<ScoreItem> { o1, o2 ->
            compareValuesBy(o1, o2) { it.scoreOrigin }.let { //比較原始分數
                if (it == 0) { //兩個值一樣
                    compareValuesBy(o1, o2) { arg -> arg.stringUUID } //比較 UUID
                } else it
            }
        }
        private val totalScoreComparator = Comparator<ScoreItem> { o1, o2 ->
            compareValuesBy(o1, o2) { it.scoreOrigin + it.scoreChange }.let { //比較總分
                if (it == 0) { //兩個值一樣
                    compareValuesBy(o1, o2) { arg -> arg.stringUUID } //比較 UUID
                } else it
            }
        }
    }
}

@Serializable
data class RankedScoreItem(
    val scoreItem: ScoreItem,
    val scoreTotal: Int,
    val scoreChangeText: Text,
    val rankFloatText: Text
)

@Serializable
data class ScoreItem(
    val displayName: String,
    val stringUUID: String,
    val isRealPlayer: Boolean,
    val botCode: Int = MahjongTile.UNKNOWN.code,
    val scoreOrigin: Int,
    val scoreChange: Int
) {
    constructor(
        mahjongPlayer: MahjongPlayerBase,
        scoreOrigin: Int,
        scoreChange: Int
    ) : this(
        displayName = mahjongPlayer.displayName,
        stringUUID = mahjongPlayer.uuid,
        isRealPlayer = mahjongPlayer.isRealPlayer,
        botCode = if (mahjongPlayer is MahjongBot) mahjongPlayer.entity.code else MahjongTile.UNKNOWN.code,
        scoreOrigin = scoreOrigin,
        scoreChange = scoreChange
    )
}