package doublemoon.mahjongcraft.game.mahjong.riichi

import doublemoon.mahjongcraft.entity.*
import doublemoon.mahjongcraft.game.GamePlayer
import doublemoon.mahjongcraft.logger
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.sound.SoundEvents
import org.mahjong4j.*
import org.mahjong4j.hands.*
import org.mahjong4j.tile.Tile
import org.mahjong4j.tile.TileType
import org.mahjong4j.yaku.normals.NormalYaku
import org.mahjong4j.yaku.yakuman.Yakuman
import kotlin.math.absoluteValue

abstract class MahjongPlayerBase : GamePlayer {
    /**
     * 手牌
     * */
    val hands: MutableList<MahjongTileEntity> = mutableListOf()

    /**
     * 自動整理手牌 [hands]
     * */
    var autoArrangeHands: Boolean = true

    /**
     * 副露
     * */
    val fuuroList: MutableList<Fuuro> = mutableListOf()

    /**
     * 玩家的立直宣告牌,
     * 出現在 [discardedTilesForDisplay] 會橫著擺的那張
     * */
    var riichiSengenTile: MahjongTileEntity? = null

    /**
     * 丟棄過的牌 (被人吃、碰、槓的時候,不會影響其中的內容),
     * 拿來計算振聽的情況
     * */
    val discardedTiles: MutableList<MahjongTileEntity> = mutableListOf()

    /**
     * 顯示用的丟牌堆 (被人吃、碰、槓的時候,會對這裡的內容進行處理)
     * */
    val discardedTilesForDisplay: MutableList<MahjongTileEntity> = mutableListOf()

    /**
     * 在麻將桌上準備好開始遊戲
     * */
    open var ready: Boolean = false

    /**
     * 立直狀態
     * */
    var riichi: Boolean = false

    /**
     * 雙立直狀態
     * */
    var doubleRiichi: Boolean = false

    /**
     * 擺放在這個玩家面前的 立直棒 數量
     * */
    val riichiStickAmount: Int
        get() = sticks.count { it.scoringStick == ScoringStick.P1000 } //立直棒是 1000 點棒

    /**
     * 這個玩家的所有 立直棒, 場棒
     * */
    val sticks: MutableList<MahjongScoringStickEntity> = mutableListOf()

    /**
     * 玩家的點數
     * */
    var points: Int = 0

    /**
     * 門前清,
     * 暗槓也算門清
     * */
    val isMenzenchin: Boolean
        get() = fuuroList.isEmpty() ||  // 沒有副露
                fuuroList.all { it.mentsu is Kantsu && !it.mentsu.isOpen }  // 副露都是暗槓

    /**
     * 當前能不能立直,
     * 必須 門前清 且 尚未立直或雙立直 且 [tilePairsForRiichi] 大小大於 0 且 [points] >= 1000
     * */
    val isRiichiable: Boolean
        get() = isMenzenchin && !(riichi || doubleRiichi) && tilePairsForRiichi.isNotEmpty() && points >= 1000

    /**
     * 基本思考時間
     * */
    var basicThinkingTime = 0

    /**
     * 額外思考時間
     * */
    var extraThinkingTime = 0

    /**
     * 手牌中的么九牌的種類的數量
     * */
    val numbersOfYaochuuhaiTypes: Int
        get() = hands.map { it.mahjong4jTile }.distinct().count { it.isYaochu }

    /**
     * 傳送的功能, 因為玩家的實體 [ServerPlayerEntity] 有內鍵玩家特殊的傳送方法,
     * 這邊寫一個都有的呼叫方法, 方便統一使用
     * */
    abstract fun teleport(
        targetWorld: ServerWorld,
        x: Double,
        y: Double,
        z: Double,
        yaw: Float,
        pitch: Float,
    )

    /**
     * 玩家吃
     * */
    fun chii(
        mjTileEntity: MahjongTileEntity,
        tilePair: Pair<MahjongTile, MahjongTile>,
        target: MahjongPlayerBase,
        onChii: (MahjongPlayerBase) -> Unit = {},
    ) {
        onChii.invoke(this)
        val tileMj4jCodePair: Pair<Tile, Tile> = tilePair.first.mahjong4jTile to tilePair.second.mahjong4jTile
        val claimTarget = ClaimTarget.LEFT //只能吃上家, 這是絕對的結果
        val tileShuntsu = mutableListOf(
            mjTileEntity,
            hands.find { it.mahjong4jTile == tileMj4jCodePair.first }!!,
            hands.find { it.mahjong4jTile == tileMj4jCodePair.second }!!
        ).also {
            it.sortBy { tile -> tile.mahjong4jTile.code }
            it.forEach { tile -> tile.inGameTilePosition = TilePosition.OTHER }
        }
        val middleTile = tileShuntsu[1].mahjong4jTile
        val shuntsu = Shuntsu(true, middleTile)
        val fuuro =
            Fuuro(mentsu = shuntsu, tileMjEntities = tileShuntsu, claimTarget = claimTarget, claimTile = mjTileEntity)
        hands -= tileShuntsu.toMutableList().also { it -= mjTileEntity }.toSet() //將吃的牌從手牌中去除
        target.discardedTilesForDisplay -= mjTileEntity
        fuuroList += fuuro //添加到副露
    }

    /**
     * 玩家碰
     * */
    fun pon(
        mjTileEntity: MahjongTileEntity,
        claimTarget: ClaimTarget,
        target: MahjongPlayerBase,
        onPon: (MahjongPlayerBase) -> Unit = {},
    ) {
        onPon.invoke(this)
        val kotsu = Kotsu(true, mjTileEntity.mahjong4jTile)
        val tilesForPon = tilesForPon(mjTileEntity).onEach { it.inGameTilePosition = TilePosition.OTHER }
        val fuuro =
            Fuuro(mentsu = kotsu, tileMjEntities = tilesForPon, claimTarget = claimTarget, claimTile = mjTileEntity)
        hands -= tilesForPon.toSet() //將碰的牌從手牌中去除
        target.discardedTilesForDisplay -= mjTileEntity
        fuuroList += fuuro //添加到副露
    }

