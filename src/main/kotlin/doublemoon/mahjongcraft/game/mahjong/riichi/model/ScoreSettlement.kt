package doublemoon.mahjongcraft.game.mahjong.riichi.model

import doublemoon.mahjongcraft.game.mahjong.riichi.player.MahjongBot
import doublemoon.mahjongcraft.game.mahjong.riichi.player.MahjongPlayerBase
import kotlinx.serialization.Serializable
import net.minecraft.text.Text

/**
 * 結算遊戲分數用, 會發給客戶端, 用來顯示結算分數畫面,
 * 並不會顯示和處理玩家的手牌, 單純顯示 結算的原因 以及 分數的加減 和 排行,
 *
 * 分數結算畫面總共有 8 種情況：流局(5種)、胡、自摸、遊戲結束
 * */
@Serializable
data class ScoreSettlement(
    val titleTranslateKey: String,
    val scoreList: List<ScoreItem>
) {
    /**
     * 根據傳入的 [scoreList] 進行排序後顯示分數排行用的 [RankedScoreItem] 列表,
     * 照得分後總分排名由高至低排列,
     * */
    val rankedScoreList: List<RankedScoreItem> = buildList {
        val origin = scoreList.sortedWith(originalScoreComparator).reversed() //用原始分數排列後倒過來 (即用原始分數降序排列)
        val after = scoreList.sortedWith(totalScoreComparator).reversed() //用總分降序排列後倒過來 (即用總分降序排列)
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
                scoreChangeText = Text.of(scoreChangeString),
                rankFloatText = Text.of(rankFloat)
            )
        }
    }

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