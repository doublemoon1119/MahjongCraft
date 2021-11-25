package doublemoon.mahjongcraft.game.mahjong.riichi

import doublemoon.mahjongcraft.entity.MahjongScoringStickEntity
import doublemoon.mahjongcraft.entity.MahjongScoringStickEntity.Companion.MAHJONG_POINT_STICK_DEPTH
import doublemoon.mahjongcraft.entity.MahjongScoringStickEntity.Companion.MAHJONG_POINT_STICK_HEIGHT
import doublemoon.mahjongcraft.entity.MahjongScoringStickEntity.Companion.MAHJONG_POINT_STICK_WIDTH
import doublemoon.mahjongcraft.entity.MahjongTileEntity
import doublemoon.mahjongcraft.entity.MahjongTileEntity.Companion.MAHJONG_TILE_DEPTH
import doublemoon.mahjongcraft.entity.MahjongTileEntity.Companion.MAHJONG_TILE_HEIGHT
import doublemoon.mahjongcraft.entity.MahjongTileEntity.Companion.MAHJONG_TILE_SMALL_PADDING
import doublemoon.mahjongcraft.entity.MahjongTileEntity.Companion.MAHJONG_TILE_WIDTH
import doublemoon.mahjongcraft.entity.TileFacing
import doublemoon.mahjongcraft.entity.TilePosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.minecraft.entity.Entity
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import org.mahjong4j.GeneralSituation
import org.mahjong4j.hands.Kantsu
import org.mahjong4j.tile.Tile
import org.mahjong4j.tile.TileType

/**
 * 在 [MahjongGame] 中所有與 [MahjongTileEntity] (麻將牌) 或者 [MahjongScoringStickEntity] (積棒) 有關的操作大部分都在這
 * */