    /**
     * 玩家明槓
     * */
    fun minkan(
        mjTileEntity: MahjongTileEntity,
        claimTarget: ClaimTarget,
        target: MahjongPlayerBase,
        onMinkan: (MahjongPlayerBase) -> Unit = {},
    ) {
        onMinkan.invoke(this)
        val kantsu = Kantsu(true, mjTileEntity.mahjong4jTile)
        val tilesForMinkan = tilesForMinkan(mjTileEntity).onEach { it.inGameTilePosition = TilePosition.OTHER }
        val fuuro =
            Fuuro(mentsu = kantsu, tileMjEntities = tilesForMinkan, claimTarget = claimTarget, claimTile = mjTileEntity)
        hands -= tilesForMinkan.toSet() //將明槓的牌從手牌中去除
        target.discardedTilesForDisplay -= mjTileEntity
        fuuroList += fuuro //添加到副露
    }

    /**
     * 玩家暗槓
     * */
    fun ankan(mjTileEntity: MahjongTileEntity, onAnkan: (MahjongPlayerBase) -> Unit = {}) {
        onAnkan.invoke(this)
        val kantsu = Kantsu(false, mjTileEntity.mahjong4jTile)
        val tilesForAnkan = tilesForAnkan(mjTileEntity).onEach { it.inGameTilePosition = TilePosition.OTHER }
        val fuuro = Fuuro(
            mentsu = kantsu,
            tileMjEntities = tilesForAnkan,
            claimTarget = ClaimTarget.SELF,
            claimTile = mjTileEntity
        )
        hands -= tilesForAnkan.toSet() //將暗槓的牌從手牌中去除
        discardedTilesForDisplay -= mjTileEntity
        fuuroList += fuuro //添加到副露
    }

    /**
     * 玩家加槓明槓
     * */
    fun kakan(mjTileEntity: MahjongTileEntity, onKakan: (MahjongPlayerBase) -> Unit = {}) {
        onKakan.invoke(this)
        val minKotsu =
            fuuroList.find { mjTileEntity.mahjongTile in it.tileMjEntities.toMahjongTileList() && it.mentsu is Kotsu }
        fuuroList -= minKotsu!!
        val kakantsu = Kakantsu(mjTileEntity.mahjong4jTile)
        val tiles = minKotsu!!.tileMjEntities.toMutableList().also { it += mjTileEntity }
        val fuuro = Fuuro(
            mentsu = kakantsu,
            tileMjEntities = tiles,
            claimTarget = minKotsu.claimTarget, //加槓的鳴牌對象以原本的為主
            claimTile = minKotsu.claimTile ////加槓的鳴的牌以原本的為主
        )
        hands -= mjTileEntity //將暗槓的牌從手牌中去除
        mjTileEntity.inGameTilePosition = TilePosition.OTHER
        fuuroList += fuuro //添加到副露
    }

    /**
     * 手牌 [hands] 中有與 [mjTileEntity] 相同的 2 張牌以上就可以"碰",
     * 並且 [mjTileEntity] "不包含在" [hands] 之中
     * */
    fun canPon(mjTileEntity: MahjongTileEntity): Boolean =
        !(riichi || doubleRiichi) && sameTilesInHands(mjTileEntity).size >= 2

    /**
     * 手牌 [hands] 中有與 [mjTileEntity] 相同的 3 張牌以上就可以"明槓",
     * 並且 [mjTileEntity] "不包含在" [hands] 之中
     * */
    fun canMinkan(mjTileEntity: MahjongTileEntity): Boolean =
        !(riichi || doubleRiichi) && sameTilesInHands(mjTileEntity).size == 3

    /**
     * 手牌 [hands] 中有與副露 [fuuroList] 中的刻子相同的第 4 張牌就可以"加槓",
     * 加槓的情況不應該傳入任何參數,只檢查手上有沒有任何一張在副露裡的刻子的第 4 張牌 (因為加槓可以扣著, 輪到自己摸牌的時候隨時可以槓)
     * */
    val canKakan: Boolean
        get() = tilesCanKakan.size > 0

    /**
     * 手牌 [hands] 中有相同的 4 張牌就可以"暗槓",
     * 暗槓的情況不應該傳入任何參數,只檢查手上有沒有任何一組超過 4 張相同的牌 (因為暗槓可以扣著, 輪到自己摸牌的時候隨時可以槓)
     * */
    val canAnkan: Boolean
        get() = tilesCanAnkan.isNotEmpty()

    /**
     * 手牌 [hands] 中有與 [mjTileEntity] 可以組順子的牌就可以"吃",
     * */
    fun canChii(mjTileEntity: MahjongTileEntity): Boolean =
        !(riichi || doubleRiichi) && tilePairsForChii(mjTileEntity).isNotEmpty()

    /**
     * 執行前請先用 [canPon] 檢查,
     * 返回在手牌 [hands] 之中可以與 [mjTileEntity] 進行"碰"的牌的列表,
     * 如果有紅寶牌,除了 [mjTileEntity] 本身以外, 紅寶牌會在列表中的最前方
     *
     * @return [mjTileEntity] 也包含在列表之中, 結果應該有 3 項
     * */
    private fun tilesForPon(mjTileEntity: MahjongTileEntity): List<MahjongTileEntity> =
        sameTilesInHands(mjTileEntity).apply {
            if (size > 2) { //赤寶牌會優先進行碰的動作
                this -= first { !it.mahjongTile.isRed } //減去第一張不是紅寶牌的牌
                sortBy { it.mahjongTile.isRed } //紅寶牌優先排在第一個
            }
            this += mjTileEntity
        }

    /**
     * 執行前請先用 [canMinkan] 檢查,
     * 返回在手牌 [hands] 之中可以與 [mjTileEntity] 進行"明槓"的牌的列表,
     *
     * @return [mjTileEntity] 也包含在列表之中, 結果應該有 4 項
     * */
    private fun tilesForMinkan(mjTileEntity: MahjongTileEntity): List<MahjongTileEntity> =
        sameTilesInHands(mjTileEntity).also { it += mjTileEntity }

    /**
     * 執行前請先用 [canAnkan] 和 [tilesCanAnkan] 檢查,
     * 返回在手牌 [hands] 之中可以與 [mjTileEntity] 進行"暗槓"的牌的列表,
     * [mjTileEntity] 也包含在列表之中, 結果應該有 4 項
     * */
    private fun tilesForAnkan(mjTileEntity: MahjongTileEntity): List<MahjongTileEntity> =
        sameTilesInHands(mjTileEntity)

