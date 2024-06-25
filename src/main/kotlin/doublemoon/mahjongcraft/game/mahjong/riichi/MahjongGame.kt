package doublemoon.mahjongcraft.game.mahjong.riichi

import doublemoon.mahjongcraft.MOD_ID
import doublemoon.mahjongcraft.block.MahjongStool
import doublemoon.mahjongcraft.blockentity.MahjongTableBlockEntity
import doublemoon.mahjongcraft.entity.*
import doublemoon.mahjongcraft.game.GameBase
import doublemoon.mahjongcraft.game.GameManager
import doublemoon.mahjongcraft.game.GameStatus
import doublemoon.mahjongcraft.game.mahjong.riichi.model.*
import doublemoon.mahjongcraft.game.mahjong.riichi.player.MahjongBot
import doublemoon.mahjongcraft.game.mahjong.riichi.player.MahjongPlayer
import doublemoon.mahjongcraft.game.mahjong.riichi.player.MahjongPlayerBase
import doublemoon.mahjongcraft.logger
import doublemoon.mahjongcraft.network.mahjong_game.MahjongGamePayload
import doublemoon.mahjongcraft.network.mahjong_table.MahjongTablePayloadListener
import doublemoon.mahjongcraft.network.sendPayloadToPlayer
import doublemoon.mahjongcraft.registry.SoundRegistry
import doublemoon.mahjongcraft.scheduler.client.ScoreSettleHandler
import doublemoon.mahjongcraft.scheduler.client.YakuSettleHandler
import doublemoon.mahjongcraft.scheduler.server.ServerScheduler
import doublemoon.mahjongcraft.util.delayOnServer
import doublemoon.mahjongcraft.util.plus
import doublemoon.mahjongcraft.util.sendTitles
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.entity.Entity
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import org.mahjong4j.PersonalSituation
import org.mahjong4j.hands.Kantsu
import kotlin.math.abs

/**
 * 日本麻將的主要實現都在這, 允許食斷
 *
 * @see <a href="https://github.com/mahjong4j/mahjong4j">使用 Mahjong4j</a>
 * */