class MahjongBoard(
    val game: MahjongGame
) {
    /**
     * [game] 的所有麻將牌, 用來裝所有牌的實體 [MahjongTileEntity],
     * 包含玩家的手牌
     * */
    val allTiles = mutableListOf<MahjongTileEntity>()

    /**
     * 牌山,
     * 不包含玩家手牌,
     * 也不包含被摸走的嶺上牌,
     * */
    val wall = mutableListOf<MahjongTileEntity>()

    /**
     * 王牌區,
     * 王牌區的順序是按照原本在牌山的順序排序的
     * */
    private val deadWall = mutableListOf<MahjongTileEntity>()

    /**
     * 槓牌計數
     * */
    var kanCount = 0
        private set

    /**
     * 丟的牌的列表, 會按照丟的順序排列
     * */
    val discards: MutableList<MahjongTileEntity> = mutableListOf()

    /**
     * 丟牌計數
     * */
    private val discardsCount: Int
        get() = game.players.sumOf { it.discardedTiles.size }

    /**
     * 目前沒有人有副露
     * */
    private val noFuuro: Boolean
        get() = game.players.find { it.fuuroList.size > 0 } == null

    /**
     * 是否第一輪就胡,
     * (丟出的牌小於等於 4 張且沒有人有副露)
     * */
    val isFirstRound: Boolean
        get() = discardsCount <= 4 && noFuuro

    /**
     * 現在是否是河底
     * */
    val isHoutei: Boolean
        get() = wall.size <= 4

    /**
     * 連續 4 次丟牌是否是同張風連打
     * */
    val isSuufonRenda: Boolean
        get() {
            if (discards.size < 4) return false
            val discards = discards.asReversed() //先將丟過的牌的列表倒過來牌, 方便處理
            val fonTile = discards[0]
            if (fonTile.mahjong4jTile.type != TileType.FONPAI) return false
            repeat(4) { if (discards[it].code != fonTile.code) return false }
            return true
        }

    /**
     * 場風牌
     * */
    private val bakaze: Tile
        get() = game.round.wind.tile

    /**
     * 寶牌指示牌,
     * 根據 [kanCount] 和 [deadWall] 取得對應的寶牌指示牌
     * */
    val doraIndicators: MutableList<MahjongTileEntity>
        get() = mutableListOf<MahjongTileEntity>().also { list ->
            val indicatorAmount = kanCount + 1  //寶牌指示牌應該有的數量
            repeat(indicatorAmount) {
                val doraIndicatorIndex = (4 - it) * 2 + kanCount //加上槓補進王牌區的牌
                list += deadWall[doraIndicatorIndex]
            }
        }

    /**
     * 裏牌指示牌,
     * 根據 [kanCount] 和 [deadWall] 取得對應的裏寶牌指示牌
     * */
    val uraDoraIndicators: MutableList<MahjongTileEntity>
        get() = mutableListOf<MahjongTileEntity>().also { list ->
            val indicatorAmount = kanCount + 1  //裏寶牌指示牌應該有的數量
            repeat(indicatorAmount) {
                val doraIndicatorIndex = (4 - it) * 2 + 1 + kanCount //加上槓補進王牌區的牌
                list += deadWall[doraIndicatorIndex]
            }
        }

    /**
     * 寶牌列表,
     * 根據 [doraIndicators] 取得對應的寶牌
     * */
    private val doraList: List<Tile>
        get() = doraIndicators.map { it.mahjongTile.nextTile.mahjong4jTile }

    /**
     * 寶牌列表,
     * 根據 [uraDoraIndicators] 取得對應的裏寶牌
     * */
    private val uraDoraList: List<Tile>
        get() = uraDoraIndicators.map { it.mahjongTile.nextTile.mahjong4jTile }

    /**
     * 根據 [kanCount] 來翻出寶牌指示牌
     * */
    private fun flipDoraIndicators() {
        doraIndicators.forEach {
            if (it.facing != TileFacing.UP) it.facing = TileFacing.UP
            if (it.inGameTilePosition != TilePosition.OTHER) it.inGameTilePosition = TilePosition.OTHER
        }
    }

    /**
     * 取得 mahjong4j 判斷用的 [GeneralSituation]
     * */
    val generalSituation: GeneralSituation
        get() = GeneralSituation(
            isFirstRound,
            isHoutei,
            bakaze,
            doraList,
            uraDoraList
        )

    /**
     * 清除所有牌 跟 [kanCount]
     * */
    fun clear() {
        kanCount = 0
        discards.clear()
        wall.clear()
        deadWall.clear()
        //最後清除掉所有牌的實體
        //有時候會同時觸發遊戲中的清除跟 MahjongGame.onBreak() 或 MahjongGame.onServerStopping() 的清除,
        //會導致有一個 allTiles.clear() 先執行, 後面另外一個正在執行 forEach 的直接報錯, 這裡直接先複製後再執行, 避免上述情況
        allTiles.toList().forEach { if (!it.isRemoved) it.remove(Entity.RemovalReason.DISCARDED) }
        allTiles.clear()
    }

    /**
     * 產生所有的牌 [allTiles],
     * 並將所有 [allTiles] 加進 [wall] 按照位置生成
     * */
    fun generateAllTilesAndSpawnWall() {
        val tableCenterPos = game.tableCenterPos
        val world = game.world
        allTiles.apply {
            when (game.rule.redFive) {
                MahjongRule.RedFive.NONE -> MahjongTile.normalWall
                MahjongRule.RedFive.THREE -> MahjongTile.redFive3Wall
                MahjongRule.RedFive.FOUR -> MahjongTile.redFive4Wall
            }.shuffled().forEach {
                //打亂牌後一個個加入
                this += MahjongTileEntity(
                    world = world,
                    code = it.code,
                    gameBlockPos = game.pos,
                    isSpawnedByGame = true,
                    inGameTilePosition = TilePosition.WALL,
                    gamePlayers = game.players.map { player -> player.uuid },
                    canSpectate = game.rule.spectate,
                    facing = TileFacing.DOWN
                )
            }
        }
        wall.apply {
            this += allTiles
            //傳送到對應的位置, 並在世界生成實體
            forEachIndexed { index, tile ->
                //各個方向有 34 張牌 (17敦 * 2 * 4 = 136)
                //調整牌的位置跟方向
                val directionOffset = 1.0
                val topOrBottom = (1 - index % 2) //這張牌在上面還是下面, 上面->1 下面->0
                val yOffset =
                    (topOrBottom * MAHJONG_TILE_DEPTH).toDouble() + (if (topOrBottom == 1) MAHJONG_TILE_SMALL_PADDING else 0.0)
                val startingPos = (17.0 * MAHJONG_TILE_WIDTH) / 2.0 - MAHJONG_TILE_HEIGHT
                val stackNum = (index / 2) % 17
                val stackWidth = stackNum * (MAHJONG_TILE_WIDTH + MAHJONG_TILE_SMALL_PADDING)
                when (index / 34) { //這裡面朝的方向就是以遊戲內的東為準，按照麻將桌"順"時針東南西北的方向
                    0 -> { //遊戲中面朝東
                        tableCenterPos.add(directionOffset, yOffset, -startingPos + stackWidth).apply {
                            tile.refreshPositionAfterTeleport(this)
                            tile.yaw = -90f
                        }
                    }
                    1 -> { //遊戲中面朝南
                        tableCenterPos.add(startingPos - stackWidth, yOffset, directionOffset).apply {
                            tile.refreshPositionAfterTeleport(this)
                            tile.yaw = 0f
                        }
                    }
                    2 -> { //遊戲中面朝西
                        tableCenterPos.add(-directionOffset, yOffset, startingPos - stackWidth).apply {
                            tile.refreshPositionAfterTeleport(this)
                            tile.yaw = 90f
                        }
                    }
                    else -> { //遊戲中面朝北
                        tableCenterPos.add(-startingPos + stackWidth, yOffset, -directionOffset).apply {
                            tile.refreshPositionAfterTeleport(this)
                            tile.yaw = 180f
                        }
                    }
                }
                //將牌生成到遊戲中
                world.spawnEntity(tile)
            }
        }
        game.playSound(soundEvent = SoundEvents.ENTITY_ITEM_PICKUP)
    }

    /**
     * 從 [wall] 移除最後 7 敦並將其加入進 [deadWall],
     * 再對王牌區進行一點點偏移, 讓他與 [wall] 的牌產生間隔
     * 王牌會集中到與最後一敦嶺上牌同一個方向
     * */
    fun assignDeadWall() {
        with(deadWall) {
            for (index in 0 until 14) {
                this += wall.last()
                wall.removeLast()
            }
            reverse()
            forEach {
                //偏移王牌區
                val gap = MAHJONG_TILE_SMALL_PADDING * 20
                when (it.horizontalFacing) {
                    Direction.EAST -> it.refreshPositionAfterTeleport(it.x, it.y, it.z + gap)
                    Direction.SOUTH -> it.refreshPositionAfterTeleport(it.x - gap, it.y, it.z)
                    Direction.WEST -> it.refreshPositionAfterTeleport(it.x, it.y, it.z - gap)
                    Direction.NORTH -> it.refreshPositionAfterTeleport(it.x + gap, it.y, it.z)
                    else -> {
                    }
                }
            }
            // 王牌區方向以最後一敦為準
            val direction = deadWall.last().horizontalFacing
            // 將與王牌區不同方向的捕到王牌區旁邊並將方向轉正
            reversed().let {
                it.forEachIndexed { index, mahjongTile ->
                    with(mahjongTile) {
                        if (this.horizontalFacing != direction) {
                            //整除 2 表示牌在下面,反之牌在上面
                            yaw = direction.asRotation()
                            if (index % 2 == 0) {
                                it.forEach { tile ->
                                    when (direction) {
                                        Direction.EAST -> tile.refreshPositionAfterTeleport(
                                            tile.x,
                                            tile.y,
                                            tile.z + MAHJONG_TILE_WIDTH
                                        )
                                        Direction.SOUTH -> tile.refreshPositionAfterTeleport(
                                            tile.x - MAHJONG_TILE_WIDTH,
                                            tile.y,
                                            tile.z
                                        )
                                        Direction.WEST -> tile.refreshPositionAfterTeleport(
                                            tile.x,
                                            tile.y,
                                            tile.z - MAHJONG_TILE_WIDTH
                                        )
                                        Direction.NORTH -> tile.refreshPositionAfterTeleport(
                                            tile.x + MAHJONG_TILE_WIDTH,
                                            tile.y,
                                            tile.z
                                        )
                                        else -> {
                                        }
                                    }
                                }
                            }
                            val posY = if (index % 2 == 0) it[0].y else it[1].y
                            val offset = (MAHJONG_TILE_WIDTH + MAHJONG_TILE_SMALL_PADDING) * (index / 2)
                            when (direction) {
                                Direction.EAST -> mahjongTile.refreshPositionAfterTeleport(
                                    it[0].x,
                                    posY,
                                    it[0].z - offset
                                )
                                Direction.SOUTH -> mahjongTile.refreshPositionAfterTeleport(
                                    it[0].x + offset,
                                    posY,
                                    it[0].z
                                )
                                Direction.WEST -> mahjongTile.refreshPositionAfterTeleport(
                                    it[0].x,
                                    posY,
                                    it[0].z + offset
                                )
                                Direction.NORTH -> mahjongTile.refreshPositionAfterTeleport(
                                    it[0].x - offset,
                                    posY,
                                    it[0].z
                                )
                                else -> {
                                }
                            }
                        }
                    }
                }
            }
            // 翻開寶牌指示牌
            flipDoraIndicators()
        }
    }


    /**
     * 根據骰子點數 [dicePoints] 跟莊家的位置分配手牌,最後再將手牌移出牌山 [wall],
     * 這裡並沒有一次發 4 張牌, 都是直接把整副牌發完的
     **/
    suspend fun assignWallAndHands(dicePoints: Int) {
        withContext(Dispatchers.Default) {
            val directionIndex = (4 - ((dicePoints % 4 - 1) + game.round.round) % 4) //從哪邊開始拿牌
            val startingStackIndex = 2 * dicePoints //從開始處數第幾張牌開始拿
            val dealer = game.seat[game.round.round] //莊家
            val newWall = mutableListOf<MahjongTileEntity>().apply {
                //按照摸牌的順序, 建立 newWall
                repeat(wall.size) { times ->
                    val tileIndex = (directionIndex * 34 + startingStackIndex + times) % wall.size
                    this += wall[tileIndex]
                }
            }
            //分配玩家的手牌
            game.seat.forEach { mjPlayer ->
                //莊家 14 張,剩下每個人 13 張牌, 共 13 * 3 + 14 = 53
                val tileAmount = if (mjPlayer == dealer) 14 else 13
                repeat(tileAmount) {
                    val tile = newWall.removeFirst() //摸走最前面的牌
                    tile.isInvisible = true //先暫時隱形一下
                    mjPlayer.takeTile(tile)
                    if (it == 13) sortHands(player = mjPlayer, lastTile = tile)
                    else sortHands(player = mjPlayer)
                }
            }
            //將牌山替換成新的牌山
            wall.clear()
            wall += newWall
            //從莊家開始顯示牌, 這樣看起來比較牛逼
            repeat(4) { times ->
                if (game.seat.size == 0) return@repeat //可能是遊戲中途消失了
                val seatIndex = (game.round.round + times) % 4
                game.seat[seatIndex].hands.forEach { it.isInvisible = false }
                game.playSound(soundEvent = SoundEvents.ENTITY_ITEM_PICKUP, volume = 0.3f, pitch = 2.0f)
                delay(250)
            }
        }
    }

    /**
     * 將指定的玩家的"手牌"按順序整理,
     * 會排好放在玩家面前
     *
     * @param lastTile 指定的最後一張牌, 不會被排序進手牌堆中
     * */
    fun sortHands(
        player: MahjongPlayerBase,
        lastTile: MahjongTileEntity? = null
    ) {
        if (player !in game.seat) return
        val seatIndex = game.seat.indexOf(player)
        val tableCenterPos = game.tableCenterPos
        with(player) {
            val tileAmount = hands.size
            hands.sortBy { it.mahjongTile.sortOrder }
            lastTile?.let { //將指定的最後一張牌,移到最後的位置
                hands -= it
                hands += it
            }
            hands.forEachIndexed { index, hTile ->
                //調整牌的位置跟方向
                val directionOffset = 1.0 + MAHJONG_TILE_DEPTH + MAHJONG_TILE_HEIGHT
                val fuuroOffset = //防止副露太長擋到手牌
                    if (fuuroList.size < 3) 0.0 else (fuuroList.size - 2.0) * (MAHJONG_TILE_WIDTH)
                val sticksOffset = //防止積棒太長
                    if (sticks.size < 3) 0.0 else (sticks.size - 2.0) * (MAHJONG_POINT_STICK_DEPTH)
                val startingPos =
                    (tileAmount * MAHJONG_TILE_WIDTH + (tileAmount - 1) * MAHJONG_TILE_SMALL_PADDING) / 2.0 + fuuroOffset + sticksOffset
                val stackOffset = //加了一點小間隙, 如果是指定的最後一張牌會偏移多一點點
                    index * (MAHJONG_TILE_WIDTH + MAHJONG_TILE_SMALL_PADDING) + if (hTile == lastTile) MAHJONG_TILE_SMALL_PADDING * 15.0 else 0.0
                when (seatIndex) { //這裡的方向就是以遊戲內的面朝東為準，按照麻將桌"逆"時針東南西北的方向
                    0 -> { //東
                        tableCenterPos.add(directionOffset, 0.0, startingPos - stackOffset).apply {
                            hTile.refreshPositionAfterTeleport(this)
                            hTile.yaw = -90f
                        }
                    }
                    3 -> { //南
                        tableCenterPos.add(-startingPos + stackOffset, 0.0, directionOffset).apply {
                            hTile.refreshPositionAfterTeleport(this)
                            hTile.yaw = 0f
                        }
                    }
                    2 -> { //西
                        tableCenterPos.add(-directionOffset, 0.0, -startingPos + stackOffset).apply {
                            hTile.refreshPositionAfterTeleport(this)
                            hTile.yaw = 90f
                        }
                    }
                    else -> { //北
                        tableCenterPos.add(startingPos - stackOffset, 0.0, -directionOffset).apply {
                            hTile.refreshPositionAfterTeleport(this)
                            hTile.yaw = 180f
                        }
                    }
                }
            }
        }
    }


    /**
     * 將指定的玩家的"顯示用棄牌堆"整理,
     * 會排好放在玩家面前
     *
     * @param openDoorPlayer 開門玩家, 用來避免開門那方的玩家放不下所有棄牌
     * */
    fun sortDiscardedTilesForDisplay(
        player: MahjongPlayerBase,
        openDoorPlayer: MahjongPlayerBase
    ) {
        if (player !in game.seat) return
        val seatIndex = game.seat.indexOf(player)
        val tableCenterPos = game.tableCenterPos
        val halfWidthOfSixTiles = (MAHJONG_TILE_WIDTH * 6 / 2.0)
        val paddingFromCenter =
            halfWidthOfSixTiles + MAHJONG_TILE_HEIGHT / 2.0 + MAHJONG_TILE_HEIGHT / 4.0 //距離牌桌中心到棄牌堆第 1 張牌的距離 y
        val basicOffset = halfWidthOfSixTiles - MAHJONG_TILE_WIDTH / 2.0 //實體的座標在實體正中心, 所以給一半牌的寬度當作偏移
        val halfTileWidthAndHalfTileHeight = (MAHJONG_TILE_HEIGHT + MAHJONG_TILE_WIDTH) / 2.0
        val startingPos = when (seatIndex) { //這個是第一張牌的座標
            //這裡的方向就是以遊戲內的面朝東為準，按照麻將桌"逆"時針東南西北的方向
            0 -> tableCenterPos.add(paddingFromCenter, 0.0, basicOffset) //東
            3 -> tableCenterPos.add(-basicOffset, 0.0, paddingFromCenter) //南
            2 -> tableCenterPos.add(-paddingFromCenter, 0.0, -basicOffset) //西
            else -> tableCenterPos.add(basicOffset, 0.0, -paddingFromCenter) //北
        }
        val tileOffset = when (seatIndex) { //以牌桌邊緣為底, 垂直擺放的牌的偏移量 (由左手往右手方向)
            0 -> Vec3d(0.0, 0.0, -MAHJONG_TILE_WIDTH.toDouble()) //東
            3 -> Vec3d(MAHJONG_TILE_WIDTH.toDouble(), 0.0, 0.0) //南
            2 -> Vec3d(0.0, 0.0, MAHJONG_TILE_WIDTH.toDouble()) //西
            else -> Vec3d(-MAHJONG_TILE_WIDTH.toDouble(), 0.0, 0.0) //北
        }
        val riichiTileOffset = when (seatIndex) { //以牌桌邊緣為底, 垂直擺放的牌的偏移量 (由左手往右手方向)
            0 -> Vec3d(0.0, 0.0, -halfTileWidthAndHalfTileHeight) //東
            3 -> Vec3d(halfTileWidthAndHalfTileHeight, 0.0, 0.0) //南
            2 -> Vec3d(0.0, 0.0, halfTileWidthAndHalfTileHeight) //西
            else -> Vec3d(-halfTileWidthAndHalfTileHeight, 0.0, 0.0) //北
        }
        val lineOffset = when (seatIndex) { //以牌桌邊緣為底, 垂直擺放的牌的偏移量 (由上往下方向)
            0 -> Vec3d(MAHJONG_TILE_HEIGHT.toDouble() + MAHJONG_TILE_SMALL_PADDING, 0.0, 0.0) //東
            3 -> Vec3d(0.0, 0.0, MAHJONG_TILE_HEIGHT.toDouble() + MAHJONG_TILE_SMALL_PADDING) //南
            2 -> Vec3d(-MAHJONG_TILE_HEIGHT.toDouble() - MAHJONG_TILE_SMALL_PADDING, 0.0, 0.0) //西
            else -> Vec3d(0.0, 0.0, -MAHJONG_TILE_HEIGHT.toDouble() - MAHJONG_TILE_SMALL_PADDING) //北
        }
        val smallGapOffset = when (seatIndex) { //擺放的牌的間隔偏移量 (由左手往右手方向)
            0 -> Vec3d(0.0, 0.0, -MAHJONG_TILE_SMALL_PADDING) //東
            3 -> Vec3d(MAHJONG_TILE_SMALL_PADDING, 0.0, 0.0) //南
            2 -> Vec3d(0.0, 0.0, MAHJONG_TILE_SMALL_PADDING) //西
            else -> Vec3d(-MAHJONG_TILE_SMALL_PADDING, 0.0, 0.0) //北
        }
        val tileRot = when (seatIndex) { //牌的面朝方向
            0 -> -90f       //東
            3 -> 0f         //南
            2 -> 90f        //西
            else -> 180f    //北
        }
        var nowPos = startingPos
        val riichiSengenTile = player.riichiSengenTile?.let {
            if (it !in player.discardedTilesForDisplay) { //立直宣言牌不在顯示用棄牌堆, 表示被鳴牌走了
                val indexOfRiichiSengenTile = player.discardedTiles.indexOf(it) //立直宣言牌在玩家丟過的牌的索引
                player.discardedTiles.let { tiles ->
                    tiles.find { tile -> //找到第 1 個索引大於立直宣言牌的牌
                        tiles.indexOf(tile) > indexOfRiichiSengenTile
                    }
                }
            } else it
        }
        player.discardedTilesForDisplay.forEachIndexed { index, tileEntity ->
            val firstTileInThisLine = index % 6 == 0 //是否是那行的第 1 張牌
            val isRiichiTile = tileEntity == riichiSengenTile //這張是不是立直宣告牌
            val lastTileIsRiichiTile = //判斷上一張牌是不是立直宣告牌
                if (index == 0) false else player.discardedTilesForDisplay[index - 1] == riichiSengenTile
            val lineCount = index / 6 //第幾行
            if (lineCount > 0 && firstTileInThisLine) {  //每行的第 1 張牌 (不包括第 1 行)
                if (!(openDoorPlayer == player && index >= 18)) {
                    //如果不是開門玩家的第 3 行,會往下偏移一行
                    nowPos = startingPos
                    repeat(lineCount) { nowPos = nowPos.add(lineOffset) }
                }
            }
            nowPos =
                if (firstTileInThisLine) {
                    if (isRiichiTile) nowPos.add(riichiTileOffset).subtract(tileOffset)
                    else nowPos
                } else {
                    if (isRiichiTile || lastTileIsRiichiTile) nowPos.add(riichiTileOffset)
                    else nowPos.add(tileOffset)
                }
            nowPos = if (firstTileInThisLine) nowPos else nowPos.add(smallGapOffset) //第 1 張牌不會有縫隙偏移
            val yRot = if (isRiichiTile) tileRot - 90 else tileRot
            tileEntity.refreshPositionAndAngles(nowPos.x, nowPos.y, nowPos.z, yRot, 0f)
            tileEntity.facing = TileFacing.UP
        }
    }

    /**
     * 將指定玩家的"副露"整理,
     * 會排好放在玩家面前, 靠近右手的麻將桌角落
     * 調整副露的位置, 配合積棒的位置
     * */
    fun sortFuuro(
        player: MahjongPlayerBase
    ) {
        //記得實體的座標是以實體本身底部的正中央為主
        if (player !in game.seat) return
        val seatIndex = game.seat.indexOf(player)
        val tableCenterPos = game.tableCenterPos
        val halfTableLengthNoBorder = 0.5 + 15.0 / 16.0 //沒有包含邊框的半張桌子長度
        val halfHeightOfTile = MAHJONG_TILE_HEIGHT / 2.0 //麻將牌高度的一半
        val startingPos = when (seatIndex) { //計算玩家右手方的牌桌角落靠邊的座標
            //這裡的方向就是以遊戲內的面朝東為準，按照麻將桌"逆"時針東南西北的方向
            0 -> tableCenterPos.add(halfTableLengthNoBorder - halfHeightOfTile, 0.0, -halfTableLengthNoBorder) //東
            3 -> tableCenterPos.add(halfTableLengthNoBorder, 0.0, halfTableLengthNoBorder - halfHeightOfTile) //南
            2 -> tableCenterPos.add(-halfTableLengthNoBorder + halfHeightOfTile, 0.0, halfTableLengthNoBorder) //西
            else -> tableCenterPos.add(
                -halfTableLengthNoBorder,
                0.0,
                -halfTableLengthNoBorder + halfHeightOfTile
            ) //北
        }.let { //計算出角落座標後, 檢查有沒有積棒的存在
            val lastStickOfFirstStack = //尋找第一疊最後一根積棒的位置, null 表示沒有任何積棒
                player.sticks.findLast { stick -> player.sticks.indexOf(stick) < MahjongGame.STICKS_PER_STACK }
            if (lastStickOfFirstStack != null) { //如果有積棒的存在
                val stickPos = lastStickOfFirstStack.pos //積棒的位置
                val offset = stickPos.subtract(it) //算出點棒到角落座標的偏移
                val halfDepthOfStick = MAHJONG_POINT_STICK_DEPTH / 2.0 //積棒深度的一半
                val pos = when (seatIndex) { //計算出只求 與積棒擺放方向平行的位置的偏移 後的角落座標
                    0 -> it.add(offset.multiply(0.0, 0.0, 1.0))    //東, 只取  z 軸方向偏移
                    3 -> it.add(offset.multiply(1.0, 0.0, 0.0))    //南, 只取  x 軸方向偏移
                    2 -> it.add(offset.multiply(0.0, 0.0, -1.0))   //西, 只取 -z 軸方向偏移
                    else -> it.add(offset.multiply(-1.0, 0.0, 0.0))//北, 只取 -x 軸方向偏移
                }
                when (seatIndex) { //再將 積棒的深度 補上, 讓他對齊積棒的邊
                    0 -> pos.add(0.0, 0.0, halfDepthOfStick)
                    3 -> pos.add(-halfDepthOfStick, 0.0, 0.0)
                    2 -> pos.add(0.0, 0.0, -halfDepthOfStick)
                    else -> pos.add(halfDepthOfStick, 0.0, 0.0)
                }
            } else it //沒有積棒就維持原本的座標
        }
        val tileGap = MAHJONG_TILE_SMALL_PADDING
        val verticalTileOffset = when (seatIndex) { //以牌桌邊緣為底, 垂直擺放的牌的偏移量 (由右手往左手方向)
            0 -> Vec3d(0.0, 0.0, MAHJONG_TILE_WIDTH.toDouble() + tileGap)      //東
            3 -> Vec3d(-MAHJONG_TILE_WIDTH.toDouble() - tileGap, 0.0, 0.0)     //南
            2 -> Vec3d(0.0, 0.0, -MAHJONG_TILE_WIDTH.toDouble() - tileGap)     //西
            else -> Vec3d(MAHJONG_TILE_WIDTH.toDouble() + tileGap, 0.0, 0.0)   //北
        }
        val halfVerticalTileOffset = when (seatIndex) { //以牌桌邊緣為底, 垂直擺放的半張牌的偏移量 (由右手往左手方向)
            0 -> verticalTileOffset.multiply(1.0, 1.0, 0.5)      //東
            3 -> verticalTileOffset.multiply(0.5, 1.0, 1.0)     //南
            2 -> verticalTileOffset.multiply(1.0, 1.0, 0.5)     //西
            else -> verticalTileOffset.multiply(0.5, 1.0, 1.0)   //北
        }
        val horizontalTileOffset = when (seatIndex) { //以牌桌邊緣為底, 水平擺放的牌的偏移量 (由右手往左手方向)
            0 -> Vec3d(0.0, 0.0, MAHJONG_TILE_HEIGHT.toDouble() + tileGap)         //東
            3 -> Vec3d(-MAHJONG_TILE_HEIGHT.toDouble() - tileGap, 0.0, 0.0)        //南
            2 -> Vec3d(0.0, 0.0, -MAHJONG_TILE_HEIGHT.toDouble() - tileGap)        //西
            else -> Vec3d(MAHJONG_TILE_HEIGHT.toDouble() + tileGap, 0.0, 0.0)      //北
        }
        val halfHorizontalTileOffset = when (seatIndex) { //以牌桌邊緣為底, 水平擺放的牌的偏移量 (由右手往左手方向)
            0 -> horizontalTileOffset.multiply(1.0, 1.0, 0.5)         //東
            3 -> horizontalTileOffset.multiply(0.5, 1.0, 1.0)        //南
            2 -> horizontalTileOffset.multiply(1.0, 1.0, 0.5)        //西
            else -> horizontalTileOffset.multiply(0.5, 1.0, 1.0)      //北
        }
        val kakanOffset = when (seatIndex) {
            0 -> Vec3d(-MAHJONG_TILE_WIDTH.toDouble() - tileGap, 0.0, 0.0)         //東
            3 -> Vec3d(0.0, 0.0, -MAHJONG_TILE_WIDTH.toDouble() - tileGap)        //南
            2 -> Vec3d(MAHJONG_TILE_WIDTH.toDouble() + tileGap, 0.0, 0.0)        //西
            else -> Vec3d(0.0, 0.0, MAHJONG_TILE_WIDTH.toDouble() + tileGap)      //北
        }
        val halfGapBetweenHeightAndWidth = (MAHJONG_TILE_HEIGHT - MAHJONG_TILE_WIDTH) / 2.0
        val horizontalTileGravityOffset = when (seatIndex) { //以牌桌邊緣為底, 水平擺放的牌的偏移量 (由右手往左手方向)
            0 -> Vec3d(halfGapBetweenHeightAndWidth, 0.0, 0.0)         //東
            3 -> Vec3d(0.0, 0.0, halfGapBetweenHeightAndWidth)        //南
            2 -> Vec3d(-halfGapBetweenHeightAndWidth, 0.0, 0.0)        //西
            else -> Vec3d(0.0, 0.0, -halfGapBetweenHeightAndWidth)      //北
        }
        val tileRot = when (seatIndex) { //牌的面朝方向
            0 -> -90f       //東
            3 -> 0f         //南
            2 -> 90f        //西
            else -> 180f    //北
        }
        var nowPos = startingPos
        var tileCount = 0
        var lastTile: MahjongTileEntity? = null
        var lastClaimTile: MahjongTileEntity? = null
        fun placeEachTile(fuuro: Fuuro, isKakan: Boolean = false) {
            val tiles =
                if (isKakan) fuuro.tileMjEntities.toMutableList()
                else fuuro.tileMjEntities.sortedByDescending { it.mahjongTile.sortOrder }.toMutableList()
            val kakanTile = if (isKakan) tiles.removeLast() else null //加槓牌必定是最後一張, 先將加槓牌移出列表, 最後再加入
            tiles -= fuuro.claimTile //再將被鳴的牌移出列表
            when (fuuro.claimTarget) { //再將被鳴的牌放回列表上應該存放的位置
                ClaimTarget.RIGHT -> tiles.add(0, fuuro.claimTile)
                ClaimTarget.LEFT -> tiles.add(fuuro.claimTile)
                ClaimTarget.ACROSS -> tiles.add(1, fuuro.claimTile)
                else -> {
                }
            }
            tiles.forEach {
                val isClaimTile = it == fuuro.claimTile
                val yRot =
                    if (it != fuuro.claimTile) tileRot
                    else when (fuuro.claimTarget) { //根據鳴牌對象轉向
                        ClaimTarget.RIGHT -> tileRot + 90
                        ClaimTarget.LEFT -> tileRot - 90
                        ClaimTarget.ACROSS -> tileRot + 90
                        else -> tileRot
                    }
                val isLastTileHorizontal: Boolean = lastTile == lastClaimTile
                nowPos = if (tileCount == 0) {
                    if (isClaimTile) nowPos.add(halfHorizontalTileOffset)
                    else nowPos.add(halfVerticalTileOffset)
                } else if (isClaimTile || isLastTileHorizontal) {
                    if (isClaimTile && isLastTileHorizontal)
                        nowPos.add(horizontalTileOffset)
                    else
                        nowPos.add(halfHorizontalTileOffset)
                            .add(halfVerticalTileOffset)
                } else nowPos.add(verticalTileOffset)
                val pos = if (!isClaimTile) nowPos else nowPos.add(horizontalTileGravityOffset)
                it.refreshPositionAndAngles(pos.x, pos.y, pos.z, yRot, 0f)
                it.facing = TileFacing.UP
                lastTile = it
                if (isClaimTile) lastClaimTile = it
                tileCount++
            }
            kakanTile?.let { //有加槓牌的話
                val claimTile = fuuro.claimTile
                val claimTilePos = fuuro.claimTile.pos
                val pos = claimTilePos.add(kakanOffset)
                it.refreshPositionAndAngles(pos.x, pos.y, pos.z, claimTile.yaw, 0f) //傳送到鳴的牌的上面
                it.facing = TileFacing.UP
            }
        }
        player.fuuroList.forEach { fuuro ->
            if (fuuro.mentsu is Kantsu && fuuro.claimTarget == ClaimTarget.SELF) { //是暗槓
                fuuro.tileMjEntities.forEachIndexed { index, tileMjEntity ->
                    nowPos =
                        if (tileCount == 0) nowPos.add(halfVerticalTileOffset)
                        else nowPos.add(verticalTileOffset)
                    tileMjEntity.refreshPositionAndAngles(nowPos.x, nowPos.y, nowPos.z, tileRot, 0f)
                    tileMjEntity.facing = //index == 1 or 2 是中間兩張牌->面朝上, 否則是旁邊兩張->面朝下
                        if (index == 1 || index == 2) TileFacing.UP else TileFacing.DOWN
                    tileCount++
                }
            } else if (fuuro.mentsu is Kakantsu) { //是加槓
                placeEachTile(fuuro = fuuro, isKakan = true)
            } else { //不是暗槓也不是加槓
                placeEachTile(fuuro = fuuro, isKakan = false)
            }
        }
    }

    /**
     * 玩家摸嶺上牌,
     * 在玩家進行槓之後使用
     *
     * @return 摸到的嶺上牌
     * */
    fun takeRinshanTile(player: MahjongPlayerBase): MahjongTileEntity {
        val tile = if (kanCount % 2 == 0) deadWall[deadWall.size - 2] else deadWall[deadWall.size - 1]
        val lastWallTile = wall.removeLast()
        val direction = deadWall.last().horizontalFacing // 王牌區方向以最後一敦為準
        val baseTile = deadWall[1] //補進來的嶺上牌都固定會在第 2 張牌的旁邊
        val basePos = baseTile.pos
        val tilePos = when (direction) {
            Direction.EAST -> basePos.add(0.0, 0.0, -MAHJONG_TILE_WIDTH.toDouble())
            Direction.SOUTH -> basePos.add(MAHJONG_TILE_WIDTH.toDouble(), 0.0, 0.0)
            Direction.WEST -> basePos.add(0.0, 0.0, MAHJONG_TILE_WIDTH.toDouble())
            Direction.NORTH -> basePos.add(-MAHJONG_TILE_WIDTH.toDouble(), 0.0, 0.0)
            else -> basePos
        }
        lastWallTile.refreshPositionAfterTeleport(tilePos)
        lastWallTile.yaw = direction.asRotation()
        deadWall.add(0, lastWallTile)
        deadWall -= tile
        kanCount++
        flipDoraIndicators()
        player.takeTile(tile)
        if (kanCount % 2 == 1) {
            val nowLastWallTile = wall.last() //這張牌要往下降, 不然會浮空
            nowLastWallTile.refreshPositionAfterTeleport(
                nowLastWallTile.pos.add(
                    0.0,
                    MAHJONG_TILE_DEPTH.toDouble(),
                    0.0
                )
            )
        }
        return tile
    }

    /**
     * 放置這個玩家的立直棒,
     * 只有宣告立直的時候會呼叫這邊,
     * 這一根立直棒會在牌桌中間, 多的立直棒會在副露的位置
     * */
    fun putRiichiStick(player: MahjongPlayerBase) {
        if (player !in game.seat) return
        val seatIndex = game.seat.indexOf(player)
        val tableCenterPos = game.tableCenterPos
        val halfWidthOfSixTiles = (MAHJONG_TILE_WIDTH * 6 / 2.0)
        val paddingFromCenter = //距離牌桌中心到點棒的距離 y
            halfWidthOfSixTiles - MAHJONG_POINT_STICK_DEPTH / 2.0
        val stickPos = when (seatIndex) { //這個是點棒的座標
            //這裡的方向就是以遊戲內的面朝東為準，按照麻將桌"逆"時針東南西北的方向
            0 -> tableCenterPos.add(paddingFromCenter, 0.0, 0.0) //東
            3 -> tableCenterPos.add(0.0, 0.0, paddingFromCenter) //南
            2 -> tableCenterPos.add(-paddingFromCenter, 0.0, 0.0) //西
            else -> tableCenterPos.add(0.0, 0.0, -paddingFromCenter) //北
        }
        val stickYaw = when (seatIndex) {
            0 -> -90f //東
            3 -> 0f //南
            2 -> 90f //西
            else -> 180f //北
        }
        player.sticks += MahjongScoringStickEntity(world = game.world).apply {
            code = ScoringStick.P1000.code //1000 點棒的編號
            gameBlockPos = game.pos
            isSpawnedByGame = true
            refreshPositionAfterTeleport(stickPos) //先移到位置上
            yaw = stickYaw //再轉個方向
            world.spawnEntity(this) //再生成到世界上
        }
    }

    /**
     * 將指定的 [stick] 傳送到積棒區的 [index] 位置上,
     * 積棒區在副露的位置
     * */
    private fun moveStickTo(player: MahjongPlayerBase, index: Int, stick: MahjongScoringStickEntity) {
        if (player !in game.seat) return
        val seatIndex = game.seat.indexOf(player)
        val tableCenterPos = game.tableCenterPos
        //記得實體的座標是以實體本身底部的正中央為主
        val halfTableLengthNoBorder = 0.5 + 15.0 / 16.0 //沒有包含邊框的半張桌子長度
        val halfWidthOfStick = MAHJONG_POINT_STICK_WIDTH / 2.0 //積棒長度的一半
        val halfDepthOfStick = MAHJONG_POINT_STICK_DEPTH / 2.0 //積棒深度的一半
        val startingPos = when (seatIndex) { //這個是玩家右手方的牌桌角落的第一根積棒的座標
            //這裡的方向就是以遊戲內的面朝東為準，按照麻將桌"逆"時針東南西北的方向
            0 -> tableCenterPos.add(
                halfTableLengthNoBorder - halfWidthOfStick,
                0.0,
                -halfTableLengthNoBorder + halfDepthOfStick
            ) //東
            3 -> tableCenterPos.add(
                halfTableLengthNoBorder - halfDepthOfStick,
                0.0,
                halfTableLengthNoBorder - halfWidthOfStick
            ) //南
            2 -> tableCenterPos.add(
                -halfTableLengthNoBorder + halfWidthOfStick,
                0.0,
                halfTableLengthNoBorder - halfDepthOfStick
            ) //西
            else -> tableCenterPos.add(
                -halfTableLengthNoBorder + halfDepthOfStick,
                0.0,
                -halfTableLengthNoBorder + halfWidthOfStick
            ) //北
        }
        val stickGap = MAHJONG_TILE_SMALL_PADDING
        val stickOffset = when (seatIndex) { //以牌桌邊緣為底, 垂直擺放的積棒的偏移量 (由右手往左手方向)
            0 -> Vec3d(0.0, 0.0, MAHJONG_POINT_STICK_DEPTH + stickGap)      //東
            3 -> Vec3d(-MAHJONG_POINT_STICK_DEPTH - stickGap, 0.0, 0.0)     //南
            2 -> Vec3d(0.0, 0.0, -MAHJONG_POINT_STICK_DEPTH - stickGap)     //西
            else -> Vec3d(MAHJONG_POINT_STICK_DEPTH + stickGap, 0.0, 0.0)   //北
        }
        val stackOffset = //擺放的積棒同一疊超過 STICKS_PER_STACK 就會往上疊
            Vec3d(0.0, MAHJONG_POINT_STICK_HEIGHT + stickGap, 0.0)
        val stickYaw = when (seatIndex) {
            0 -> -90f //東
            3 -> 0f //南
            2 -> 90f //西
            else -> 180f //北
        } - 90f //調整積棒的方向
        val stackIndex = (index / MahjongGame.STICKS_PER_STACK).toDouble() //這是第幾疊
        val stickIndex = (index % MahjongGame.STICKS_PER_STACK).toDouble() //這根積棒在這疊的第幾個位置
        val offsetXZ = stickOffset.multiply(stickIndex, stickIndex, stickIndex) // XZ平面上的偏移
        val offsetY = stackOffset.multiply(stackIndex, stackIndex, stackIndex)  // Y軸上的偏移
        val pos = startingPos.add(offsetXZ).add(offsetY)
        stick.yaw = stickYaw
        stick.refreshPositionAfterTeleport(pos)
    }

    /**
     * 將指定的 [stick] 放到積棒區的最後位置上
     * */
    private fun moveStickToLast(player: MahjongPlayerBase, stick: MahjongScoringStickEntity) =
        moveStickTo(player = player, stick = stick, index = player.sticks.lastIndex + 1)

    /**
     * 重新排列這個玩家的積棒
     * */
    fun resortSticks(player: MahjongPlayerBase) =
        player.sticks.sortedBy { it.code } //先按照外觀排列
            .forEachIndexed { index, stick ->
                moveStickTo( //再移動到對應位置
                    player = player,
                    index = index,
                    stick = stick
                )
            }

    /**
     * 放置這個玩家的場棒,
     * 只有流局的時候會呼叫這邊,
     * 這一根場棒會放在這個玩家的最後一根的積棒的旁邊
     * */
    fun addHonbaStick(player: MahjongPlayerBase) {
        if (player !in game.seat) return
        player.sticks += MahjongScoringStickEntity(world = game.world).apply {
            code = ScoringStick.P100.code
            gameBlockPos = game.pos
            isSpawnedByGame = true
            moveStickToLast(player = player, stick = this) //這時候場棒還沒生到世界上
            world.spawnEntity(this)
        }
    }

    /**
     * 移除這個玩家積棒中的所有場棒
     * 只有莊家下莊會呼叫
     * */
    fun removeHonbaSticks(player: MahjongPlayerBase) {
        if (player !in game.seat) return
        player.sticks.filter { it.scoringStick == ScoringStick.P100 }.forEach { //場棒是 100 點棒
            it.remove(Entity.RemovalReason.DISCARDED)
            player.sticks -= it
        }
        resortSticks(player) //移除完所有場棒, 重新排列一下剩下的積棒
    }
}