    /**
     * [hands] 中可以進行暗槓的牌,
     * 在立直的時候,不會改變 [machi] 以及 暗槓的牌不能與手牌中組成任意順子(即必須是獨立的暗刻) 才可以暗槓
     *
     * @return 可以直接對在集合中的牌可以直接使用 [ankan]
     *
     * */
    val tilesCanAnkan: Set<MahjongTileEntity>
        get() = buildSet {
            hands.distinct().forEach { //先取得牌數符合暗槓門檻的牌
                val count = hands.count { tile -> tile.mahjong4jTile.code == it.mahjong4jTile.code }
                if (count == 4) this += it
            }
            if (!riichi && !doubleRiichi) return@buildSet //沒有立直就結束計算
            /*
             * 在立直可以暗槓的時候, 絕對不會聽可以暗槓的那張牌 (it.mahjong4jTile),
             * 如果沒過濾掉會造成計算的時候可能會出現一張牌同時有 5 個在這個玩家上的 Bug,
             * 會觸發 org.mahjong4j.MahjongTileOverFlowException: 麻雀の牌は4枚までしかありません
             * */
            forEach { //計算暗槓之後的 machi
                val handsCopy = hands.toMutableList()
                val anKanTilesInHands = hands.filter { tile -> tile.mahjong4jTile == it.mahjong4jTile }.toMutableList()
                handsCopy -= anKanTilesInHands.toSet()
                val fuuroListCopy = fuuroList.toMutableList().apply {
                    this += Fuuro(
                        mentsu = Kantsu(false, it.mahjong4jTile),
                        tileMjEntities = anKanTilesInHands,
                        claimTarget = ClaimTarget.SELF,
                        it
                    )
                }
                val mentsuList = fuuroListCopy.map { fuuro -> fuuro.mentsu }
                val calculatedMachi = buildList {
                    MahjongTile.values().filter { mjTile ->
                        mjTile.mahjong4jTile != it.mahjong4jTile
                    }.forEach { mjTile -> //遍歷所有牌
                        val mj4jTile = mjTile.mahjong4jTile
                        val nowHands =
                            handsCopy.toIntArray().apply { this[mj4jTile.code]++ }
                        val tilesWinnable = tilesWinnable(
                            hands = nowHands,
                            mentsuList = mentsuList,
                            lastTile = mj4jTile
                        )
                        if (tilesWinnable) this += mjTile //這張牌構成勝利的牌型, 加入列表中
                    }
                }
                if (calculatedMachi != machi) { //如果計算後的 machi 與計算前不相同, 不能暗槓這張牌
                    this -= it
                } else { //如果計算後的 machi 與計算前相同, 計算會不會影響其他的面子
                    /*
                    * 計算出來的 machi 相同的話也有例外情況, 像是 一色三同順 要暗槓的情況 或者手拿 333 345 摸到 6 把 3 暗槓起來,
                    * 以上情況也不允許暗槓, 待補上
                    * 使用 machi 判斷, 只要要暗槓的牌在手牌中可以組成順子就不能暗槓，必須是獨立存在的暗刻才能暗槓
                    * */
                    val otherTiles = hands.toIntArray()
                    val mentsuList1 = fuuroList.map { fuuro -> fuuro.mentsu } //從副露取得已經確定的面子
                    calculatedMachi.forEach { machiTile ->
                        val tile = machiTile.mahjong4jTile
                        val mj4jHands = Hands(otherTiles, tile, mentsuList1) //先初步計算牌型
                        val mentsuCompSet = mj4jHands.mentsuCompSet
                        val shuntsuList = //從所有可能的手牌中取得所有可以形成的順子
                            mentsuCompSet.flatMap { mentsuComp -> mentsuComp.shuntsuList }
                        shuntsuList.forEach { shuntsu ->
                            val middleTile = shuntsu.tile //順子中間這張牌
                            val previousTile = MahjongTile.values()[middleTile.code].previousTile.mahjong4jTile
                            val nextTile = MahjongTile.values()[middleTile.code].nextTile.mahjong4jTile
                            val shuntsuTiles = listOf(previousTile, middleTile, nextTile)
                            if (it.mahjong4jTile in shuntsuTiles) this -= it //立直後可以暗刻的牌必須不能在手牌中組成任何順子
                        }
                    }
                }
            }
        }

    /**
     * [hands] 中可以進行加槓的牌,
     *
     * @return 可以直接對在集合中的牌可以直接使用 [kakan]
     * */
    private val tilesCanKakan: MutableSet<Pair<MahjongTileEntity, ClaimTarget>>
        get() = mutableSetOf<Pair<MahjongTileEntity, ClaimTarget>>().apply {
            fuuroList.filter { it.mentsu is Kotsu }.forEach { fuuro -> //遍歷所有是刻子的副露
                val tile =
                    hands.find { it.mahjong4jTile.code == fuuro.claimTile.mahjong4jTile.code } //尋找手牌中與刻子的宣告牌 code 一樣的牌
                if (tile != null) this += tile to fuuro.claimTarget
            }
        }