class MahjongGame(
    override val world: ServerWorld,
    override val pos: BlockPos,
    var rule: MahjongRule = MahjongRule(),
) : GameBase<MahjongPlayerBase> {

    override val name = Text.translatable("$MOD_ID.game.riichi_mahjong")

    override var status = GameStatus.WAITING

    private val isPlaying: Boolean
        get() = status == GameStatus.PLAYING

    /**
     *  第 1 個玩家為 host,可以調整麻將的規則
     * */
    override val players = ArrayList<MahjongPlayerBase>(4)

    private val realPlayers: List<MahjongPlayer>
        get() = players.filterIsInstance<MahjongPlayer>()

    private val botPlayers: List<MahjongBot>
        get() = players.filterIsInstance<MahjongBot>()

    /**
     * 用來操作大部分麻將牌的功能
     * */
    private val board = MahjongBoard(this)

    /**
     * 桌子正中央的座標
     * */
    val tableCenterPos = Vec3d(pos.x + 0.5, pos.y + 1.0, pos.z + 0.5)

    /**
     * 座位會隨機從 players 編排, 順序是東南西北, 遊戲中的東與這個東是同個方向, 再來照麻將桌逆時針轉
     * */
    var seat = mutableListOf<MahjongPlayerBase>()

    /**
     * 從莊家開始按照順序的 [seat], seatOrderFromDealer[0] 就是莊家
     * */
    private val seatOrderFromDealer: List<MahjongPlayerBase>
        get() = List(4) {
            val dealerIndex = round.round
            seat[(dealerIndex + it) % 4]
        }

    /**
     * 目前的回合
     * */
    var round: MahjongRound = MahjongRound()

    /**
     * 處理遊戲的 [Job],
     * 幾乎所有的遊戲邏輯都在這裡處理
     * */
    private var jobRound: Job? = null

    /**
     * 等待遊戲開始的 [Job]
     * */
    private var jobWaitForStart: Job? = null

    /**
     * 當前骰子點數
     * */
    private var dicePoints = 0

    /**
     * 規則發生改變, 在等待中的已準備玩家, 除了 Bot 和 Host->全都取消準備, 並且發送訊息進行提示
     * */
    fun changeRules(rule: MahjongRule) {
        this.rule = rule
        players.forEachIndexed { index, mjPlayer ->
            if (mjPlayer is MahjongPlayer) {
                if (index == 0) {
                    mjPlayer.sendMessage(
                        text = PREFIX + Text.translatable("$MOD_ID.game.message.you_changed_the_rules")
                    )
                } else {
                    mjPlayer.ready = false
                    mjPlayer.sendMessage(
                        text = PREFIX + Text.translatable("$MOD_ID.game.message.host_changed_the_rules")
                    )
                }
            }
        }
    }

    /**
     * 加入機器人到遊戲
     * */
    fun addBot() {
        val bot = MahjongBot(world = world, pos = tableCenterPos, gamePos = pos)
        players += bot
    }

    /**
     * 讓玩家準備 or 取消準備
     * */
    fun readyOrNot(player: ServerPlayerEntity, ready: Boolean) {
        getPlayer(player)?.ready = ready
    }

    /**
     * 剔除 [players] 的 [index] 位置上的玩家
     * */
    fun kick(index: Int) {
        if (index in players.indices) {
            val player = players.removeAt(index)
            if (player is MahjongPlayer) { //是玩家
                player.sendMessage(PREFIX + Text.translatable("$MOD_ID.game.message.be_kick"))
            } else {  //是機器人, 清掉實體
                player.entity.remove(Entity.RemovalReason.DISCARDED)
            }
        }
    }

    /**
     * 玩家加入,
     * 不會重複加入
     * */
    override fun join(player: ServerPlayerEntity) {
        if (GameManager.isInAnyGame(player) || isInGame(player)) return
        players += MahjongPlayer(entity = player)
        if (isHost(player)) players[0].ready = true
    }

    /**
     * 玩家離開,
     * 目前只有離開伺服器或切換世界會自動離開 麻將遊戲,
     * "沒有限制"超出範圍會離開 麻將遊戲
     * */
    override fun leave(player: ServerPlayerEntity) {
        if (!GameManager.isInAnyGame(player) || !isInGame(player)) return
        if (isHost(player)) { //如果玩家是 host
            players.find { it is MahjongPlayer && it.entity != player }.apply {
                //找到第一個 不是機器人(即 MahjongPlayer) 也不是當前 host 的玩家, 讓他成為 host
                //否則全都是機器人, 清空玩家
                if (this != null) {
                    players.remove(this)
                    players.add(0, this)
                    this.ready = true
                } else {
                    botPlayers.forEach { it.entity.remove(Entity.RemovalReason.DISCARDED) } //清掉機器人實體
                    players.clear()
                }
            }
        }
        players.removeIf { it.entity == player }
    }

    /**
     * 清除或重置大部分東西用,
     * 包括: [board] 之中的牌, 玩家立直狀態 and 手牌 etc.
     *
     * @param clearRiichiSticks 是否要清掉玩家的立直棒
     * */
    private fun clearStuffs(clearRiichiSticks: Boolean = true) {
        players.forEach { //清理玩家的狀態
            if (clearRiichiSticks) { //如果要清理立直棒
                it.sticks.filter { stick -> stick.scoringStick == ScoringStick.P1000 }
                    .forEach { stick ->
                        stick.remove(Entity.RemovalReason.DISCARDED)
                        it.sticks -= stick
                    }
            }
            it.riichi = false
            it.doubleRiichi = false
            it.hands.clear()
            it.fuuroList.clear()
            it.discardedTiles.clear()
            it.discardedTilesForDisplay.clear()
            it.riichiSengenTile = null
        }
        dicePoints = 0
        board.clear()
    }

    /**
     * 向遊戲中的玩家以標題的方式顯示目前的回合數
     * */
    private fun showRoundsTitle() {
        val windText = round.wind.toText()
        val countersText = Text.translatable("$MOD_ID.game.repeat_counter", round.honba).formatted(Formatting.YELLOW)
        realPlayers.map { it.entity }.sendTitles(
            title = Text.translatable("$MOD_ID.game.round.title", windText, round.round + 1)
                .formatted(Formatting.GOLD)
                .formatted(Formatting.BOLD),
            subtitle = Text.literal("§c - ") + countersText + "§c - "
        )
    }

    /**
     * 開始目前的 [round],
     * 請在主線程上調用
     * 向客戶端發送一些訊息 (像是聽的牌, 振聽, 或者哪幾張牌剩幾張)
     * @param clearRiichiSticks 是否要清掉立直棒
     * */
    private fun startRound(clearRiichiSticks: Boolean = true) {
        if (status != GameStatus.PLAYING) return
        val handler = CoroutineExceptionHandler { _, exception -> //目前遊戲過程中的例外, 異常情況都暫時先無視
            logger.warn("Something happened, I hope you can report it.", exception)
        }
        jobRound?.cancel()
        jobRound = CoroutineScope(Dispatchers.IO).launch(handler) {
            clearStuffs(clearRiichiSticks = clearRiichiSticks)
            showRoundsTitle()
            syncMahjongTable() //每個 Round 開始時同步
            board.generateAllTilesAndSpawnWall() //產生所有牌

            val dealer = seatOrderFromDealer[0] //這回合的莊家
            var dealerRemaining = false //判斷連莊用
            var clearNextRoundRiichiSticks = true //判斷下回合要不要清掉立直棒用 (四家立直會保留立直棒)
            var roundExhaustiveDraw: ExhaustiveDraw? = null //判斷結束遊戲的原因用
            delayOnServer(0) //等待 1 tick 後才整理積棒, 否則可能積棒還沒清就整理了, 導致整理的位置不對
            players.forEach { board.resortSticks(it) }
            delayOnServer(1000)

            val dices = rollDice()
            val openDoorPlayerSeatIndexFromDealer = ((dicePoints - 1) % 4 + round.round) % 4
            val openDoorPlayer = seatOrderFromDealer[openDoorPlayerSeatIndexFromDealer] //開門牌所在位置的玩家
            board.assignWallAndHands(dicePoints = dicePoints)
            board.assignDeadWall()
            delayOnServer(500)

            //清除擲出的骰子
            dices.forEach { it.remove(Entity.RemovalReason.DISCARDED) }
            delayOnServer(1000)

            var nextPlayer: MahjongPlayerBase = dealer //莊家開始打牌
            var drawTile = true //這次動作需不需要拿牌
            var drewTile: Boolean //這次動作已經拿牌了嗎
            val cannotDiscardTiles = mutableListOf<MahjongTile>() //不能丟的牌的 mahjong4j 編號列表 出現在吃或碰的時候, 不能丟剛吃或剛碰的牌, 每次丟牌後清空

            // 這圈 while 最後要判斷 [wall] 還有沒有牌, 否則拿最後一張牌的後一張牌的時候會出問題
            roundLoop@ while (isPlaying) {
                // 開始打牌的動作
                val player = nextPlayer //當前執行動作的玩家, (正常是從莊家按順序開始)
                val seatIndex = seat.indexOf(player)
                val isDealer = dealer == player //是否是莊家
                var timeoutTile = player.hands.last() //超時操作預設丟棄的牌 (預設為手牌最後一張)

                if (drawTile) { // 這次動作需要拿牌
                    val lastTile =
                        if (isDealer && player.discardedTiles.size == 0) player.hands.last() //如果是莊家第一輪->取手牌最後一張牌
                        else board.wall
                            .removeFirst() //如果不是莊家第一輪->從牌山拿第一張牌,並加入手牌
                            .also {
                                player.drawTile(it)
                                board.sortHands(player = player, lastTile = it) //指定最後一張牌, 否則摸的牌會被整理進去
                            }
                    drewTile = true //已經拿過牌了
                    if (player is MahjongBot) delayOnServer(500) //如果這是機器人, 摸牌後會等待 500 毫秒再執行動作

                    //判斷能不能自摸
                    val canWin = player.canWin(
                        winningTile = lastTile.mahjongTile,
                        isWinningTileInHands = true,
                        rule = rule,
                        generalSituation = board.generalSituation,
                        personalSituation = player.getPersonalSituation(isTsumo = true)
                    )
                    if (canWin && player.askToTsumo()) {
                        player.tsumo(tile = lastTile)  //自摸, 直接結束當前的 round
                        if (isDealer) dealerRemaining = true //莊家自摸的話會連莊
                        break@roundLoop
                    } else { //不是自摸, 判斷暗槓
                        //第一巡有九種九牌可以和局
                        if (player.isKyuushuKyuuhai() && player.askToKyuushuKyuuhai()) {
                            //詢問是否要九種九牌和局
                            roundExhaustiveDraw = ExhaustiveDraw.KYUUSHU_KYUUHAI
                            break@roundLoop
                        }
                        //加槓跟暗槓應該同時詢問
                        //玩家可以選擇一直暗槓或加槓, 使用 while 判斷
                        var finalRinshanTile: MahjongTileEntity? = null //最後一張摸的嶺上牌, null 表示沒有暗槓或加槓
                        kanLoop@ while ((player.canKakan || player.canAnkan) && !board.isHoutei && board.kanCount < 4) {
                            val tileToAnkanOrKakan = player.askToAnkanOrKakan(rule)
                            if (tileToAnkanOrKakan != null) { //玩家要暗槓或加槓
                                val isAnkan = tileToAnkanOrKakan in player.tilesCanAnkan.toList()
                                    .toMahjongTileList() //是要暗槓,否則就是加槓
                                val tileEntityToAnkanOrKakan =
                                    player.hands.find { it.mahjongTile == tileToAnkanOrKakan } //要暗槓或加槓的牌
                                if (tileEntityToAnkanOrKakan == null) {
                                    cancel("要暗槓的牌的實體消失, 可能是麻將桌被摧毀")
                                    return@launch
                                }
                                if (isAnkan) player.ankan(tileEntityToAnkanOrKakan) {
                                    it.playSoundAtSeat(soundEvent = SoundRegistry.kan)
                                } else player.kakan(tileEntityToAnkanOrKakan) {
                                    it.playSoundAtSeat(soundEvent = SoundRegistry.kan)
                                }
                                board.sortFuuro(player = player)
                                val canChankanList = //可以搶槓的玩家列表
                                    if (isAnkan) canChanAnkanList(tileEntityToAnkanOrKakan, player)
                                    else canChanKakanList(tileEntityToAnkanOrKakan, player)
                                if (canChankanList.isNotEmpty()) { //有人可以搶槓
                                    if (canChankanList.size > 1) { //超過 1 人可以榮和,會強制所有可以榮和的玩家都榮和
                                        canChankanList.ron(
                                            target = player,
                                            isChankan = true,
                                            tile = tileEntityToAnkanOrKakan
                                        )
                                        if (dealer in canChankanList) dealerRemaining = true //搶暗槓的人之中有莊家
                                        break@roundLoop
                                    } else { //只有 1 人可以搶暗槓
                                        val canChanKanPlayer = canChankanList[0]
                                        if (canChanKanPlayer.askToRon(
                                                tileEntityToAnkanOrKakan,
                                                canChanKanPlayer.asClaimTarget(player)
                                            )
                                        ) { //詢問搶暗槓是否要榮和
                                            mutableListOf(canChanKanPlayer).ron(
                                                target = player,
                                                isChankan = true,
                                                tile = tileEntityToAnkanOrKakan
                                            )
                                            if (dealer == canChanKanPlayer) dealerRemaining = true
                                            break@roundLoop
                                        }
                                    }
                                }
                                val rinshanTile = board.drawRinshanTile(player = player) //摸嶺上牌
                                board.sortHands(player = player, lastTile = rinshanTile) //整理並指定最後一張牌為摸到的嶺上牌
                                //判斷嶺上開花
                                val rinshanKaihoh = player.canWin(
                                    winningTile = rinshanTile.mahjongTile,
                                    isWinningTileInHands = true,
                                    rule = rule,
                                    generalSituation = board.generalSituation,
                                    personalSituation = player.getPersonalSituation(
                                        isTsumo = true,
                                        isRinshanKaihoh = true
                                    )
                                )
                                if (rinshanKaihoh) { //嶺上開花成立
                                    player.tsumo(isRinshanKaihoh = true, tile = rinshanTile)
                                    if (isDealer) dealerRemaining = true //莊家自摸的話會連莊
                                    break@roundLoop
                                }
                                val isFourKanAbort = //最後才判斷 四開槓
                                    if (board.kanCount == 3) { //已經槓 3 次
                                        val playerKanCount =
                                            player.fuuroList.count { it.mentsu is Kantsu } //槓的這個玩家槓幾次
                                        playerKanCount != 3 //如果不是同一個玩家已經槓了 3 次,準備槓第 4 次->四開槓成立
                                    } else false
                                if (isFourKanAbort) { //四開槓, 直接結束
                                    roundExhaustiveDraw = ExhaustiveDraw.SUUKAIKAN
                                    break@roundLoop
                                }
                                finalRinshanTile = rinshanTile
                            } else {
                                //玩家不要暗槓或加槓
                                break@kanLoop
                            }
                        }
                        //不能或結束暗槓或加槓->讓玩家出牌
                        timeoutTile = finalRinshanTile ?: lastTile
                    }
                } else {  // 這次動作不需要拿牌
                    drewTile = false //這次沒有拿牌
                    drawTile = true //下次要拿牌
                }

                // 丟牌前判斷玩家要不要立直宣言
                val riichiSengen =
                    if (!board.isHoutei && player.isRiichiable) { //不是 河底 且 能夠立直
                        player.askToRiichi() //詢問是否要立直宣言, 並選擇要丟出去的牌
                    } else null // 沒有要立直

                // 限制立直時候只能丟摸到的牌或者暗槓後的嶺上牌
                if (player.riichi || player.doubleRiichi) {
                    cannotDiscardTiles += player.hands.toMahjongTileList()
                    cannotDiscardTiles.removeAll { it == timeoutTile.mahjongTile }
                }

                // 讓玩家丟牌
                val tileToDiscard = //玩家丟掉的牌的編號
                    riichiSengen ?: player.askToDiscardTile(
                        timeoutTile = timeoutTile.mahjongTile,
                        cannotDiscardTiles = cannotDiscardTiles,
                        skippable = drewTile //這次有沒有拿牌代表客戶端能不能 跳過(即自動摸切)
                    )
                val tileDiscarded = player.discardTile(tileToDiscard) //玩家丟掉的牌
                if (tileDiscarded == null) {
                    cancel("要丟的牌的實體消失, 可能是麻將桌被摧毀") //實體不正常
                    return@launch
                }
                board.discards += tileDiscarded
                board.sortDiscardedTilesForDisplay(player = player, openDoorPlayer = openDoorPlayer) //整理顯示用丟牌堆
                board.sortHands(player = player)  //整理手牌
                cannotDiscardTiles.clear()

                // 判斷 四風連打
                if (board.isSuufonRenda) {
                    roundExhaustiveDraw = ExhaustiveDraw.SUUFON_RENDA
                    break
                }

                // 判斷榮和
                val canRonList = canRonList(tile = tileDiscarded, player)
                if (canRonList.isNotEmpty()) { //有人可以榮和
                    if (canRonList.size > 1) { //超過 1 人可以榮和,會強制所有可以榮和的玩家都榮和
                        canRonList.ron(target = player, tile = tileDiscarded)
                        if (dealer in canRonList) dealerRemaining = true //榮和的人之中有莊家
                        break@roundLoop
                    } else { //只有 1 人可以榮和
                        val canRonPlayer = canRonList[0]
                        if (canRonPlayer.askToRon(tileDiscarded, canRonPlayer.asClaimTarget(player))) { //詢問是否要榮和
                            mutableListOf(canRonPlayer).ron(target = player, tile = tileDiscarded)
                            if (dealer == canRonPlayer) dealerRemaining = true
                            break@roundLoop
                        }
                    }
                }

                // 立直宣言要在沒有被榮和的情況下才成立
                if (riichiSengen != null) {
                    // 立直棒的點數會在流局或者有人贏牌的時候結算減去
                    player.riichi(riichiSengenTile = tileDiscarded, isFirstRound = board.isFirstRound)
                    board.sortDiscardedTilesForDisplay(player = player, openDoorPlayer = openDoorPlayer)
                    board.putRiichiStick(player = player)
                    player.playSoundAtSeat(SoundRegistry.riichi)
                    //TODO 播放立直動畫 (需要再放?
                    if (players.count { it.riichi || it.doubleRiichi } == 4) { //四家立直
                        roundExhaustiveDraw = ExhaustiveDraw.SUUCHA_RIICHI
                        break
                    }
                }

                // 能明槓或碰的玩家列表，有 4 槓以後, 這個列表不會有玩家 (即最多 4 槓且必須是 4 槓子的情況才行)
                val canMinKanOrPonList = canMinKanOrPonList(
                    tile = tileDiscarded,
                    seatIndex = seatIndex,
                    discardedPlayer = player
                )

                // 執行明槓或碰兩種情況的部分
                var someoneKanOrPon = false
                if (canMinKanOrPonList.isNotEmpty()) { //有人可以明槓 (明槓一定只有一個人能明槓, 而且可以進行明槓的人除了上家以外一定能碰)
                    val canMinKanOrPonPlayer = canMinKanOrPonList[0] // 明槓一定只有一個人能明槓
                    val seatIndexOfCanMinkanOrPonPlayer = seat.indexOf(canMinKanOrPonPlayer)
                    val claimTarget = when (abs(seatIndexOfCanMinkanOrPonPlayer - seatIndex)) { // 取兩個座位的距離絕對值
                        //槓或碰的玩家相對丟牌的玩家
                        1 -> ClaimTarget.RIGHT
                        2 -> ClaimTarget.ACROSS
                        else -> ClaimTarget.LEFT // 明槓的時候這情況不可能出現 (不能槓上家), 一出現就是表示錯誤
                    }

                    // 詢問玩家要不要明槓, 明槓跟碰一起詢問, 故丟牌的玩家的下家不會被這裡問到, 因為他不能明槓上家
                    someoneKanOrPon = when (canMinKanOrPonPlayer.askToMinkanOrPon(
                        tileDiscarded,
                        canMinKanOrPonPlayer.asClaimTarget(player),
                        rule
                    )) {
                        // 玩家要碰
                        MahjongGameBehavior.PON -> {
                            canMinKanOrPonPlayer.pon(tileDiscarded, claimTarget, player) {
                                it.playSoundAtSeat(soundEvent = SoundRegistry.pon)
                            }
                            board.sortFuuro(player = canMinKanOrPonPlayer)
                            nextPlayer = canMinKanOrPonPlayer
                            drawTile = false
                            cannotDiscardTiles += tileDiscarded.mahjongTile
                            true
                        }
                        // 玩家要明槓
                        MahjongGameBehavior.MINKAN -> {
                            canMinKanOrPonPlayer.minkan(tileDiscarded, claimTarget, player) {
                                it.playSoundAtSeat(soundEvent = SoundRegistry.kan)
                            }
                            board.sortFuuro(player = canMinKanOrPonPlayer) //整理副露顯示
                            val rinshanTile = board.drawRinshanTile(player = canMinKanOrPonPlayer) //摸嶺上牌

                            //整理並指定最後一張牌為摸到的嶺上牌
                            board.sortHands(player = canMinKanOrPonPlayer, lastTile = rinshanTile)

                            //判斷嶺上開花
                            val rinshanKaiHoh = canMinKanOrPonPlayer.canWin(
                                winningTile = rinshanTile.mahjongTile,
                                isWinningTileInHands = true,
                                rule = rule,
                                generalSituation = board.generalSituation,
                                personalSituation = canMinKanOrPonPlayer.getPersonalSituation(
                                    isTsumo = true,
                                    isRinshanKaihoh = true
                                )
                            )

                            // 嶺上開花成立
                            if (rinshanKaiHoh) {
                                player.tsumo(isRinshanKaihoh = true, tile = rinshanTile)
                                if (canMinKanOrPonPlayer == dealer) dealerRemaining = true //莊家自摸的話會連莊
                                break@roundLoop
                            }

                            val isFourKanAbort = //最後才判斷 四開槓
                                if (board.kanCount == 3) { //已經槓 3 次
                                    val playerKanCount =
                                        canMinKanOrPonPlayer.fuuroList.count { it.mentsu is Kantsu } //槓的這個玩家槓幾次
                                    playerKanCount != 3 //如果是同一個玩家已經槓了 3 次,準備槓第 4 次->四開槓成立
                                } else false
                            if (isFourKanAbort) { //四開槓, 直接結束
                                roundExhaustiveDraw = ExhaustiveDraw.SUUKAIKAN
                                break@roundLoop
                            }
                            nextPlayer = canMinKanOrPonPlayer
                            drawTile = false
                            true
                        }
                        // 玩家選擇 pass
                        else -> false
                    }
                }

                // 能碰的玩家列表
                val canPonList = canPonList(tile = tileDiscarded, discardedPlayer = player)
                    .toMutableList()
                    .also { it -= canMinKanOrPonList.toSet() }  // 去除掉與 canMinKanOrPonList 重複的玩家

                // 能吃的玩家列表
                val canChiiList = canChiiList(tile = tileDiscarded, seatIndex = seatIndex, discardedPlayer = player)
                    .toMutableList()

                // 執行碰或吃兩種情況的部分
                var someonePonOrChii = false
                if (!someoneKanOrPon && canPonList.isNotEmpty()) {
                    var ponOrChiiResult = false

                    //從丟牌玩家開始照順序問要不要碰
                    repeat(4) {
                        val seatIndexOfPonOrChiiPlayer = (seatIndex + it) % 4
                        val ponOrChiiPlayer = seat[seatIndexOfPonOrChiiPlayer]
                        if (ponOrChiiPlayer in canPonList) {
                            if (ponOrChiiPlayer in canChiiList) { //如果 nPlayer 同時出現可以吃的情況
                                val tilePairToPonOrChii =
                                    ponOrChiiPlayer.askToPonOrChii(
                                        tileDiscarded,
                                        ponOrChiiPlayer.asClaimTarget(player)
                                    )
                                if (tilePairToPonOrChii != null) { //玩家有要碰或吃
                                    if (tilePairToPonOrChii.first == tilePairToPonOrChii.second) { //玩家選擇碰
                                        ponOrChiiPlayer.pon(
                                            tileDiscarded,
                                            ClaimTarget.LEFT, //碰或吃同時出現, target 必定為上家
                                            player
                                        ) { here ->
                                            here.playSoundAtSeat(soundEvent = SoundRegistry.pon)
                                        }
                                        cannotDiscardTiles += tileDiscarded.mahjongTile
                                    } else { //玩家選擇吃
                                        ponOrChiiPlayer.chii(
                                            tileDiscarded,
                                            tilePairToPonOrChii,
                                            player
                                        ) { here ->
                                            here.playSoundAtSeat(soundEvent = SoundRegistry.chii)
                                        }
                                        val tileDiscardedCode = tileDiscarded.mahjong4jTile.code
                                        val tileDiscardedNumber = tileDiscarded.mahjong4jTile.number
                                        val tileCodeList = mutableListOf(
                                            tileDiscardedCode,
                                            tilePairToPonOrChii.first.mahjong4jTile.code,
                                            tilePairToPonOrChii.second.mahjong4jTile.code
                                        ).also { list -> list.sort() } //正序排列, 由小到大
                                        val indexOfTileDiscarded = tileCodeList.indexOf(tileDiscardedCode)
                                        if (indexOfTileDiscarded == 0 && tileDiscardedNumber + 3 < 9) //吃的這張牌在最前面
                                            cannotDiscardTiles += tileDiscarded.mahjongTile.nextTile.nextTile.nextTile
                                        if (indexOfTileDiscarded == 2 && tileDiscardedNumber - 3 > 1) //吃的這張牌在最後面
                                            cannotDiscardTiles += tileDiscarded.mahjongTile.previousTile.previousTile.previousTile
                                    }
                                    board.sortFuuro(player = ponOrChiiPlayer)
                                    nextPlayer = ponOrChiiPlayer
                                    drawTile = false
                                    ponOrChiiResult = true
                                    return@repeat
                                } else { //玩家沒有要碰或吃
                                    canChiiList -= ponOrChiiPlayer //避免重複詢問吃的動作
                                }
                            } else { //正常情況
                                val claimTarget = when (seatIndex) {
                                    (seatIndexOfPonOrChiiPlayer + 1) % 4 -> ClaimTarget.RIGHT
                                    (seatIndexOfPonOrChiiPlayer + 2) % 4 -> ClaimTarget.ACROSS
                                    (seatIndexOfPonOrChiiPlayer + 3) % 4 -> ClaimTarget.LEFT
                                    else -> ClaimTarget.SELF //不可能出現的情況, 碰自己的牌
                                }
                                if (ponOrChiiPlayer.askToPon(tileDiscarded, claimTarget)) { //如果問到的玩家要碰
                                    ponOrChiiPlayer.pon(tileDiscarded, claimTarget, player) { here ->
                                        here.playSoundAtSeat(soundEvent = SoundRegistry.pon)
                                    }
                                    board.sortFuuro(player = ponOrChiiPlayer)
                                    nextPlayer = ponOrChiiPlayer
                                    drawTile = false
                                    cannotDiscardTiles += tileDiscarded.mahjongTile
                                    ponOrChiiResult = true
                                    return@repeat
                                }
                            }
                        }
                    }
                    someonePonOrChii = ponOrChiiResult
                }

                // 執行吃的部分
                var someoneChii = false
                if (!someoneKanOrPon && !someonePonOrChii && canChiiList.isNotEmpty()) {
                    var chiiResult = false
                    val canChiiPlayer = canChiiList[0] //只有一個玩家能吃
                    val askToChiiResult =
                        canChiiPlayer.askToChii(tileDiscarded, canChiiPlayer.asClaimTarget(player))
                    if (askToChiiResult != null) {
                        val tileDiscardedCode = tileDiscarded.mahjong4jTile.code
                        val tileDiscardedNumber = tileDiscarded.mahjong4jTile.number
                        val tileCodeList = mutableListOf(
                            tileDiscardedCode,
                            askToChiiResult.first.mahjong4jTile.code,
                            askToChiiResult.second.mahjong4jTile.code
                        ).also { it.sort() } //正序排列, 由小到大
                        val indexOfTileDiscarded = tileCodeList.indexOf(tileDiscardedCode)
                        canChiiPlayer.chii(tileDiscarded, askToChiiResult, player) {
                            it.playSoundAtSeat(soundEvent = SoundRegistry.chii)
                        }
                        board.sortFuuro(player = canChiiPlayer)
                        nextPlayer = canChiiPlayer
                        drawTile = false
                        cannotDiscardTiles += tileDiscarded.mahjongTile
                        if (indexOfTileDiscarded == 0 && tileDiscardedNumber + 3 < 9) //吃的這張牌在最前面
                            cannotDiscardTiles += tileDiscarded.mahjongTile.nextTile.nextTile.nextTile
                        if (indexOfTileDiscarded == 2 && tileDiscardedNumber - 3 > 1) //吃的這張牌在最後面
                            cannotDiscardTiles += tileDiscarded.mahjongTile.previousTile.previousTile.previousTile
                        chiiResult = true
                    }
                    someoneChii = chiiResult
                }

                // 在沒有人可以吃碰槓的情況下, 會給予一點延遲, 讓每次玩家丟牌後都有一點點延遲, 看起來比較自然
                if (canMinKanOrPonList.isEmpty() && canPonList.isEmpty() && canChiiList.isEmpty()) {
                    delayOnServer(MIN_WAITING_TIME) //這個延遲待調整
                }

                // 沒有任何人進行 吃 碰 槓
                if (!someoneKanOrPon && !someonePonOrChii && !someoneChii) {
                    nextPlayer = seat[(seatIndex + 1) % 4] //輪到下家
                }

                // 如果牌山沒牌的話會直接結束這 Round
                if (board.wall.size == 0) {
                    roundExhaustiveDraw = ExhaustiveDraw.NORMAL
                    break@roundLoop
                }
            }

            // 如果是流局的話
            if (roundExhaustiveDraw != null) {
                dealerRemaining = if (roundExhaustiveDraw == ExhaustiveDraw.NORMAL) { //正常流局
                    val nagashiPlayers = canNagashiManganList()
                    //有人流局滿貫->直接讓可以流局滿貫的玩家直接流局滿貫, 並按照流局結束這局 (意思是莊家有聽牌才連莊)
                    if (nagashiPlayers.isNotEmpty()) nagashiPlayers.nagashiMangan()
                    dealer.isTenpai //莊家聽牌才會連莊
                } else { //特殊流局
                    true //特殊流局莊家直接連莊
                }
                clearNextRoundRiichiSticks = false //只要流局就不會回收立直棒
                roundDraw(roundExhaustiveDraw)
            }

            //結束後等待一下
            delayOnServer(3000L)

            //準備進行下一回合
            if (!round.isAllLast(rule)) { //不是 AllLast
                if (dealerRemaining) {//有連莊就本場 + 1
                    board.addHonbaStick(player = dealer)
                    round.honba++
                } else { //沒連莊就換莊家
                    board.removeHonbaSticks(player = dealer)
                    round.nextRound()
                }
                startRound(clearRiichiSticks = clearNextRoundRiichiSticks)
            } else {  //是 AllLast
                //AllLast 要考慮連莊,和第一點數不足 南/西入的問題
                if (players.none { it.points >= rule.minPointsToWin }) {
                    //沒有任何玩家的點數大於 1 位必要點數->繼續遊戲直到有人大於 1 位必要點數,玩家可以連莊
                    if (dealerRemaining) { //莊家有連莊
                        board.addHonbaStick(player = dealer)
                        round.honba++
                        startRound(clearRiichiSticks = clearNextRoundRiichiSticks)
                    } else { //莊家沒連莊
                        //最多 AllLast 只能持續一個風
                        board.removeHonbaSticks(player = dealer)
                        val finalRound = rule.length.finalRound
                        if (round.wind == finalRound.first && round.round == finalRound.second) {
                            //是最後一局->結束遊戲
                            showGameResult()
                            end()
                        } else {
                            //不是最後一局->繼續下一場
                            round.nextRound()
                            startRound(clearRiichiSticks = clearNextRoundRiichiSticks)
                        }
                    }
                } else {
                    //至少一個玩家的點數大於 1 位必要點數->結束遊戲
                    showGameResult()
                    end()
                }
            }
        }
    }

    /**
     * 這局的結果,
     * 只會對 [ExhaustiveDraw] 進行處理, 會考慮 當前的立直狀態 加減分數
     * @param draw 這局結束的原因
     * */
    private suspend fun roundDraw(draw: ExhaustiveDraw) {
        //計算流局情況下的分數變化, 並處理成列表
        val scoreList = buildList { //計算分數列表
            if (draw != ExhaustiveDraw.NORMAL) { //不是正常流局->存入原本的分數
                players.forEach {
                    val riichiStickPoints = if (it.riichi || it.doubleRiichi) ScoringStick.P1000.point else 0
                    this += ScoreItem(
                        mahjongPlayer = it,
                        scoreOrigin = it.points,
                        scoreChange = -riichiStickPoints
                    )
                    it.points -= riichiStickPoints
                    if (draw == ExhaustiveDraw.KYUUSHU_KYUUHAI && it.isKyuushuKyuuhai()) { //如果是九種九牌才要翻出手牌
                        it.openHands()
                    } else it.closeHands()
                }
            } else { //是正常流局
                val tenpaiCount = players.count { it.isTenpai } //聽牌有幾人
                if (tenpaiCount == 0) { //沒有人聽->點數變動 = 0
                    players.forEach {
                        this += ScoreItem(
                            mahjongPlayer = it,
                            scoreOrigin = it.points,
                            scoreChange = 0
                        )
                        it.closeHands()
                    }
                } else { //有人聽->計算不聽罰符, 不聽者向聽牌者支付合計 3000 點
                    val notenCount = 4 - tenpaiCount //計算沒聽的有幾人
                    val notenBappu = 3000 / notenCount //不聽罰符
                    val bappuGet = 3000 / tenpaiCount //得到的罰符
                    players.forEach {
                        if (it.isTenpai) { //這個玩家有聽牌
                            val riichiStickPoints =
                                if (it.riichi || it.doubleRiichi) ScoringStick.P1000.point else 0 //這個玩家這輪有沒有立直,有->還要減去立直棒的點數
                            this += ScoreItem(
                                mahjongPlayer = it,
                                scoreOrigin = it.points,
                                scoreChange = bappuGet - riichiStickPoints
                            )
                            it.points += bappuGet
                            it.points -= riichiStickPoints
                            it.openHands()
                        } else { //這個玩家沒有聽牌
                            this += ScoreItem(
                                mahjongPlayer = it,
                                scoreOrigin = it.points,
                                scoreChange = -notenBappu
                            )
                            it.points -= notenBappu
                            it.closeHands()
                        }
                    }
                }
            }
        }
        delayOnServer(3000) //玩家倒牌後小延遲一下, 再發送和局, 看起來比較自然
        realPlayers.forEach {  //寄送結算數據包以及分數列表
            sendPayloadToPlayer(
                player = it.entity,
                payload = MahjongGamePayload(
                    behavior = MahjongGameBehavior.SCORE_SETTLEMENT,
                    extraData = Json.encodeToString(
                        ScoreSettlement(
                            titleTranslateKey = draw.translateKey,
                            scoreList = scoreList
                        )
                    )
                )
            )
        }
        delayOnServer(ScoreSettleHandler.defaultTime * 1000L) //基本最少會等待跟結算畫面一樣長的秒數
    }

    /**
     * 顯示遊戲結果
     * */
    private fun showGameResult() {
        val scoreList = players.map {
            ScoreItem(
                mahjongPlayer = it,
                scoreOrigin = it.points,
                scoreChange = 0
            )
        }

        realPlayers.forEach {
            // 寄送結算數據包以及分數列表
            sendPayloadToPlayer(
                player = it.entity,
                payload = MahjongGamePayload(
                    behavior = MahjongGameBehavior.SCORE_SETTLEMENT,
                    extraData = Json.encodeToString(
                        ScoreSettlement(
                            titleTranslateKey = "$MOD_ID.game.game_over",
                            scoreList = scoreList
                        )
                    )
                )
            )

            // 會多傳一份給玩家的遊戲結算文字訊息
            val mahjongText = (Text.translatable("$MOD_ID.game.riichi_mahjong") + ":")
                .formatted(Formatting.YELLOW)
                .formatted(Formatting.BOLD)
//            val tooltipRuleString = rule.toTexts().joinToString(separator = "\n") { text -> text.string } //這方法會讓 text.string 沒有包含色碼, 所以不適用
            val ruleTooltip = Text.literal("").also {
                rule.toTexts().forEachIndexed { index, text ->
                    if (index > 0) it.append("\n")
                    it.append(text)
                }
            }
            val ruleHoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, ruleTooltip)
            val ruleStyle = Style.EMPTY.withColor(Formatting.GREEN).withHoverEvent(ruleHoverEvent)
            val ruleText = (Text.literal("§a[") + Text.translatable("$MOD_ID.game.rules") + "§a]").fillStyle(ruleStyle)
            val scoreText = Text.translatable("$MOD_ID.game.score")
            it.sendMessage(
                Text.literal("§2------------------------------------------")
                        + "\n" + mahjongText
                        + "\n§7 - " + ruleText
                        + "\n§a" // 空一行
                        + "\n§6" + scoreText + ":"
            )
            players.sortedByDescending { player -> player.points }.forEachIndexed { index, mjPlayer ->
                val displayNameText = if (mjPlayer.isRealPlayer) {
                    Text.literal(mjPlayer.displayName)
                } else {
                    Text.translatable("entity.$MOD_ID.mahjong_bot")
                }.formatted(Formatting.AQUA)
                it.sendMessage(Text.literal("§7 - §e${index + 1}. ") + displayNameText + "  §c${mjPlayer.points}")
            }
            it.sendMessage(Text.of("§2------------------------------------------"))
        }
    }

    /**
     * 在玩家的座位上播放聲音,
     * (因為玩家有可能離開座位我才這樣寫,其實好像也沒必要(?))
     * */
    private fun MahjongPlayerBase.playSoundAtSeat(
        soundEvent: SoundEvent,
        category: SoundCategory = SoundCategory.PLAYERS,
        volume: Float = 1f,
        pitch: Float = 1f,
    ) {
        val seatIndex = seat.indexOf(this)
        val x = pos.x + 0.5 + if (seatIndex == 0) 2 else if (seatIndex == 2) -2 else 0
        val y = pos.y.toDouble()
        val z = pos.z + 0.5 + if (seatIndex == 1) -2 else if (seatIndex == 3) 2 else 0
        world.playSound(
            null,
            x,
            y,
            z,
            soundEvent,
            category,
            volume,
            pitch
        )
    }

    /**
     * 玩家是否滿足九種九牌的條件,
     * 第一巡 且 么九牌種類 >= 9
     * */
    private fun MahjongPlayerBase.isKyuushuKyuuhai(): Boolean = board.isFirstRound && numbersOfYaochuuhaiTypes >= 9

    /**
     * 以 [this] 為基礎, 按 [target] 相對的位置返回 鳴牌的對象
     * */
    private fun MahjongPlayerBase.asClaimTarget(target: MahjongPlayerBase): ClaimTarget {
        val seatIndex = seat.indexOf(this)
        return when (target) {
            seat[(seatIndex + 1) % 4] -> ClaimTarget.RIGHT
            seat[(seatIndex + 2) % 4] -> ClaimTarget.ACROSS
            seat[(seatIndex + 3) % 4] -> ClaimTarget.LEFT
            else -> ClaimTarget.SELF //除非 target 是自己, 否則不可能發生這個條件
        }
    }

    /**
     * 根據 [tile] 判斷有沒有人可以 碰,
     * 任何位置的玩家都可以碰
     * TODO 目前有不確定的情況->ex: 自家有 2 張東, 下家打了 1 張你沒碰, 但是對面打了 1 張你突然想碰, 這樣能碰嗎? (就是 過水碰 的狀況)
     *
     * @return 可以碰的玩家的列表
     * */
    private fun canPonList(
        tile: MahjongTileEntity,
        discardedPlayer: MahjongPlayerBase,
    ): List<MahjongPlayerBase> =
        if (!board.isHoutei) players.filter { it != discardedPlayer && it.canPon(tile) }
        else emptyList()

    /**
     * 根據 [tile] 判斷有沒有人可以 明槓,
     * 除了丟出 [tile] 的玩家的下家以外都可以明槓 (除非已經有 4 槓了)
     *
     *
     * @param seatIndex 丟出 [tile] 的玩家的座位編號
     * @return 可以明槓的玩家的列表
     * */
    private fun canMinKanOrPonList(
        tile: MahjongTileEntity,
        discardedPlayer: MahjongPlayerBase,
        seatIndex: Int = seat.indexOf(discardedPlayer),
    ): List<MahjongPlayerBase> =
        if (board.kanCount < 4 && !board.isHoutei) // 4 槓前或者不是河底都可以明槓
            players.filter { it != discardedPlayer && it.canMinkan(tile) && it != seat[(seatIndex + 1) % 4] }
        else emptyList()

    /**
     * 根據 [tile] 判斷有沒有人可以 吃
     * 只有丟出 [tile] 的玩家的下家可以吃
     *
     * @param seatIndex 丟出 [tile] 的玩家的座位編號
     * @return 可以明槓的玩家的列表
     * */
    private fun canChiiList(
        tile: MahjongTileEntity,
        discardedPlayer: MahjongPlayerBase,
        seatIndex: Int = seat.indexOf(discardedPlayer),
    ): List<MahjongPlayerBase> =
        if (!board.isHoutei) players.filter { it != discardedPlayer && it.canChii(tile) && it == seat[(seatIndex + 1) % 4] }
        else emptyList()

    /**
     * 根據 [tile] 判斷有沒有人可以 榮和
     *
     * @return 可以榮和的玩家的列表
     * */
    private fun canRonList(
        tile: MahjongTileEntity,
        discardedPlayer: MahjongPlayerBase,
        isChanKan: Boolean = false,
    ): List<MahjongPlayerBase> = players.filter {
        if (it == discardedPlayer) return@filter false //丟牌玩家不能和自己的牌
        if (it.discardedTiles.isEmpty() && it.isMenzenchin) return@filter false //如果這個玩家連牌都沒丟過而且門前清, 先跳過
        val canWin = it.canWin(
            winningTile = tile.mahjongTile,
            isWinningTileInHands = false,
            rule = rule,
            generalSituation = board.generalSituation,
            personalSituation = it.getPersonalSituation(isChankan = isChanKan)
        )
        val isFuriten = it.isFuriten(tile = tile, discards = board.discards) //需要沒有振聽
        (canWin && !isFuriten)
    }

    /**
     * 根據 [kanTile] 判斷有沒有人可以 搶加槓
     *
     * @return 可以搶槓的玩家的列表
     * */
    private fun canChanKakanList(
        kanTile: MahjongTileEntity,
        discardedPlayer: MahjongPlayerBase,
    ): List<MahjongPlayerBase> =
        canRonList(tile = kanTile, isChanKan = true, discardedPlayer = discardedPlayer)

    /**
     * 根據 [kanTile] 判斷有沒有人可以 搶暗槓
     * 只有國士無雙才能搶暗槓
     *
     * @return 可以搶槓的玩家的列表
     * */
    private fun canChanAnkanList(
        kanTile: MahjongTileEntity,
        discardedPlayer: MahjongPlayerBase,
    ): List<MahjongPlayerBase> =
        canChanKakanList(
            kanTile = kanTile,
            discardedPlayer = discardedPlayer
        ).filter { it.isKokushimuso(kanTile.mahjong4jTile) }

    /**
     * 當前能夠流局滿貫的玩家
     * */
    private fun canNagashiManganList(): List<MahjongPlayerBase> =
        if (board.wall.isEmpty()) { //牌山必須為空
            players.filter {
                val discardedTilesNoCall = it.discardedTiles == it.discardedTilesForDisplay //棄牌堆的牌全都在
                val discardedTilesAllYaochu = it.discardedTiles.all { tile -> tile.mahjong4jTile.isYaochu } //全部都打么九牌
                discardedTilesNoCall && discardedTilesAllYaochu
            }
        } else listOf()

    /**
     * 玩家榮和其他玩家,
     * @param target 放槍的對象
     * @param tile 槍牌
     * */
    private suspend fun List<MahjongPlayerBase>.ron(
        target: MahjongPlayerBase,
        isChankan: Boolean = false,
        tile: MahjongTileEntity,
    ) {
        val yakuSettlementList = mutableListOf<YakuSettlement>()
        val scoreList = mutableListOf<ScoreItem>()
        val honbaScore = round.honba * 300 //場棒的分數
        var totalScore = honbaScore //這個總分是被胡的人將要扣的分數, 先算上場棒的分數
        val allRiichiStickQuantity = players.sumOf { it.riichiStickAmount } //所有立直棒的數量
        //額外分數->計算場上的積棒, 立直棒一根 1000, 本場棒一根 300 (只會給頭跳, 即摸牌順序離被榮和的人最近的那一位)
        val extraScore = allRiichiStickQuantity * ScoringStick.P1000.point + honbaScore
        val seatOrderFromTarget = List(4) { //從被榮和的玩家開始排序玩家位置
            val targetIndex = seat.indexOf(target)
            seat[(targetIndex + it) % 4]
        }
        val atamahanePlayer = seatOrderFromTarget.find { it in this } //找到頭跳的玩家
        this.forEach { //榮和的玩家
            it.playSoundAtSeat(soundEvent = SoundRegistry.ron) //多個人榮和會同時播放聲音
            it.openHands()
            val isDealer = it == seatOrderFromDealer[0]
            val isAtamahanePlayer = it == atamahanePlayer
            val settlement = it.calcYakuSettlementForWin(
                winningTile = tile.mahjongTile,
                isWinningTileInHands = false,
                rule = rule,
                generalSituation = board.generalSituation,
                personalSituation = it.getPersonalSituation(isChankan = isChankan),
                doraIndicators = board.doraIndicators.map { entity -> entity.mahjongTile },
                uraDoraIndicators = board.uraDoraIndicators.map { entity -> entity.mahjongTile }
            )
            yakuSettlementList += settlement
            //對分數做加減的動作
            val riichiStickPoints =
                if (it.riichi || it.doubleRiichi) ScoringStick.P1000.point else 0 //這個玩家這輪有沒有立直,有->還要減去立直棒的點數
            val basicScore = (settlement.score * if (isDealer) 1.5 else 1.0).toInt() //如果是莊家有 1.5 倍的分數
            val score = basicScore - riichiStickPoints + if (isAtamahanePlayer) extraScore else 0 //頭跳玩家會拿走所有積棒
            scoreList += ScoreItem(
                mahjongPlayer = it,
                scoreOrigin = it.points,
                scoreChange = score
            )
            it.points += score
            totalScore += basicScore //總分除去已經計算的場棒分數, 只會累計基本分數
        }
        target.also { //被榮和的玩家
            val riichiStickPoints =
                if (it.riichi || it.doubleRiichi) ScoringStick.P1000.point else 0 //這個玩家這輪有沒有立直,有->還要減去立直棒的點數
            scoreList += ScoreItem(
                mahjongPlayer = it,
                scoreOrigin = it.points,
                scoreChange = -(totalScore + riichiStickPoints)
            )
            it.points -= (totalScore + riichiStickPoints)
        }
        val remainingPlayers = players.toMutableList().also { it -= this.toSet(); it -= target } //除了 榮和 和 被榮和 以外的玩家
        remainingPlayers.forEach {
            val riichiStickPoints =
                if (it.riichi || it.doubleRiichi) ScoringStick.P1000.point else 0 //這個玩家這輪有沒有立直,有->還要減去立直棒的點數
            scoreList += ScoreItem(mahjongPlayer = it, scoreOrigin = it.points, scoreChange = -riichiStickPoints)
            it.points -= riichiStickPoints
        }
        val scoreSettlement = ScoreSettlement(
            titleTranslateKey = MahjongGameBehavior.RON.translateKey,
            scoreList = scoreList
        )
        realPlayers.sendSettlePacketAndDelay(yakuSettlementList, scoreSettlement)
    }

    /**
     * 玩家自摸
     * @param tile 自摸牌
     * */
    private suspend fun MahjongPlayerBase.tsumo(
        isRinshanKaihoh: Boolean = false,
        tile: MahjongTileEntity,
    ) {
        playSoundAtSeat(soundEvent = SoundRegistry.tsumo)
        val yakuSettlementList = mutableListOf<YakuSettlement>()
        val scoreList = mutableListOf<ScoreItem>()
        val allRiichiStickQuantity = players.sumOf { it.riichiStickAmount } //所有立直棒的數量
        //額外分數->計算場上的積棒, 立直棒一根 1000, 本場棒一根 300 (自摸全拿)
        val honbaScore = round.honba * 300 //場棒的分數
        val playerRiichiStickPoints =
            if (this.riichi || this.doubleRiichi) ScoringStick.P1000.point else 0 //這個自摸的玩家這輪有沒有立直,有->還要減去立直棒的點數
        //以下對自摸的玩家做計算
        val extraScore = allRiichiStickQuantity * ScoringStick.P1000.point + honbaScore
        val tsumoPlayerIsDealer = this == seatOrderFromDealer[0]
        val settlement = this.calcYakuSettlementForWin(
            winningTile = tile.mahjongTile,
            isWinningTileInHands = true,
            rule = rule,
            generalSituation = board.generalSituation,
            personalSituation = this.getPersonalSituation(isTsumo = true, isRinshanKaihoh = isRinshanKaihoh),
            doraIndicators = board.doraIndicators.map { entity -> entity.mahjongTile },
            uraDoraIndicators = board.uraDoraIndicators.map { entity -> entity.mahjongTile }
        )
        yakuSettlementList += settlement
        val basicScore = settlement.score //settlement 已經計算過莊家的點數, 不用再 * 1.5 了
        val score = basicScore - playerRiichiStickPoints + extraScore
        scoreList += ScoreItem(
            mahjongPlayer = this,
            scoreOrigin = this.points,
            scoreChange = score
        )
        this.points += score
        //以下對其他玩家做計算
        players.filter { it != this }.forEach {
            val riichiStickPoints =
                if (it.riichi || it.doubleRiichi) ScoringStick.P1000.point else 0 //這個玩家這輪有沒有立直,有->還要減去立直棒的點數
            if (tsumoPlayerIsDealer) { //自摸的那個玩家是否是莊家->分數平分
                val averageScore = (basicScore + honbaScore) / 3 //場棒的分數也要分擔
                val itsScore = averageScore + riichiStickPoints
                scoreList += ScoreItem(
                    mahjongPlayer = it,
                    scoreOrigin = it.points,
                    scoreChange = -itsScore
                )
                it.points -= itsScore
            } else { //自摸的玩家不是莊家->莊家要承受一半的點數
                val isDealer = it == seatOrderFromDealer[0]
                if (isDealer) {
                    val halfScore = basicScore / 2
                    val itsScore = halfScore + honbaScore / 3 + riichiStickPoints //場棒的分數也要分擔
                    scoreList += ScoreItem(
                        mahjongPlayer = it,
                        scoreOrigin = it.points,
                        scoreChange = -itsScore
                    )
                    it.points -= itsScore
                } else {
                    val quartScore = basicScore / 4
                    val itsScore = quartScore + honbaScore / 3 + riichiStickPoints //場棒的分數也要分擔
                    scoreList += ScoreItem(
                        mahjongPlayer = it,
                        scoreOrigin = it.points,
                        scoreChange = -itsScore
                    )
                    it.points -= itsScore
                }
            }
        }
        val scoreSettlement = ScoreSettlement(
            titleTranslateKey = MahjongGameBehavior.TSUMO.translateKey,
            scoreList = scoreList
        )
        realPlayers.sendSettlePacketAndDelay(
            yakuSettlementList = yakuSettlementList,
            scoreSettlement = scoreSettlement
        )
    }

    /**
     * 玩家流局滿貫,
     * 當成自摸處理, 要考慮多人流局滿貫的情況 (機率超級小, 且應該最多兩個人可以流局滿貫)
     * */
    private suspend fun List<MahjongPlayerBase>.nagashiMangan() {
        val yakuSettlementList = mutableListOf<YakuSettlement>()
        val scoreList = mutableListOf<ScoreItem>()
        //計算 scoreList 並加減玩家的分數
        //多人流局滿貫時積棒算誰的(? 目前應該算最靠近莊家的那個玩家的
        val atamahanePlayer = seatOrderFromDealer.find { it in this } //找到頭跳的玩家 (從莊家開始找)
        val allRiichiStickQuantity = players.sumOf { it.riichiStickAmount } //所有立直棒的數量
        val honbaScore = round.honba * 300 //場棒的分數
        val extraScore = allRiichiStickQuantity * ScoringStick.P1000.point + honbaScore
        val originalScoreList = hashMapOf<String, Int>().apply { //以 <stringUUID, 分數> 儲存原本的分數
            players.forEach { this[it.uuid] = it.points }
        }
        val dealer = seatOrderFromDealer[0]
        this.forEach {
            val yakuSettlement = YakuSettlement.nagashiMangan(
                mahjongPlayer = it,
                doraIndicators = board.doraIndicators.map { entity -> entity.mahjongTile },
                uraDoraIndicators = board.uraDoraIndicators.map { entity -> entity.mahjongTile },
                isDealer = it == dealer
            )
            yakuSettlementList += yakuSettlement
            val basicScore = yakuSettlement.score
            val score = basicScore + if (it == atamahanePlayer) extraScore else 0
            it.points += score
            players.filter { player -> player != it }.forEach { player ->
                player.points -= (basicScore / 3)
                if (it == atamahanePlayer) player.points -= (honbaScore / 3) //要分擔場棒的分數
            }
        }
        players.forEach {
            val originalScore = originalScoreList[it.uuid]!!
            val scoreChange = it.points - originalScore
            scoreList += ScoreItem(
                mahjongPlayer = it,
                scoreOrigin = originalScore,
                scoreChange = -scoreChange
            )
        }
        val scoreSettlement = ScoreSettlement(
            titleTranslateKey = MahjongGameBehavior.TSUMO.translateKey,
            scoreList = scoreList
        )
        realPlayers.sendSettlePacketAndDelay(
            yakuSettlementList = yakuSettlementList,
            scoreSettlement = scoreSettlement
        )
    }

    /**
     * 發送給玩家結算的數據包, 會等待數據包應該等待的時間
     * */
    private suspend fun List<MahjongPlayer>.sendSettlePacketAndDelay(
        yakuSettlementList: List<YakuSettlement>? = null,
        scoreSettlement: ScoreSettlement? = null,
    ) {
        if (yakuSettlementList != null) {
            this.forEach { //發送給玩家役結算畫面的數據
                sendPayloadToPlayer(
                    player = it.entity,
                    payload = MahjongGamePayload(
                        behavior = MahjongGameBehavior.YAKU_SETTLEMENT,
                        extraData = Json.encodeToString(yakuSettlementList)
                    )
                )
            }
            val yakuSettlementTime = YakuSettleHandler.defaultTime * 1000L * yakuSettlementList.size //顯示役結算畫面需要的時間
            delayOnServer(yakuSettlementTime)
        }
        if (scoreSettlement != null) {
            this.forEach { //發送給玩家分數結算畫面的數據
                sendPayloadToPlayer(
                    player = it.entity,
                    payload = MahjongGamePayload(
                        behavior = MahjongGameBehavior.SCORE_SETTLEMENT,
                        extraData = Json.encodeToString(scoreSettlement)
                    )
                )
            }
            val scoreSettlementTime = ScoreSettleHandler.defaultTime * 1000L //顯示分數結算畫面需要的時間
            delayOnServer(scoreSettlementTime)
        }
    }

    /**
     * 玩家收到遊戲結束的數據
     * */
    private fun MahjongPlayer.gameOver() {
        if (!isHost(this.entity)) ready = false //非 Host 玩家會取消準備
        cancelWaitingBehavior = true

        // 對玩家發送 GAME_OVER 的數據包
        sendPayloadToPlayer(
            player = this.entity,
            payload = MahjongGamePayload(behavior = MahjongGameBehavior.GAME_OVER)
        )
    }

    /**
     * 擲骰,
     * 並設定骰子點數 [dicePoints],
     * 最後會回傳兩個骰子
     * */
    private suspend fun rollDice(): List<DiceEntity> {
        val dices = List(2) {
            DiceEntity(
                world = world,
                pos = Vec3d(pos.x + 0.5, pos.y.toDouble() + 1.5, pos.z + 0.5),
                yaw = (-180 until 180).random().toFloat()
            ).apply {
                isSpawnedByGame = true
                gameBlockPos = this@MahjongGame.pos
                val sin = MathHelper.sin(yaw * 0.017453292F - 11)
                val cos = MathHelper.cos(yaw * 0.017453292F - 11)
                val range = if (it == 0) (15..90) else (-90..-15)
                val randomMoveXPercentage = ((range).random()) / 100.0
                val randomMoveZPercentage = ((range).random()) / 100.0
                setVelocity(0.1 * cos * randomMoveXPercentage, 0.03, 0.1 * sin * randomMoveZPercentage)
                ServerScheduler.scheduleDelayAction { world.spawnEntity(this) }
            }
        }
        //骰子的點數, 不管自動骰到的點數是多少, 骰完的時候都會使用這兩個點數替代
        val dicePoints = dices.associateWith { DicePoint.random() }
        //這邊等待骰子都結束 rolling 才會跳出 while 迴圈
        while (dices.any { it.rolling }) delayOnServer(50)
        dices.forEach { diceEntity -> diceEntity.point = dicePoints[diceEntity]!! }
        //總和的點數
        val totalPoints = dicePoints.values.sumOf { it.value }
        delayOnServer(500) //給一個延遲, 讓擲骰完不要馬上顯示骰到的點數
        val pointsSumText =
            Text.translatable("$MOD_ID.game.dice_points").formatted(Formatting.GOLD) + " §c$totalPoints"
        realPlayers.map { it.entity }.sendTitles(subtitle = pointsSumText)
        this@MahjongGame.dicePoints = totalPoints
        return dices
    }

    /**
     * 開始遊戲
     * */
    override fun start(sync: Boolean) {
        //開始遊戲
        status = GameStatus.PLAYING
        seat = players.toMutableList().apply {
            shuffle() //隨編排座位
            forEachIndexed { index, mjPlayer ->
                //傳送到對應座位的
                with(mjPlayer) {
                    val yaw = 90 - 90f * index
                    val stoolX = pos.x + if (index == 0) 2 else if (index == 2) -2 else 0
                    val stoolZ = pos.z + if (index == 1) -2 else if (index == 3) 2 else 0
                    val stoolBlockPos = BlockPos(stoolX, pos.y, stoolZ) //麻將凳應該在的位置
                    val blockState = world.getBlockState(stoolBlockPos)
                    val block = blockState.block //在麻將凳應該在的位置上的方塊
                    if (block is MahjongStool && SeatEntity.canSpawnAt(
                            world,
                            stoolBlockPos,
                            checkEntity = false
                        )
                    ) { //檢查有沒有麻將凳的存在和高度是否足夠
                        ServerScheduler.scheduleDelayAction { //先傳送再讓玩家坐在椅子上, 因為先把玩家傳到椅子後面, 所以玩家會自動看向麻將桌
                            val x = pos.x + 0.5 + if (index == 0) 3 else if (index == 2) -3 else 0
                            val y = pos.y.toDouble()
                            val z = pos.z + 0.5 + if (index == 1) -3 else if (index == 3) 3 else 0
                            this.teleport(world, x, y, z, yaw, 0f) //先將玩家傳送到椅子後面
                            val offsetY = if (this is MahjongPlayer) 0.4 else 1.0 / 16.0 * 10.0
                            SeatEntity.forceSpawnAt(
                                entity = this.entity,
                                world = world,
                                pos = stoolBlockPos,
                                sitOffsetY = offsetY
                            )
                            if (this is MahjongBot) this.entity.isInvisible = false //Bot->傳送後再解除隱形
                        }
                    } else { //沒有麻將凳或者高度不夠
                        fun BlockPos.collisionExists() =
                            this.let { !world.getBlockState(it).getCollisionShape(world, it).isEmpty }

                        val blockBelowCollisionExists = stoolBlockPos.offset(Direction.DOWN).collisionExists()
                        val blockCollisionDoesNotExist = !stoolBlockPos.collisionExists()
                        val blockAboveCollisionDoesNotExist = !stoolBlockPos.offset(Direction.UP).collisionExists()
                        if (blockBelowCollisionExists && blockCollisionDoesNotExist && blockAboveCollisionDoesNotExist) {
                            this.teleport(world, stoolX + 0.5, pos.y.toDouble(), stoolZ + 0.5, yaw, 0f) //將玩家傳送到凳子的位置上
                        } else { //最後不能的話就生在桌子上, 會看向自己的凳子的方向
                            this.teleport(
                                world,
                                pos.x + 0.5,
                                pos.y + 1.2,
                                pos.z + 0.5,
                                yaw + 180, //yaw 會朝凳子方向
                                0f
                            )
                        }
                        if (this is MahjongBot) this.entity.isInvisible = false //Bot->傳送後再解除隱形
                    }
                }
            }
        }
        round = rule.length.getStartingRound()
        players.forEach {
            it.points = rule.startingPoints //設置玩家的初始點數
            it.basicThinkingTime = rule.thinkingTime.base
            it.extraThinkingTime = rule.thinkingTime.extra
        }
        realPlayers.forEach {
            it.cancelWaitingBehavior = false
            sendPayloadToPlayer(
                player = it.entity,
                payload = MahjongGamePayload(behavior = MahjongGameBehavior.GAME_START)
            )
        }
        if (sync) syncMahjongTable()  //開始遊戲同步麻將桌
        //延遲 0.5 秒後再開始
        val handler = CoroutineExceptionHandler { _, _ -> } //通常只有中途拆了麻將桌才會導致這的錯誤, 目前先直接無視
        jobWaitForStart = CoroutineScope(Dispatchers.IO).launch(handler) {
            delayOnServer(500)
            startRound()
        }
    }

    /**
     * 結束遊戲,
     * 請在主線程上調用
     *
     * @param sync 是否要同步 [MahjongTableBlockEntity]
     * */
    override fun end(sync: Boolean) {
        //結束遊戲
        status = GameStatus.WAITING
        jobWaitForStart?.cancel()
        jobRound?.cancel()
        seat.clear()
        clearStuffs()
        round = MahjongRound()
        realPlayers.forEach { it.gameOver() }
        botPlayers.forEach { //將電腦傳回原本的位置
            it.entity.isInvisible = true
            it.entity.teleport(tableCenterPos.x, tableCenterPos.y, tableCenterPos.z)
        }
        if (sync) syncMahjongTable()  //結束遊戲要同步麻將桌
    }

    /**
     * 當玩家破壞麻將桌時
     * */
    override fun onBreak() {
        if (isPlaying) {
            showGameResult()
            end(sync = false)
        }
        val message = PREFIX + Text.translatable("$MOD_ID.game.message.game_block_is_destroyed")
        realPlayers.forEach { //傳給玩家->麻將桌被破壞的訊息
            it.sendMessage(message)
        }
        botPlayers.forEach {  //將電腦從遊戲中移除
            it.entity.remove(Entity.RemovalReason.DISCARDED)
        }
        players.clear()
        GameManager.games -= this
    }

    /**
     * 有任何玩家離開遊戲時結束遊戲, 向玩家顯示有人離開遊戲
     * */
    override fun onPlayerDisconnect(player: ServerPlayerEntity) {
        if (isPlaying) {
            showGameResult()
            end(sync = false)
        }
        leave(player)
        val message = PREFIX + Text.translatable("$MOD_ID.game.message.player_left_game", player.displayName)
        realPlayers.forEach { //傳給玩家-> [player] 離開遊戲的訊息
            it.sendMessage(message)
        }
        syncMahjongTable() //有任何玩家離開遊戲時同步
    }

    /**
     * 有任何玩家切換世界時結束遊戲, 向玩家顯示有人不在當前的世界
     * */
    override fun onPlayerChangedWorld(player: ServerPlayerEntity) {
        if (isPlaying) {
            showGameResult()
            end(sync = false)
        }
        leave(player)
        val message =
            PREFIX + Text.translatable("$MOD_ID.game.message.player_is_not_in_this_world", player.displayName)
        realPlayers.forEach {  //傳給玩家-> [player] 改變世界的訊息
            it.sendMessage(message)
        }
        syncMahjongTable() //有任何玩家切換世界時同步
    }

    override fun onServerStopping(server: MinecraftServer) {
        if (isPlaying) end(sync = false)
        realPlayers.forEach { leave(it.entity) }
    }

    /**
     * 同步 [MahjongTableBlockEntity]
     * */
    private fun syncMahjongTable(invokeOnNextTick: Boolean = true) {
        MahjongTablePayloadListener.syncBlockEntityWithGame(invokeOnNextTick = invokeOnNextTick, game = this)
    }

    /**
     * 判斷玩家是否是主持
     * */
    override fun isHost(player: ServerPlayerEntity): Boolean =
        players.firstOrNull()?.let { it.entity == player } ?: false

    /**
     * 取得 mahjong4j 判定用的 [PersonalSituation],
     *
     * @param isTsumo 是否自摸
     * @param isChankan 是否搶槓
     * @param isRinshanKaihoh 是否嶺上開花
     * */
    private fun MahjongPlayerBase.getPersonalSituation(
        isTsumo: Boolean = false,
        isChankan: Boolean = false,
        isRinshanKaihoh: Boolean = false,
    ): PersonalSituation {
        val selfWindNumber = seatOrderFromDealer.indexOf(this) //從以莊家開始排序的座位中取得 this 玩家的座位編號 (與自風編號一樣)
        val jikaze = Wind.values()[selfWindNumber].tile
        val isIppatsu: Boolean = isIppatsu(players, board.discards) //是否一發
        return PersonalSituation(
            isTsumo,
            isIppatsu,
            this.riichi,
            this.doubleRiichi,
            isChankan,
            isRinshanKaihoh,
            jikaze
        )
    }

    /**
     * 取得玩家在丟了 [tile] 之後會聽的所有牌跟對應的翻數
     * */
    fun MahjongPlayerBase.getMachiAndHan(tile: MahjongTile): Map<MahjongTile, Int> {
        if (board.deadWall.isEmpty()) return emptyMap() //王牌區還沒初始化, 直接回傳空 map
        val handsForCalculate = this.hands.toMahjongTileList().toMutableList().apply { remove(tile) }
        return this.calculateMachiAndHan(
            hands = handsForCalculate,
            rule = rule,
            generalSituation = board.generalSituation,
            personalSituation = this.getPersonalSituation(
                isTsumo = false,
                isChankan = false,
                isRinshanKaihoh = false
            )
        )
    }

    /**
     * 計算是否振聽, 接在 [getMachiAndHan] 使用
     *
     * @param machi 直接使用 [getMachiAndHan] 的 keys 即可
     * */
    fun MahjongPlayerBase.isFuriten(tile: MahjongTile, machi: List<MahjongTile>): Boolean =
        this.isFuriten(tile.mahjong4jTile, board.discards.map { it.mahjong4jTile }, machi.map { it.mahjong4jTile })

    companion object {
        /**
         * 最小等待時間,
         * 用來讓玩家丟牌、思考、等待...的過程看起來更合理
         *
         * (單位: 毫秒)
         * */
        const val MIN_WAITING_TIME = 1200L

        /**
         * 積棒最多一層幾個
         * */
        const val STICKS_PER_STACK = 5

        val PREFIX get() = Text.translatable("$MOD_ID.game.riichi_mahjong.prefix")
    }
}
