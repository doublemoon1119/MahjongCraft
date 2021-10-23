package doublemoon.mahjongcraft.game.mahjong.riichi

import doublemoon.mahjongcraft.MOD_ID
import kotlinx.serialization.Serializable

/**
 * 結算遊戲分數用, 會發給客戶端, 用來顯示結算分數畫面,
 * 並不會顯示和處理玩家的手牌, 單純顯示 結算的原因 以及 分數的加減 和 排行,
 * 原因包括: 流局, 榮和, 自摸, 遊戲結束 共 4 種情況
 *
 * @param titleLang 這個結算的標題
 * */
@Serializable
sealed class ScoreSettlement(
    val titleLang: String
) : SettleDataInput {

    /**
     * 顯示分數排行用的字串列表,
     * 照得分後總分排名由高至低排列,
     * 格式如下:
     * ["DoubleMoon","stringUUID","true","1","40000","15000","↑"] ,
     * [玩家名稱(長度上限:16), 玩家的 uuid, 是否是真的玩家, 機器人code(只有不是真的玩家才會用到), 總分, 分數變化, 如果有原本的分數加上分數變化導致排名有變動會在最後加上變化的符號 ↑/↓],
     * */
    val rankedScoreStringList: List<List<String>>
        get() = mutableListOf<List<String>>().apply {
            val originComparator = Comparator<ScoreItem> { o1, o2 ->
                compareValuesBy(o1, o2) { it.scoreOrigin }.let { //比較原始分數
                    if (it == 0) { //兩個值一樣
                        compareValuesBy(o1, o2) { arg -> arg.stringUUID } //比較 UUID
                    } else it
                }
            }
            val afterComparator = Comparator<ScoreItem> { o1, o2 ->
                compareValuesBy(o1, o2) { it.scoreOrigin + it.scoreChange }.let { //比較總分
                    if (it == 0) { //兩個值一樣
                        compareValuesBy(o1, o2) { arg -> arg.stringUUID } //比較 UUID
                    } else it
                }
            }
            val origin = scoreList.sortedWith(originComparator).reversed() //用原始分數排列後倒過來 (即用原始分數降序排列)
            val after = scoreList.sortedWith(afterComparator).reversed() //用總分降序排列後倒過來 (即用總分降序排列)
            after.forEachIndexed { index, playerScore ->
                val listToAdd = mutableListOf<String>()
                val rankOrigin = origin.indexOf(playerScore)    //玩家原本的排名
                val rankFloat = if (index < rankOrigin) "↑" else if (index > rankOrigin) "↓" else ""
                val textScoreChange = playerScore.scoreChange.let { //這邊檢查一下這個值是不是 0
                    when {
                        it == 0 -> "" //如果 == 0->當作空白
                        it > 0 -> "+$it" //如果 > 0->加上 "+" 號
                        else -> "$it"
                    }
                }
                listToAdd += playerScore.displayName                                        //顯示名稱
                listToAdd += playerScore.stringUUID                                         //uuid
                listToAdd += playerScore.isRealPlayer.toString()                            //是否是真的玩家
                listToAdd += playerScore.botCode.toString()                                 //機器人編號 (如果是真的玩家的話可以無視)
                listToAdd += (playerScore.scoreOrigin + playerScore.scoreChange).toString() //總分
                listToAdd += textScoreChange                                                //分數變化
                listToAdd += rankFloat                                                      //排名浮動的符號
                this += listToAdd
            }
        }

    @Serializable
    data class ExhaustiveDraw(
        val exhaustiveDraw: doublemoon.mahjongcraft.game.mahjong.riichi.ExhaustiveDraw,
        override val scoreList: List<ScoreItem>,
    ) : ScoreSettlement(titleLang = exhaustiveDraw.lang)

    @Serializable
    data class Ron(
        override val scoreList: List<ScoreItem>,
    ) : ScoreSettlement(titleLang = MahjongGameBehavior.RON.lang)

    @Serializable
    data class Tsumo(
        override val scoreList: List<ScoreItem>,
    ) : ScoreSettlement(titleLang = MahjongGameBehavior.TSUMO.lang)

    @Serializable
    data class GameOver(
        override val scoreList: List<ScoreItem>
    ) : ScoreSettlement(titleLang = "$MOD_ID.game.game_over")
}

/**
 * [ScoreSettlement] 會帶上的資料
 * */
interface SettleDataInput {

    /**
     * 玩家的分數列表, 以 [ScoreItem] 存取
     * */
    val scoreList: List<ScoreItem>
}

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