    /**
     * 手牌 [hands] 中有與 [mjTileEntity] 可以組順子的牌就可以"吃",
     * 分三個情況判斷: 1.被吃的牌在頭 2.被吃的牌在中間 3.被吃的牌在尾,
     * 只要有一種情況存在就表示可以吃,
     * 並且 [mjTileEntity] "不包含在" [hands] 之中
     *
     * @return 玩家可以對 [mjTileEntity] 進行"吃"的所有組合
     * */
    private fun tilePairsForChii(mjTileEntity: MahjongTileEntity): List<Pair<MahjongTile, MahjongTile>> {
        val mj4jTile = mjTileEntity.mahjong4jTile
        if (mj4jTile.number == 0) return emptyList() //只有字牌 number 是 0, 不能吃
        val mjTile = mjTileEntity.mahjongTile
        val next = hands.find { it.mahjongTile == mjTile.nextTile }?.mahjongTile
        val nextNext = hands.find { it.mahjongTile == mjTile.nextTile.nextTile }?.mahjongTile
        val previous = hands.find { it.mahjongTile == mjTile.previousTile }?.mahjongTile
        val previousPrevious = hands.find { it.mahjongTile == mjTile.previousTile.previousTile }?.mahjongTile
        val pairs = mutableListOf<Pair<MahjongTile, MahjongTile>>()
        //被吃的牌在頭, number 需小於 8, 只有在下張牌存在跟下下張牌存在時才成立
        if (mj4jTile.number < 8 && next != null && nextNext != null) pairs += next to nextNext
        //被吃的牌在中間, number 需在 2..8 之間, 只有在上張牌存在跟下張牌存在時才成立
        if (mj4jTile.number in 2..8 && previous != null && next != null) pairs += previous to next
        //被吃的牌在尾, number 需大於 2,只有在上張牌存在跟上上張牌存在時才成立
        if (mj4jTile.number > 2 && previous != null && previousPrevious != null) pairs += previous to previousPrevious

        val sameTypeRedFiveTile = //手中的同個種類的赤寶牌, (同種類的赤寶牌取第一張就好, 因為牌都一樣是赤5)
            hands.filter { it.mahjongTile.isRed && it.mahjong4jTile.type == mj4jTile.type }.getOrNull(0)
        val canChiiWithRedFive = //可以使用紅寶牌吃的情況, 被吃的牌編號是 3,4,6,7 且 手中有同個種類的赤寶牌
            (mj4jTile.number in 3..4 || mj4jTile.number in 6..7) && sameTypeRedFiveTile != null
        if (canChiiWithRedFive) { //如果可以用赤寶牌吃的話
            val redFiveTile = sameTypeRedFiveTile!! //赤寶牌
            val redFiveTileCode = redFiveTile.mahjong4jTile.code
            val targetCode = mj4jTile.code
            // 求赤寶牌跟被吃的牌的差距, 1 為赤寶牌與被吃的牌連續, 2 為赤寶牌與被吃的牌組成中洞 (不會出現 0 或 3 以上)
            val gap = redFiveTileCode - targetCode
            if (gap.absoluteValue == 1) { //赤寶牌與被吃的牌連續, 檢查前後兩張牌有沒有存在
                val firstTile = MahjongTile.values()[minOf(redFiveTileCode, targetCode)].previousTile //開頭的牌
                val lastTile = MahjongTile.values()[maxOf(redFiveTileCode, targetCode)].nextTile //結尾的牌
                val allTileInHands = //開頭跟結尾的牌都存在手牌中
                    hands.any { it.mahjongTile == firstTile } && hands.any { it.mahjongTile == lastTile }
                if (allTileInHands) {
                    pairs += firstTile to lastTile
                }
            } else { //赤寶牌與被吃的牌組成中洞, 檢查手牌有沒有中間牌存在
                val midTileCode = (redFiveTileCode + targetCode) / 2 //中間牌的 code
                val midTile = MahjongTile.values()[midTileCode]
                val midTileInHands = hands.any { it.mahjongTile == midTile }
                if (midTileInHands) { //手牌中有存在中洞的這張牌
                    pairs += if (gap > 0) { //手中有 5, 6 萬吃 7 萬
                        redFiveTile.mahjongTile to midTile
                    } else { //手中有 4, 5 萬吃 3 萬
                        midTile to redFiveTile.mahjongTile
                    }
                }
            }
        }
        return pairs
    }

    /**
     * 需要在確保要碰的時候調用
     * */
    private fun tilePairForPon(mjTileEntity: MahjongTileEntity): Pair<MahjongTile, MahjongTile> {
        val tiles = tilesForPon(mjTileEntity)
        return tiles[0].mahjongTile to tiles[1].mahjongTile
    }

    /**
     * 詢問玩家立直時使用的方法,
     * 資料是 <可以打的牌,打了之後聽的牌>,
     * 調用的時候, 手牌應該會正好 14 張
     * */
    private val tilePairsForRiichi
        get() = buildList {
            if (hands.size != 14) return@buildList
            val listToAdd = buildList {
                hands.forEach { entity ->
                    val nowHands = hands.toMutableList().also { it -= entity }.toMahjongTileList()
                    val nowMachi = calculateMachi(hands = nowHands) //取得丟了這張牌後, 會聽的牌的列表
                    if (nowMachi.isNotEmpty()) { //丟了這張牌會聽
                        this += entity.mahjongTile to nowMachi
                    }
                }
            }
            addAll(listToAdd.distinct()) //這個列表內容要不能重複
        }

    /**
     * 取得 [hands] 中,
     * 與 [mjTileEntity] 相同編號的手牌,
     * */
    private fun sameTilesInHands(mjTileEntity: MahjongTileEntity): MutableList<MahjongTileEntity> =
        hands.filter { it.mahjong4jTile == mjTileEntity.mahjong4jTile }.toMutableList()

    /**
     * 是否聽牌
     * */
    val isTenpai: Boolean
        get() = machi.isNotEmpty()

    /**
     * 聽的牌的列表,
     * 同日文 "待ち" 的意思
     * */
    private val machi: List<MahjongTile>
        get() = calculateMachi()

    /**
     * 根據條件得到玩家聽哪張牌, 只計算聽的牌, 沒計算有沒有超過起胡翻數
     * 門清狀態下傳進來的 [hands] 加上 [fuuroList] 的牌應該只有 13 張, 第 14 張就是拿來偵測用的
     * */
    private fun calculateMachi(
        hands: List<MahjongTile> = this.hands.toMahjongTileList(),
        fuuroList: List<Fuuro> = this.fuuroList,
    ): List<MahjongTile> = MahjongTile.values().filter { //過濾列表
        val tileInHandsCount = hands.count { tile -> tile.mahjong4jTile == it.mahjong4jTile }
        val tileInFuuroCount =
            fuuroList.sumOf { fuuro -> fuuro.tileMjEntities.count { entity -> entity.mahjong4jTile == it.mahjong4jTile } }
        val allTileHere = (tileInHandsCount + tileInFuuroCount) == 4 //這張牌的所有牌都在這個玩家手上
        if (allTileHere) return@filter false //如果玩家持有這張牌的 4 張就直接跳過計算這張牌
        val nowHands = hands.toIntArray().apply { this[it.mahjong4jTile.code]++ }
        val mentsuList = fuuroList.map { fuuro -> fuuro.mentsu }
        if (nowHands.sum() > 14) {
            logger.error("計算 machi 時, 手牌大於 14 張牌, 請檢查錯誤")
            logger.error(nowHands.joinToString(prefix = "Hands: ") { code -> "$code" })
        }
        tilesWinnable(
            hands = nowHands,
            mentsuList = mentsuList,
            lastTile = it.mahjong4jTile,
        ) //這張牌構成勝利的牌型
    }

