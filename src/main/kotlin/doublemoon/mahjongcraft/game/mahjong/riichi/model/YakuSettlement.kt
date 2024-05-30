package doublemoon.mahjongcraft.game.mahjong.riichi.model

import doublemoon.mahjongcraft.entity.toMahjongTileList
import doublemoon.mahjongcraft.game.mahjong.riichi.player.MahjongBot
import doublemoon.mahjongcraft.game.mahjong.riichi.player.MahjongPlayerBase
import kotlinx.serialization.Serializable
import org.mahjong4j.yaku.normals.NormalYaku
import org.mahjong4j.yaku.yakuman.Yakuman

/**
 * 傳給玩家用的,
 *
 * @param displayName 胡牌玩家的 displayName
 * @param uuid 胡牌玩家的 stringUUID
 * @param nagashiMangan 特殊情況, 流局滿貫專用
 * @param redFiveCount 紅寶牌數量
 * @param riichi 立直, 只要有立直就算, 不管是普通的立直還是雙立直
 * @param winningTile 使玩家胡牌的那張牌, 流局滿貫為 [MahjongTile.UNKNOWN]
 * @param hands 手牌
 * @param fuuroList 副露列表, 結構為 <是否為暗槓, [MahjongTile] 列表>, 傳副露裡面的 [MahjongTile] 即可
 * @param doraIndicators 寶牌指示牌
 * @param uraDoraIndicators 裏寶牌指示牌
 * @param score 這邊只是做顯示用, 上面的數字並不是玩家實際得到點數 (ex: 立直棒, 場棒的點數影響)
 * @param botCode 機器人才會用到的代碼, 會存放機器人的外觀碼, 渲染畫面用
 * */
@Serializable
data class YakuSettlement(
    val displayName: String,
    val uuid: String,
    val isRealPlayer: Boolean,
    val botCode: Int = MahjongTile.UNKNOWN.code,
    val yakuList: List<NormalYaku>,
    val yakumanList: List<Yakuman>,
    val doubleYakumanList: List<DoubleYakuman>,
    val nagashiMangan: Boolean = false,
    val redFiveCount: Int = 0,
    val riichi: Boolean,
    val winningTile: MahjongTile,
    val hands: List<MahjongTile>,
    val fuuroList: List<Pair<Boolean, List<MahjongTile>>>,
    val doraIndicators: List<MahjongTile>,
    val uraDoraIndicators: List<MahjongTile>,
    val fu: Int,
    val han: Int,
    val score: Int,
) {
    constructor(
        mahjongPlayer: MahjongPlayerBase,
        yakuList: List<NormalYaku>,
        yakumanList: List<Yakuman>,
        doubleYakumanList: List<DoubleYakuman>,
        nagashiMangan: Boolean = false,
        redFiveCount: Int = 0,
        winningTile: MahjongTile,
        fuuroList: List<Pair<Boolean, List<MahjongTile>>>,
        doraIndicators: List<MahjongTile>,
        uraDoraIndicators: List<MahjongTile>,
        fu: Int,
        han: Int,
        score: Int,
    ) : this(
        displayName = mahjongPlayer.displayName,
        uuid = mahjongPlayer.uuid,
        isRealPlayer = mahjongPlayer.isRealPlayer,
        botCode = if (mahjongPlayer is MahjongBot) mahjongPlayer.entity.code else MahjongTile.UNKNOWN.code,
        yakuList = yakuList,
        yakumanList = yakumanList,
        doubleYakumanList = doubleYakumanList,
        nagashiMangan = nagashiMangan,
        redFiveCount = redFiveCount,
        riichi = mahjongPlayer.riichi || mahjongPlayer.doubleRiichi,
        winningTile = winningTile,
        hands = mahjongPlayer.hands.toMahjongTileList(),
        fuuroList = fuuroList,
        doraIndicators = doraIndicators,
        uraDoraIndicators = uraDoraIndicators,
        fu = fu,
        han = han,
        score = score
    )

    companion object {
        /**
         * 流局滿貫是特殊情況
         * */
        fun nagashiMangan(
            mahjongPlayer: MahjongPlayerBase,
            doraIndicators: List<MahjongTile>,
            uraDoraIndicators: List<MahjongTile>,
            isDealer: Boolean //是否是莊家
        ): YakuSettlement = YakuSettlement(
            mahjongPlayer = mahjongPlayer,
            yakuList = listOf(),
            yakumanList = listOf(),
            doubleYakumanList = listOf(),
            nagashiMangan = true,
            winningTile = MahjongTile.UNKNOWN,
            fuuroList = listOf(),
            doraIndicators = doraIndicators,
            uraDoraIndicators = uraDoraIndicators,
            fu = 0,
            han = 0,
            score = if (isDealer) 12000 else 8000 // 滿貫 8000 (莊家 12000) 點
        )

        /**
         * 無役, 判斷是否聽牌用
         * */
        val NO_YAKU = YakuSettlement(
            displayName = "",
            uuid = "",
            isRealPlayer = false,
            botCode = MahjongTile.UNKNOWN.code,
            yakuList = emptyList(),
            yakumanList = emptyList(),
            doubleYakumanList = emptyList(),
            nagashiMangan = false,
            redFiveCount = 0,
            riichi = false,
            winningTile = MahjongTile.UNKNOWN,
            hands = emptyList(),
            fuuroList = emptyList(),
            doraIndicators = emptyList(),
            uraDoraIndicators = emptyList(),
            fu = 0,
            han = 0,
            score = 0
        )
    }
}