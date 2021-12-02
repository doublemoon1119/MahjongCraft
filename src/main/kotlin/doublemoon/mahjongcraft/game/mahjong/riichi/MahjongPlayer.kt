package doublemoon.mahjongcraft.game.mahjong.riichi

import doublemoon.mahjongcraft.entity.toMahjongTileList
import doublemoon.mahjongcraft.network.MahjongGamePacketHandler.sendMahjongGamePacket
import doublemoon.mahjongcraft.network.MahjongTablePacketHandler.sendMahjongTablePacket
import doublemoon.mahjongcraft.scheduler.server.ServerScheduler
import doublemoon.mahjongcraft.util.delayOnServer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.network.MessageType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.MutableText
import net.minecraft.util.Util
import net.minecraft.util.math.BlockPos
import java.util.*

/**
 * 麻將玩家
 * */
class MahjongPlayer(
    override val entity: ServerPlayerEntity
) : MahjongPlayerBase() {

    override val displayName: String
        get() = entity.entityName

    fun sendMahjongGamePacket(
        behavior: MahjongGameBehavior,
        hands: List<MahjongTile> = listOf(),
        target: ClaimTarget = ClaimTarget.SELF,
        extraData: String = "",
    ) {
        entity.sendMahjongGamePacket(behavior, hands, target, extraData)
    }

    fun sendMahjongTablePacket(
        behavior: MahjongTableBehavior,
        pos: BlockPos,
        extraData: String = ""
    ) {
        entity.sendMahjongTablePacket(behavior, pos, extraData)
    }

    fun sendMessage(
        text: MutableText,
        messageType: MessageType = MessageType.SYSTEM,
        senderUUID: UUID = Util.NIL_UUID
    ) {
        entity.sendMessage(text, messageType, senderUUID)
    }

    /**
     * 正在等待客戶端執行的動作
     * */
    val waitingBehavior = mutableListOf<MahjongGameBehavior>()

    /**
     * 取消等待 [waitingBehavior], 用在遊戲結束的時候, 避免遊戲結束卻還在倒數
     * */
    var cancelWaitingBehavior = false

    /**
     * 在發送數據包給玩家後,
     * 用來等待玩家回傳的數據,
     * 後面的 [String] 是拿來裝額外資料的,
     * 只有玩家有, 機器人沒有
     * */
    var behaviorResult: Pair<MahjongGameBehavior, String>? = null

    /**
     * 玩家不能丟的牌
     * */
    var cannotDiscardTiles = listOf<MahjongTile>()
        private set(value) {
            field = value
            //TODO 發數據包在客戶端提示不能丟的牌之類的
        }

    override fun teleport(targetWorld: ServerWorld, x: Double, y: Double, z: Double, yaw: Float, pitch: Float) {
        this.entity.teleport(targetWorld, x, y, z, yaw, pitch)
    }

    /**
     * 要求並讓玩家丟牌的功能,
     * 傳過去的額外資料是 空的 (不能丟的牌的 [MahjongTile] 編號列表, 交給 [MahjongPlayer.cannotDiscardTiles] 處理),
     * 傳回來的額外資料是 要丟的牌的 [MahjongTile] 編號
     * */
    override suspend fun askToDiscardTile(
        timeoutTile: MahjongTile,
        cannotDiscardTiles: List<MahjongTile>
    ): MahjongTile {
        this.cannotDiscardTiles = cannotDiscardTiles
        return waitForBehaviorResult(
            behavior = MahjongGameBehavior.DISCARD,
            extraData = "",
            target = ClaimTarget.SELF
        ) { behavior, data ->
            this.cannotDiscardTiles = listOf()
            val tileCode = data.toIntOrNull() ?: return@waitForBehaviorResult timeoutTile
            if (behavior == MahjongGameBehavior.DISCARD && tileCode in MahjongTile.values().indices) {
                MahjongTile.values()[tileCode]
            } else timeoutTile
        }
    }

    /**
     * 詢問玩家是否要吃的功能,
     * 傳過去的額外資料是 要進行吃的牌的編號,以及可以與之組成順子的牌的編號對的列表,
     * 傳回來的額外資料是 要與之組成順子的牌的編號對,
     * */
    override suspend fun askToChii(
        tile: MahjongTile,
        tilePairs: List<Pair<MahjongTile, MahjongTile>>,
        target: ClaimTarget
    ): Pair<MahjongTile, MahjongTile>? = waitForBehaviorResult(
        behavior = MahjongGameBehavior.CHII,
        extraData = Json.encodeToString( //轉成 Json 字串傳過去
            listOf(
                Json.encodeToString(tile),
                Json.encodeToString(tilePairs)
            )
        ),
        target = target
    ) { behavior, data ->
        val result = runCatching {
            Json.decodeFromString<Pair<MahjongTile, MahjongTile>>(data)
        }.getOrNull() ?: return@waitForBehaviorResult null
        if (behavior == MahjongGameBehavior.CHII && result in tilePairs) result
        else null
    }

    /**
     * 詢問玩家是否要碰或吃的功能,
     * 傳過去的額外資料是 要進行碰或吃的牌的編號,以及可以與之組成順子的牌對的列表,最後是可以與之組成刻子的牌對
     * 傳回來的額外資料是 如果是要吃->與 [tile] 組成順子的牌對,碰->原本的 [tile],
     * */
    override suspend fun askToPonOrChii(
        tile: MahjongTile,
        tilePairsForChii: List<Pair<MahjongTile, MahjongTile>>,
        tilePairForPon: Pair<MahjongTile, MahjongTile>,
        target: ClaimTarget
    ): Pair<MahjongTile, MahjongTile>? = waitForBehaviorResult(
        behavior = MahjongGameBehavior.PON_OR_CHII,
        waitingBehavior = listOf(
            MahjongGameBehavior.CHII,
            MahjongGameBehavior.PON,
            MahjongGameBehavior.SKIP
        ),
        extraData = Json.encodeToString( //轉成 Json 字串傳過去
            listOf(
                Json.encodeToString(tile),
                Json.encodeToString(tilePairsForChii),
                Json.encodeToString(tilePairForPon)
            )
        ),
        target = target
    ) { behavior, data ->
        when (behavior) {
            MahjongGameBehavior.CHII -> {
                val result = runCatching {
                    Json.decodeFromString<Pair<MahjongTile, MahjongTile>>(data)
                }.getOrNull() ?: return@waitForBehaviorResult null
                if (result in tilePairsForChii) result else null
            }
            MahjongGameBehavior.PON -> tile to tile
            else -> null
        }
    }


    /**
     * 詢問玩家是否要碰的功能,
     * 傳過去的額外資料是 要進行碰的牌 和 可以與之組成刻子的牌對,
     * 傳回來的額外資料是 空白
     * */
    override suspend fun askToPon(
        tile: MahjongTile,
        tilePairForPon: Pair<MahjongTile, MahjongTile>,
        target: ClaimTarget
    ): Boolean = waitForBehaviorResult(
        behavior = MahjongGameBehavior.PON,
        extraData = Json.encodeToString( //轉成 Json 字串傳過去
            listOf(
                Json.encodeToString(tile),
                Json.encodeToString(tilePairForPon)
            )
        ),
        target = target
    ) { behavior, _ ->
        behavior == MahjongGameBehavior.PON
    }

    /**
     * 詢問玩家是否要暗槓或加槓的功能,
     * 傳過去的額外資料是 可以進行暗槓 以及 可以進行加槓的牌的集合 和 規則,
     * 傳回來的額外資料是 要暗槓或加槓的 [MahjongTile], null 表示沒有要暗槓
     * */
    override suspend fun askToAnkanOrKakan(
        canAnkanTiles: Set<MahjongTile>,
        canKakanTiles: Set<Pair<MahjongTile, ClaimTarget>>,
        rule: MahjongRule
    ): MahjongTile? = waitForBehaviorResult(
        behavior = MahjongGameBehavior.ANKAN_OR_KAKAN,
        extraData = Json.encodeToString(
            listOf(
                Json.encodeToString(canAnkanTiles),
                Json.encodeToString(canKakanTiles),
                rule.toJsonString()
            )
        ),
        target = ClaimTarget.SELF
    ) { behavior, data ->
        val result = runCatching {
            Json.decodeFromString<MahjongTile>(data)
        }.getOrNull() ?: return@waitForBehaviorResult null
        if (behavior == MahjongGameBehavior.ANKAN_OR_KAKAN) {
            if (result in canAnkanTiles || result in canKakanTiles.unzip().first) result else null
        } else null
    }

    /**
     * 詢問玩家是否要明槓的功能,
     * 傳過去的額外資料是 要進行明槓的牌 和 規則,
     * 傳回來的額外資料是 空白
     *
     * @return 由回傳的行為決定是否要碰還是要槓還是跳過
     * */
    override suspend fun askToMinkanOrPon(
        tile: MahjongTile,
        target: ClaimTarget,
        rule: MahjongRule
    ): MahjongGameBehavior = waitForBehaviorResult(
        behavior = MahjongGameBehavior.MINKAN,
        waitingBehavior = listOf(MahjongGameBehavior.PON, MahjongGameBehavior.MINKAN),
        extraData = Json.encodeToString(
            listOf(
                Json.encodeToString(tile),
                rule.toJsonString()
            )
        ),
        target = target
    ) { behavior, _ ->
        when (behavior) {
            MahjongGameBehavior.PON -> behavior
            MahjongGameBehavior.MINKAN -> behavior
            else -> MahjongGameBehavior.SKIP
        }
    }

    /**
     * 詢問玩家是否要立直的功能,
     * 傳過去的額外資料是 要丟的牌和丟了之後對應會聽的牌,
     * 傳回來的額外資料是 空白
     * */
    override suspend fun askToRiichi(tilePairsForRiichi: List<Pair<MahjongTile, List<MahjongTile>>>): MahjongTile? =
        waitForBehaviorResult(
            behavior = MahjongGameBehavior.RIICHI,
            extraData = Json.encodeToString(tilePairsForRiichi),
            target = ClaimTarget.SELF
        ) { behavior, data ->
            val result = runCatching {
                Json.decodeFromString<MahjongTile>(data)
            }.getOrNull() ?: return@waitForBehaviorResult null
            if (behavior == MahjongGameBehavior.RIICHI && result in tilePairsForRiichi.unzip().first) result
            else null
        }

    /**
     * 詢問玩家是否要自摸的功能,
     * 傳過去的額外資料是 空白,
     * 傳回來的額外資料是 空白
     * */
    override suspend fun askToTsumo(): Boolean = waitForBehaviorResult(
        behavior = MahjongGameBehavior.TSUMO,
        extraData = "",
        target = ClaimTarget.SELF
    ) { behavior, _ ->
        behavior == MahjongGameBehavior.TSUMO
    }

    /**
     * 詢問玩家是否要榮和的功能,
     * 傳過去的額外資料是 要榮和的牌的編號,
     * 傳回來的額外資料是 空白
     * */
    override suspend fun askToRon(tile: MahjongTile, target: ClaimTarget): Boolean = waitForBehaviorResult(
        behavior = MahjongGameBehavior.RON,
        extraData = Json.encodeToString(tile),
        target = target
    ) { behavior, _ ->
        behavior == MahjongGameBehavior.RON
    }

    override suspend fun askToKyuushuKyuuhai(): Boolean = waitForBehaviorResult(
        behavior = MahjongGameBehavior.KYUUSHU_KYUUHAI,
        extraData = "",
        target = ClaimTarget.SELF
    ) { behavior, _ ->
        behavior == MahjongGameBehavior.KYUUSHU_KYUUHAI
    }

    /**
     * 發送給玩家行為 [behavior],
     * 傳過去的資料分別是 :
     * 1.手牌 [hands]
     * 2.額外資料 [extraData] (轉成 [Json] 字串列表後傳給玩家)
     * 3.鳴牌對象
     *
     * 最後收到的結果的時候會執行 [onResult], 會傳回對應的 [MahjongGameBehavior] 以及字串形式的資料 [String],
     * 超時的話會得到結果 [MahjongGameBehavior.SKIP]
     * */
    private suspend fun <T> waitForBehaviorResult(
        behavior: MahjongGameBehavior,
        waitingBehavior: List<MahjongGameBehavior> = listOf(behavior),
        extraData: String,
        hands: List<MahjongTile> = this.hands.toMahjongTileList(),
        target: ClaimTarget,
        onResult: (MahjongGameBehavior, String) -> T
    ): T {
        this.waitingBehavior += waitingBehavior
        this.waitingBehavior += MahjongGameBehavior.SKIP
        this.sendMahjongGamePacket(
            behavior = behavior,
            hands = hands,
            target = target,
            extraData = extraData
        )
        var tBase = basicThinkingTime
        var tExtra = extraThinkingTime
        var count = 0
        var completed = false
        val action = ServerScheduler.scheduleRepeatAction(
            times = (tBase + tExtra) * 20 + 1, //間隔 1 tick (50ms) 的話, 1 sec = 20 ticks
            interval = 0,  // 1000 ms 響應時間太慢, 看起來卡卡的, 這裡給 0 ,表示每個 tick 都執行一次
        ) {
            count++
            if (cancelWaitingBehavior || behaviorResult != null || ((tBase + tExtra) <= 0 && count % 20 == 1)) {
                completed = true
            } else if ((tBase + tExtra) > 0 && count % 20 == 1) {
                entity.sendMahjongGamePacket(
                    behavior = MahjongGameBehavior.COUNTDOWN_TIME,
                    extraData = Json.encodeToString<Pair<Int?, Int?>>(tBase to tExtra)
                )
                if (tBase > 0) {
                    tBase--
                } else if (tExtra > 0) {
                    tExtra--
                }
            }
        }
        while (!completed) delayOnServer(50)
        ServerScheduler.removeQueuedAction(action)
        entity.sendMahjongGamePacket( //傳兩個 null 過去表示倒數結束
            behavior = MahjongGameBehavior.COUNTDOWN_TIME,
            extraData = Json.encodeToString<Pair<Int?, Int?>>(null to null)
        )
        val usedExtraTime = extraThinkingTime - tExtra //使用過的額外思考時間
        extraThinkingTime -= usedExtraTime //扣掉用掉的額外思考時間
        val result = behaviorResult ?: (MahjongGameBehavior.SKIP to "")
        behaviorResult = null //最後重置 behaviorResult
        this.waitingBehavior.clear() //重置等待中的動作
        return onResult.invoke(result.first, result.second)
    }
}