    /**
     * 計算 machi 與對應的翻數(役), 計算 machi 請參考 [calculateMachi]
     *
     * @return 回傳聽的牌與對應的翻數, -1 表示役滿
     * */
    fun calculateMachiAndHan(
        hands: List<MahjongTile> = this.hands.toMahjongTileList(),
        fuuroList: List<Fuuro> = this.fuuroList,
        rule: MahjongRule,
        generalSituation: GeneralSituation,
        personalSituation: PersonalSituation,
    ): Map<MahjongTile, Int> {
        val allMachi = calculateMachi(hands, fuuroList)
        return allMachi.associateWith { machiTile ->
            val yakuSettlement = calculateYakuSettlement(
                winningTile = machiTile,
                isWinningTileInHands = false,
                hands = hands,
                fuuroList = fuuroList,
                rule = rule,
                generalSituation = generalSituation,
                personalSituation = personalSituation,
                doraIndicators = emptyList(),
                uraDoraIndicators = emptyList()
            )
            yakuSettlement.let { if (it.yakuList.isNotEmpty() || it.yakumanList.isNotEmpty()) -1 else it.han }
        }
    }

    /**
     * 指定的牌 [tile] 是否振聽,
     * 與 [canWin] 分開使用,在榮和前使用
     *
     * @param discards 所有玩家丟過的牌, 須按照丟的順序排序
     * */
    fun isFuriten(tile: MahjongTileEntity, discards: List<MahjongTileEntity>): Boolean =
        isFuriten(tile.mahjong4jTile, discards.map { it.mahjong4jTile })

    /**
     * 振聽使用 mahjong4j 的 [Tile] 處理, 避免出現赤寶牌沒有算到的情況
     * */
    fun isFuriten(
        tile: Tile, discards: List<Tile>,
        machi: List<Tile> = this.machi.map { it.mahjong4jTile },
    ): Boolean {
        val discardedTiles = discardedTiles.map { it.mahjong4jTile }
        if (tile in discardedTiles) return true //一般振聽
        //考慮同巡振聽
        val lastDiscard = discardedTiles.last() //玩家丟過的最後一張牌
        val sameTurnStartIndex = discards.indexOf(lastDiscard) //取得丟過的最後一張牌在所有人丟過的牌中的索引
        for (index in sameTurnStartIndex until discards.lastIndex) { //從玩家最後丟的這張牌開始算到所有人丟過的牌的倒數第 2 張牌
            // (算到倒數第 2 張的意思是, 因為會在倒數第 1 張的時候才會調用這裡詢問玩家要不要榮和, 玩家如果沒選擇榮和才會振聽)
            if (discards[index] in machi) return true //有丟過的牌是玩家聽的牌, 同巡振聽成立
        }
        //考慮立直振聽
        val riichiSengenTile = riichiSengenTile?.mahjong4jTile ?: return false
        if (riichi || doubleRiichi) {
            val riichiStartIndex = discards.indexOf(riichiSengenTile)
            for (index in riichiStartIndex until discards.lastIndex) { //從玩家丟的立直宣言牌開始算到所有人丟過的牌的倒數第 2 張牌
                if (discards[index] in machi) return true //有丟過的牌是玩家聽的牌, 立直振聽成立
            }
        }
        return false
    }

    /**
     * 是否是一發
     *
     * @param players 所有玩家
     * @param discards 所有玩家丟過的牌, 須按照丟的順序排序
     * */
    fun isIppatsu(players: List<MahjongPlayerBase>, discards: List<MahjongTileEntity>): Boolean {
        if (riichi) { //有立直
            val riichiSengenIndex = discards.indexOf(riichiSengenTile!!)
            if (discards.lastIndex - riichiSengenIndex > 4) return false //相差大於 4 次丟牌, 即不可能 1 發, 剛好差 4 次表示一發自摸
            //檢查從立直宣言到最後丟的牌中間有任意一人鳴牌
            val someoneCalls = discards.slice(riichiSengenIndex..discards.lastIndex).any { tile ->
                players.any {
                    it.fuuroList.any { fuuro ->
                        tile in fuuro.tileMjEntities
                    }
                }
            }
            if (someoneCalls) return false //只要有人鳴牌, 一發就不成立
            return true
        }
        return false
    }

    /**
     * 手上的牌 [hands] 加上 [tile] 是否構成國世無雙的牌型 ([tile] 不包含在 [hands] 之中)
     * */
    fun isKokushimuso(tile: Tile): Boolean {
        val otherTiles = hands.toIntArray()
        //從副露取得已經確定的面子
        val mentusList = fuuroList.toMentsuList()
        //先初步計算牌型是否有符合贏的牌型
        val mj4jHands = Hands(otherTiles, tile, mentusList)
        return mj4jHands.isKokushimuso
    }


    /**
     * 將裝著 [MahjongTileEntity] 的列表轉成 Mahjong4j 判斷手牌用的 [IntArray]
     * */
    @JvmName("toIntArrayMahjongTileEntity")
    private fun List<MahjongTileEntity>.toIntArray() =
        IntArray(Tile.values().size) { code -> this.count { it.mahjong4jTile.code == code } }

    /**
     * 將裝著 [MahjongTile] 的列表轉成 Mahjong4j 判斷手牌用的 [IntArray]
     * */
    private fun List<MahjongTile>.toIntArray() =
        IntArray(Tile.values().size) { code -> this.count { it.mahjong4jTile.code == code } }

    private fun List<Fuuro>.toMentsuList() = this.map { it.mentsu }

    /**
     * 判斷勝利的方法, 從所有輸入的參數計算這個組合能贏嗎,
     * 振聽需另外使用 [isFuriten] 來判斷
     * */
    fun canWin(
        winningTile: MahjongTile,
        isWinningTileInHands: Boolean,
        hands: List<MahjongTile> = this.hands.toMahjongTileList(),
        fuuroList: List<Fuuro> = this.fuuroList,
        rule: MahjongRule,
        generalSituation: GeneralSituation,
        personalSituation: PersonalSituation,
    ): Boolean {
        val yakuSettlement = calculateYakuSettlement(
            winningTile = winningTile,
            isWinningTileInHands = isWinningTileInHands,
            hands = hands,
            fuuroList = fuuroList,
            rule = rule,
            generalSituation = generalSituation,
            personalSituation = personalSituation
        )
        return with(yakuSettlement) { yakumanList.isNotEmpty() || doubleYakumanList.isNotEmpty() || han >= rule.minimumHan.han }
    }

