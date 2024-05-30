package doublemoon.mahjongcraft.game.mahjong.riichi.model

import kotlinx.serialization.Serializable

/**
 * 麻將的回合
 *
 * @param wind 場風 (Prevalent Wind)
 * @param round 第幾局 (從 0 開始, 3 為結束) (也可以視作 "x"th rotation, 意即轉幾次的意思)
 * @param honba 本場數
 * */
@Serializable
data class MahjongRound(
    var wind: Wind = Wind.EAST,
    var round: Int = 0,
    var honba: Int = 0
) {
    private var spentRounds = 0 //經過的回合數

    /**
     * 換下個回合會牽扯到 [wind] 場風的問題,
     * 所以 [round] 跟 [wind] 不能直接更改
     * */
    fun nextRound() {
        val nextRound = (this.round + 1) % 4
        honba = 0
        if (nextRound == 0) { //這個風的最後一局, 換風
            val nextWindNum = (this.wind.ordinal + 1) % 4 //在北 4 局的下回合會回到東風, 但是請別這樣搞, 一般是完全不會用到的
            wind = Wind.values()[nextWindNum]
        }
        round = nextRound
        spentRounds++
    }

    /**
     * 經過的回合數 ([spentRounds] + 1) >= 規則中的長度 即是 AllLast,
     * 超過 AllLast 的回合一律都當作 AllLast
     * */
    fun isAllLast(rule: MahjongRule): Boolean = (spentRounds + 1) >= rule.length.rounds
}