    /**
     * 判斷輸入的這組資料是否組成能贏的狀態, 不計算任何其他條件
     *
     * @param [hands] 必須包含 [lastTile]
     * @return [hands] 和 [mentsuList] 和 [lastTile] , 有沒有構成勝利的基本條件
     * */
    private fun tilesWinnable(
        hands: IntArray,
        mentsuList: List<Mentsu>,
        lastTile: Tile,
    ): Boolean = Hands(hands, lastTile, mentsuList).canWin //在 Hands 初始化的時候就有計算牌型是否有符合贏的牌型

    /**
     * 計算所有規則後得到的結果
     *
     * @param winningTile 贏的牌
     * @param isWinningTileInHands 贏的牌是否在手牌中
     * */
    private fun calculateYakuSettlement(
        winningTile: MahjongTile,
        isWinningTileInHands: Boolean,
        hands: List<MahjongTile>,
        fuuroList: List<Fuuro>,
        rule: MahjongRule,
        generalSituation: GeneralSituation,
        personalSituation: PersonalSituation,
        doraIndicators: List<MahjongTile> = listOf(),
        uraDoraIndicators: List<MahjongTile> = listOf(),
    ): YakuSettlement {
        val handsIntArray = hands.toIntArray().also { if (!isWinningTileInHands) it[winningTile.mahjong4jTile.code]++ }
        val mentsuList = fuuroList.toMentsuList() //從副露取得已知的面子
        val mj4jHands = Hands(handsIntArray, winningTile.mahjong4jTile, mentsuList)
        if (!mj4jHands.canWin) return YakuSettlement.NO_YAKU
        val mj4jPlayer = Player(mj4jHands, generalSituation, personalSituation).apply { calculate() }
        var finalHan = 0 //最後計算完的翻數, 役滿不會計算
        var finalFu = 0 //最後計算完的符數, 役滿不會計算
        var finalRedFiveCount = 0 //最後計算完的赤寶牌數量, 役滿不會計算
        var finalNormalYakuList = mutableListOf<NormalYaku>() //最後計算完的役種
        val finalYakumanList = mj4jPlayer.yakumanList.toMutableList()
        val finalDoubleYakumanList = mutableListOf<DoubleYakuman>()
        //下面開始進行各種計算
        if (finalYakumanList.isNotEmpty()) { //有役滿出現, 只計算役滿
            if (!rule.localYaku) { //沒開啟古役
                if (Yakuman.RENHO in finalYakumanList) finalYakumanList -= Yakuman.RENHO //去掉人和
            }
            //有雙倍役滿的情況 (ex: 大四喜, 四暗刻單騎, 純正九蓮寶燈, 國士無雙十三面)
            val handsWithoutWinningTile =
                hands.toMutableList().also { if (isWinningTileInHands) it -= winningTile } //拿到一個沒有包含 winningTile 的手牌
            val machiBeforeWin = calculateMachi(handsWithoutWinningTile, fuuroList) //計算還沒包含 winningTile 時候的 machi
            when {
                Yakuman.DAISUSHI in finalYakumanList -> { //大四喜當作雙倍役滿
                    finalYakumanList -= Yakuman.DAISUSHI
                    finalDoubleYakumanList += DoubleYakuman.DAISUSHI
                }
                Yakuman.KOKUSHIMUSO in finalYakumanList && machiBeforeWin.size == 13 -> { //如果國士無雙是十三面聽當作雙倍役滿
                    finalYakumanList -= Yakuman.KOKUSHIMUSO
                    finalDoubleYakumanList += DoubleYakuman.KOKUSHIMUSO_JUSANMENMACHI
                }
                Yakuman.CHURENPOHTO in finalYakumanList && machiBeforeWin.size == 9 -> { //如果純正九蓮當作雙倍役滿
                    finalYakumanList -= Yakuman.CHURENPOHTO
                    finalDoubleYakumanList += DoubleYakuman.JUNSEI_CHURENPOHTO
                }
                Yakuman.SUANKO in finalYakumanList && machiBeforeWin.size == 1 -> { //如果四暗刻單騎當作雙倍役滿
                    finalYakumanList -= Yakuman.SUANKO
                    finalDoubleYakumanList += DoubleYakuman.SUANKO_TANKI
                }
            }
        } else { //沒有役滿, 只有一般役
            //先計算出最好的 翻數 and 役種 and 牌組-> finalHan, finalNormalYakuList, finalComp
            //finalComp 只要有聽牌就不可能為 null, hands 初始化就計算過 mentsuCompSet 了
            var finalComp: MentsuComp =
                mj4jHands.mentsuCompSet.firstOrNull() ?: throw IllegalStateException("輸入的手牌沒有構成勝利的牌型")
            mj4jHands.mentsuCompSet.forEach { comp ->
                val yakuStock = mutableListOf<NormalYaku>()
                val resolverSet =
                    Mahjong4jYakuConfig.getNormalYakuResolverSet(comp, generalSituation, personalSituation)
                resolverSet.filter { it.isMatch }.forEach { yakuStock += it.normalYaku }

                //沒開啟食斷 且 沒有門前清 且 有斷么九, 去掉多的斷么九
                //這個 mj4jHands.isOpen 表示有面子是 open 的狀態, 即 沒有門前清
                if (!rule.openTanyao && mj4jHands.isOpen && NormalYaku.TANYAO in yakuStock) yakuStock -= NormalYaku.TANYAO

                val hanSum = if (mj4jHands.isOpen) yakuStock.sumOf { it.kuisagari } else yakuStock.sumOf { it.han }
                if (hanSum > finalHan) {
                    finalHan = hanSum
                    finalNormalYakuList = yakuStock
                    finalComp = comp
                }
            }
            //計算加入寶牌的翻數
            if (finalHan >= rule.minimumHan.han) { //寶牌不能用於計算起胡翻數, 至少要有起胡翻數後才能算
                val handsComp = mj4jHands.handsComp
                val isRiichi = NormalYaku.REACH in finalNormalYakuList
                //寶牌
                val doraAmount = generalSituation.dora.sumOf { handsComp[it.code] }
                repeat(doraAmount) {
                    NormalYaku.DORA.apply { finalNormalYakuList += this; finalHan += this.han }
                }
                //裏寶牌
                if (isRiichi) {
                    val uraDoraAmount = generalSituation.uradora.sumOf { handsComp[it.code] }
                    repeat(uraDoraAmount) {
                        NormalYaku.URADORA.apply { finalNormalYakuList += this; finalHan += this.han }
                    }
                }
                //赤寶牌
                if (rule.redFive != MahjongRule.RedFive.NONE) {
                    val handsRedFiveCount = this@MahjongPlayerBase.hands.count { it.mahjongTile.isRed }
                    val fuuroListRedFiveCount =
                        fuuroList.sumOf { it.tileMjEntities.count { tile -> tile.mahjongTile.isRed } }
                    finalRedFiveCount = handsRedFiveCount + fuuroListRedFiveCount //取得手上總共有多少紅寶牌
                    finalHan += finalRedFiveCount
                }
            }
            //計算符數
            finalFu = when {
                finalNormalYakuList.size == 0 -> 0
                NormalYaku.PINFU in finalNormalYakuList && NormalYaku.TSUMO in finalNormalYakuList -> 20
                NormalYaku.CHITOITSU in finalNormalYakuList -> 25
                else -> {
                    var tmpFu = 20
                    tmpFu += when {
                        personalSituation.isTsumo -> 2
                        !mj4jHands.isOpen -> 10
                        else -> 0
                    }
                    tmpFu += finalComp.allMentsu.sumOf { it.fu }
                    tmpFu +=
                        if (finalComp.isKanchan(mj4jHands.last) ||
                            finalComp.isPenchan(mj4jHands.last) ||
                            finalComp.isTanki(mj4jHands.last)
                        ) 2 else 0
                    val jantoTile = finalComp.janto.tile
                    if (jantoTile == generalSituation.bakaze) tmpFu += 2
                    if (jantoTile == personalSituation.jikaze) tmpFu += 2
                    if (jantoTile.type == TileType.SANGEN) tmpFu += 2
                    tmpFu
                }
            }
        }
        val fuuroListForSettlement = fuuroList.map { fuuro ->
            val isAnkan = fuuro.mentsu is Kantsu && !fuuro.mentsu.isOpen
            isAnkan to fuuro.tileMjEntities.toMahjongTileList()
        }
        val score = //只會計算這個玩家應該拿到多少點數
            if (finalYakumanList.isNotEmpty() || finalDoubleYakumanList.isNotEmpty()) { //有役滿
                val yakumanScore = finalYakumanList.size * 32000
                val doubleYakumanScore = finalDoubleYakumanList.size * 64000
                val scoreSum = yakumanScore + doubleYakumanScore
                if (personalSituation.isParent) (scoreSum * 1.5).toInt() else scoreSum
            } else { //沒有役滿
                Score.calculateScore(personalSituation.isParent, finalHan, finalFu).ron
            }
        return YakuSettlement(
            mahjongPlayer = this@MahjongPlayerBase,
            yakuList = finalNormalYakuList,
            yakumanList = finalYakumanList,
            doubleYakumanList = finalDoubleYakumanList,
            redFiveCount = finalRedFiveCount,
            winningTile = winningTile,
            fuuroList = fuuroListForSettlement,
            doraIndicators = doraIndicators,
            uraDoraIndicators = uraDoraIndicators,
            fu = finalFu,
            han = finalHan,
            score = score
        )
    }

    /**
     * 根據條件計算出應該贏得的點數,
     * 請在榮和或自摸時調用
     * */
    fun calcYakuSettlementForWin(
        winningTile: MahjongTile,
        isWinningTileInHands: Boolean,
        rule: MahjongRule,
        generalSituation: GeneralSituation,
        personalSituation: PersonalSituation,
        doraIndicators: List<MahjongTile>,
        uraDoraIndicators: List<MahjongTile>,
    ): YakuSettlement = calculateYakuSettlement(
        winningTile = winningTile,
        isWinningTileInHands = isWinningTileInHands,
        hands = this.hands.toMahjongTileList(),
        fuuroList = this.fuuroList,
        rule = rule,
        generalSituation = generalSituation,
        personalSituation = personalSituation,
        doraIndicators = doraIndicators,
        uraDoraIndicators = uraDoraIndicators
    )

    /**
     * 詢問並讓玩家丟牌的功能
     *
     * @param timeoutTile 當超時的時候會丟出去的牌
     * @param cannotDiscardTiles 不能丟掉的牌的列表,需要輸入 [MahjongTile]
     * @param skippable 客戶端可以跳過的, 會影響到自動摸切而已
     * @return 要丟出的牌的編號 [MahjongTile.code]
     * */
    open suspend fun askToDiscardTile(
        timeoutTile: MahjongTile,
        cannotDiscardTiles: List<MahjongTile>,
        skippable: Boolean,
    ): MahjongTile = hands.findLast { it.mahjongTile !in cannotDiscardTiles }?.mahjongTile ?: timeoutTile

    /**
     * 詢問玩家 [tile] 是否要 吃
     *
     * @param tilePairs 是能與 [tile] 編號可以組成順子的牌的編號對
     * @return 要與之配對的兩張牌, null 表示沒有要吃
     * */
    open suspend fun askToChii(
        tile: MahjongTile,
        tilePairs: List<Pair<MahjongTile, MahjongTile>>,
        target: ClaimTarget,
    ): Pair<MahjongTile, MahjongTile>? = null

    suspend fun askToChii(entity: MahjongTileEntity, target: ClaimTarget): Pair<MahjongTile, MahjongTile>? =
        askToChii(tile = entity.mahjongTile, tilePairs = tilePairsForChii(entity), target = target)

    /**
     * 詢問玩家 [tile] 是否要 碰或吃
     *
     * @param tilePairsForChii 是能與 [tile] 可以組成順子的牌對列表
     * @param tilePairForPon 是能與 [tile] 可以組成刻子的牌對
     * @return 要與之配對的兩張牌,兩個牌相同表示要碰, null 表示沒有要碰或吃
     * */
    open suspend fun askToPonOrChii(
        tile: MahjongTile,
        tilePairsForChii: List<Pair<MahjongTile, MahjongTile>>,
        tilePairForPon: Pair<MahjongTile, MahjongTile>,
        target: ClaimTarget,
    ): Pair<MahjongTile, MahjongTile>? = null

    suspend fun askToPonOrChii(entity: MahjongTileEntity, target: ClaimTarget): Pair<MahjongTile, MahjongTile>? =
        askToPonOrChii(
            tile = entity.mahjongTile,
            tilePairsForChii = tilePairsForChii(entity),
            tilePairForPon = tilePairForPon(entity),
            target = target
        )

    /**
     * 詢問玩家 編號 [tile] 的牌是否要 碰
     * @param tilePairForPon 是能與 [tile] 可以組成刻子的牌對
     * */
    open suspend fun askToPon(
        tile: MahjongTile,
        tilePairForPon: Pair<MahjongTile, MahjongTile>,
        target: ClaimTarget,
    ): Boolean = true

    suspend fun askToPon(entity: MahjongTileEntity, target: ClaimTarget): Boolean =
        askToPon(
            tile = entity.mahjongTile,
            tilePairForPon = tilePairForPon(entity),
            target = target
        )

    /**
     * 詢問玩家是否要 暗槓 或 加槓 (因為兩種情況有機會同時出現)
     *
     * @param canAnkanTiles 可以進行暗槓的牌
     * @param canKakanTiles 可以進行加槓的牌,必須附帶原本的碰牌對象
     * @param rule 拿來處裡有紅寶牌出現的情況
     * @return 要暗槓或加槓的牌的編號 [MahjongTile.code], null 表示沒有要暗槓或加槓
     * */
    open suspend fun askToAnkanOrKakan(
        canAnkanTiles: Set<MahjongTile>,
        canKakanTiles: Set<Pair<MahjongTile, ClaimTarget>>,
        rule: MahjongRule,
    ): MahjongTile? = null

    suspend fun askToAnkanOrKakan(rule: MahjongRule): MahjongTile? =
        askToAnkanOrKakan(
            canAnkanTiles = tilesCanAnkan.toList().toMahjongTileList().toSet(),
            canKakanTiles = tilesCanKakan.map { it.first.mahjongTile to it.second }.toSet(),
            rule = rule
        )


    /**
     * 詢問玩家 [tile] 是否要 明槓
     * */
    open suspend fun askToMinkanOrPon(
        tile: MahjongTile,
        target: ClaimTarget,
        rule: MahjongRule,
    ): MahjongGameBehavior =
        MahjongGameBehavior.MINKAN

    suspend fun askToMinkanOrPon(
        mjTileEntity: MahjongTileEntity,
        target: ClaimTarget,
        rule: MahjongRule,
    ): MahjongGameBehavior =
        askToMinkanOrPon(tile = mjTileEntity.mahjongTile, target = target, rule = rule)

    /**
     * 詢問玩家是否要立直
     * (詢問立直應該要傳入要丟的牌和對應會聽的牌)
     * */
    open suspend fun askToRiichi(
        tilePairsForRiichi: List<Pair<MahjongTile, List<MahjongTile>>> = this.tilePairsForRiichi,
    ): MahjongTile? = null

    /**
     * 詢問玩家是否要 自摸
     * */
    open suspend fun askToTsumo(): Boolean = true

    /**
     *  詢問玩家 [tile] 是否要 榮和
     * */
    open suspend fun askToRon(tile: MahjongTile, target: ClaimTarget): Boolean = true

    suspend fun askToRon(mjTileEntity: MahjongTileEntity, target: ClaimTarget): Boolean =
        askToRon(tile = mjTileEntity.mahjongTile, target = target)

    /**
     * 詢問玩家是否要九種九牌和局
     * */
    open suspend fun askToKyuushuKyuuhai(): Boolean = true

    /**
     * 拿牌的功能
     * */
    fun drawTile(tile: MahjongTileEntity) {
        hands += tile
        tile.ownerUUID = uuid
        tile.facing = TileFacing.HORIZONTAL
        tile.inGameTilePosition = TilePosition.HAND
        //目前拿牌沒有音效
    }

    /**
     * 立直的時候執行的動作, 在立直成立之後才執行,
     * [sticks] 會增加一根立直棒
     * */
    fun riichi(riichiSengenTile: MahjongTileEntity, isFirstRound: Boolean) {
        this.riichiSengenTile = riichiSengenTile //設定立直宣言牌
        if (isFirstRound) doubleRiichi = true else riichi = true //設定立直狀態
    }

    /**
     * 丟牌的功能, 僅需要麻將牌 [MahjongTile] 的 code 編號即可, 會回傳丟出去的牌
     *
     * @return 特殊情況會打出 null 的牌, ex: 在打牌的瞬間把桌子拆了
     * */
    fun discardTile(tile: MahjongTile): MahjongTileEntity? =
        hands.findLast { it.mahjongTile == tile }?.also {
            hands -= it
            it.facing = TileFacing.UP
            it.inGameTilePosition = TilePosition.OTHER
            discardedTiles += it
            discardedTilesForDisplay += it
            playSoundAtHandsMiddle(soundEvent = SoundEvents.BLOCK_BAMBOO_PLACE) //播放把牌打出去的聲音
        }

    /**
     * 將玩家的手牌翻出來,
     * 用在遊戲即將結束的時候
     * */
    fun openHands() {
        hands.forEach {
            it.facing = TileFacing.UP
            it.inGameTilePosition = TilePosition.OTHER
        }
        playSoundAtHandsMiddle(soundEvent = SoundEvents.ITEM_ARMOR_EQUIP_GENERIC)
    }

    /**
     * 將玩家的手牌蓋起來,
     * 用在遊戲即將結束的時候
     * */
    fun closeHands() {
        hands.forEach {
            it.facing = TileFacing.DOWN
            it.inGameTilePosition = TilePosition.OTHER
        }
        playSoundAtHandsMiddle(soundEvent = SoundEvents.ITEM_ARMOR_EQUIP_GENERIC)
    }

    /**
     * 在手牌中間的牌的位置上播放聲音
     * */
    private fun playSoundAtHandsMiddle(
        soundEvent: SoundEvent,
        category: SoundCategory = SoundCategory.VOICE,
        volume: Float = 1f,
        pitch: Float = 1f,
    ) {
        if (hands.size > 0) {
            val handsMiddleIndex = hands.size / 2
            hands[handsMiddleIndex].let {
                it.world.playSound(
                    null,
                    it.x,
                    it.y,
                    it.z,
                    soundEvent,
                    category,
                    volume,
                    pitch
                )
            }
        }
    }
}
