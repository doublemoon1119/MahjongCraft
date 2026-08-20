package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.MeldPresentation
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.FabricAppCoroutineScope
import com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.ServerThreadCoroutineDispatcher
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.fabric.server.time.FabricTickMonotonicClock
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDicePresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.seatIndexToTableSide
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.MahjongSeatingPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDiscardPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDiscardPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongInitialDealPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongMeldTileGroup
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongPlayerAreaPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongPlayerAreaPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresenter
import kotlinx.coroutines.launch
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.property.Properties
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.uuid.Uuid

/**
 * [GamePresentationPublisher] 的 Fabric 實作，薄分派層。
 *
 * @property seatingPresenter 開局座位傳送的實際呈現邏輯。
 * @property diceRollPresenter 正式擲骰的實際呈現邏輯。
 * @property tileWallPresenter 正式牌牆的實際呈現邏輯。
 * @property playerAreaPresenter 正式手牌／摸牌位／副露（合併，理由見 [MahjongPlayerAreaPresenter]
 *   KDoc）的實際呈現邏輯。
 * @property discardPresenter 正式牌河的實際呈現邏輯。
 * @property scoringStickPresenter 正式積棒的實際呈現邏輯，生命週期跟牌牆同時生成/清除，見
 *   [MahjongScoringStickPresenter] KDoc。
 * @property tableLocationRegistry 麻將桌最後已知位置索引。
 * @property serverHolder 目前運行中的 server，供世界／方塊狀態查詢使用。
 * @property busyTracker 呈現動畫播放期間標記該桌暫時忙碌，供輸入分派入口與自動操作心跳擋下操作。
 * @property scope 承接世界／方塊狀態查詢需要切回伺服器主執行緒的工作。
 * @property dispatchers 切回伺服器主執行緒用的 dispatcher。
 * @property tickClock 排定手牌延遲落地用的 tick 排程器，跟 [busyTracker] 共用同一套暫停感知計時。
 */
@Single(binds = [GamePresentationPublisher::class])
class FabricGamePresentationPublisher(
    private val seatingPresenter: MahjongSeatingPresenter,
    private val diceRollPresenter: MahjongDiceRollPresenter,
    private val tileWallPresenter: MahjongTileWallPresenter,
    private val playerAreaPresenter: MahjongPlayerAreaPresenter,
    private val discardPresenter: MahjongDiscardPresenter,
    private val scoringStickPresenter: MahjongScoringStickPresenter,
    private val tableLocationRegistry: TableLocationRegistry,
    private val serverHolder: FabricServerHolder,
    private val busyTracker: TablePresentationBusyTracker,
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    private val tickClock: FabricTickMonotonicClock,
) : GamePresentationPublisher {
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    /**
     * 記錄每張桌子最近一次 [publishWallStructure] 算出的牌牆生成掉落動畫總時長（ticks）——[publishDiceRoll]
     * 與 [publishInitialDealAnimation] 都會讀取，用來把擲骰動畫、發牌動畫依序延遲到牌牆完全落地、
     * 擲骰動畫也播完才開始播放，符合真實麻將先砌牌牆、擲骰開門、才發牌的順序。
     *
     * 每次 [publishWallStructure] 呼叫都覆寫（不是單次消費後移除）——同一張桌子每局都會重新呼叫
     * [publishWallStructure]，覆寫掉上一局的舊值，兩個讀取端都能各自安全讀到本局的值，不需要協調
     * 誰先讀、誰清除。依賴呼叫端（`StartGameUseCase`／`AdvanceRoundUseCase`）固定先呼叫
     * [publishWallStructure] 才呼叫 [publishDiceRoll]／[publishInitialDealAnimation]；找不到對應紀錄
     * （例如規則沒有牌牆）時預設視為 `0`，不強制要求呼叫順序。
     */
    private val wallDropTicksByTable = ConcurrentHashMap<Uuid, Int>()

    /**
     * 查無桌子位置、世界、或 controller 目前不是合法麻將桌方塊時直接放棄——比照本介面 best-effort
     * 的既有慣例，不拋例外、不影響呼叫端的權威狀態變更；每個放棄點都留一則 DEBUG log，方便排查
     * 「規則有配置骰子、但畫面上完全沒出現」這種靜默失敗。
     *
     * 世界／方塊狀態查詢一定要丟回伺服器主執行緒才能執行——`GameFlowCoordinator` 的呼叫鏈跑在
     * `Dispatchers.Default`（見 [FabricAppCoroutineScope]），
     * 直接在這裡讀 `world.getBlockState`／`getBlockEntity` 會在非主執行緒上跟伺服器自己的 tick
     * 並發存取同一份 chunk／block entity 資料，讀到不一致或直接讀不到，結果就是靜默呈現
     * `TABLE_NOT_FOUND`——這是曾經在這裡踩過、log 追出來的真實問題，不是假設。用 [dispatchers] 的
     * `main`（[ServerThreadCoroutineDispatcher]）
     * 而不是手動 `server.execute`：呼叫端不需要等世界端真的完成才能返回，符合本介面 best-effort、
     * 不阻塞呼叫端的既有慣例。
     *
     * 骰子 entity 本身何時自動消失，不歸這裡管——`MahjongDiceEntity.tick()` 自己會依
     * `animationStartGameTime` 判斷是否已經播完＋額外觀看時間，時間到就自我 `discard()`，同一段邏輯
     * 天然涵蓋伺服器崩潰重啟的情境（entity 重新載入後第一個 tick 就會算出「早就該消失了」），這裡
     * 不需要另外排一個計時器，也不需要處理骰子本身的清理。
     *
     * [busyTracker] 的標記刻意寫在方法最前面、同步執行，不是等實際呈現成功後才標記——呼叫端
     * （`AdvanceRoundUseCase`／`StartGameUseCase`）呼叫完這個方法就會緊接著繼續走自己的自動連鎖
     * （莊家自動摸牌、開始思考計時器），如果標記忙碌這件事本身也要等非同步的世界呈現跑完才發生，
     * 兩邊完全沒有因果關係、純粹看哪個先跑完，曾經真的看過自動連鎖贏過這個標記、玩家動畫都還沒播完
     * 就已經被叫著打牌。同步標記才能保證呼叫端往下走之前，忙碌狀態已經生效。代價是就算之後
     * [present] 真的失敗（例如桌子被拆掉），這桌還是會被錯誤標記忙碌一小段時間——比起每一次擲骰都
     * 有機會被搶跑，這個機率很低的邊界情況划算得多。
     *
     * 真正的呈現（[presentDiceRoll]）延遲到 [wallDropTicksByTable] 記錄的牌牆掉落動畫播完才
     * 觸發，理由見該欄位 KDoc；[busyTracker] 標記的時長需要涵蓋這段等待，因此是牌牆時長加上擲骰動畫
     * 本身時長的總和，不是單純擲骰動畫時長。
     */
    override fun publishDiceRoll(
        gameId: Uuid,
        dice: DiceRollResult,
        dealerSeatIndex: Int,
        roundNumber: Int,
        comboCount: Int,
    ) {
        logger.debug(
            "publishDiceRoll gameId={} dice={} dealerSeatIndex={} roundNumber={} comboCount={}",
            gameId,
            dice.values,
            dealerSeatIndex,
            roundNumber,
            comboCount,
        )
        if (serverHolder.current() == null) {
            logger.warn("publishDiceRoll gameId={} skipped: no active server", gameId)
            return
        }
        val wallDropTicks = wallDropTicksByTable[gameId] ?: 0
        val diceTicks = MahjongDiceTableLayout.totalAnimationTicks(dice.values.size)
        busyTracker.markBusyFor(gameId, wallDropTicks + diceTicks)
        tickClock.scheduleAfter(wallDropTicks * MILLIS_PER_TICK) {
            presentDiceRoll(gameId, dice, dealerSeatIndex, roundNumber, comboCount)
        }
    }

    /** 實際呼叫 [diceRollPresenter]，跟 [publishWallStructure] 同理丟回伺服器主執行緒執行。 */
    private fun presentDiceRoll(
        gameId: Uuid,
        dice: DiceRollResult,
        dealerSeatIndex: Int,
        roundNumber: Int,
        comboCount: Int,
    ) {
        scope.launch(dispatchers.main) {
            val resolved = resolveTableContext(gameId, "publishDiceRoll") ?: return@launch

            val presentation = MahjongDiceRollPresentation(
                tableId = gameId,
                tableLocation = resolved.location,
                tableFacing = resolved.facing,
                throwSide = seatIndexToTableSide(dealerSeatIndex),
                rollSequence = roundNumber.toLong() * ROLL_SEQUENCE_ROUND_MULTIPLIER + comboCount,
                dice = dice.values.map { point ->
                    MahjongDicePresentation(
                        point = point,
                        animationSeed = Random.nextLong(),
                    )
                },
            )
            val result = diceRollPresenter.present(presentation)
            logger.debug("publishDiceRoll gameId={} present() result={}", gameId, result)
        }
    }

    /**
     * 跟 [publishDiceRoll] 同理，牌牆呈現也需要碰觸世界／entity，一併丟回伺服器主執行緒執行。
     *
     * [busyTracker] 標記牌牆生成掉落動畫（波浪感，見 [FabricMahjongTileWallPresenter.startWallDropAnimations]）
     * 的忙碌時長，理由與同步標記的時機點跟 [publishDiceRoll] 完全一致——過去牌牆生成是瞬間完成，不需要
     * 標記；現在有動畫了，呼叫端（`StartGameUseCase`／`AdvanceRoundUseCase`）緊接著觸發的莊家自動摸牌
     * 不能搶在牌牆落地之前執行。`stacksPerSide` 的算法跟 [FabricMahjongTileWallPresenter.present]
     * 內部完全一致（取 `side == 0` 的最大 `stack + 1`），兩處必須同步，否則忙碌時長會跟實際動畫時長脫鉤。
     *
     * 同時把算出來的動畫時長寫進 [wallDropTicksByTable]，供緊接著呼叫的 [publishDiceRoll]／
     * [publishInitialDealAnimation] 讀取，延遲擲骰／發牌動畫到牌牆完全落地才開始播放；呼叫端固定先
     * 呼叫這個方法才呼叫另外兩者，見該欄位 KDoc。
     */
    override fun publishWallStructure(
        gameId: Uuid,
        structure: Map<Uuid, TileWallPosition>,
        dealerSeatIndex: Int,
        deadWallTileIds: Set<Uuid>,
        diceCount: Int,
        revealedTileIds: Set<Uuid>,
    ) {
        logger.debug(
            "publishWallStructure gameId={} tileCount={} dealerSeatIndex={} deadWallTileCount={} diceCount={} revealedTileCount={}",
            gameId,
            structure.size,
            dealerSeatIndex,
            deadWallTileIds.size,
            diceCount,
            revealedTileIds.size,
        )
        if (serverHolder.current() == null) {
            logger.warn("publishWallStructure gameId={} skipped: no active server", gameId)
            return
        }
        val stacksPerSide = structure.values.filter { position -> position.side == 0 }.maxOfOrNull { position -> position.stack + 1 } ?: 0
        val wallDropTicks = MahjongTileTableLayout.wallDropAnimationTicks(stacksPerSide)
        wallDropTicksByTable[gameId] = wallDropTicks
        busyTracker.markBusyFor(gameId, wallDropTicks)
        scope.launch(dispatchers.main) {
            val resolved = resolveTableContext(gameId, "publishWallStructure") ?: return@launch

            val presentation = MahjongTileWallPresentation(
                tableId = gameId,
                tableLocation = resolved.location,
                tableFacing = resolved.facing,
                dealerSeatIndex = dealerSeatIndex,
                structure = structure,
                deadWallTileIds = deadWallTileIds,
                diceCount = diceCount,
                revealedTileIds = revealedTileIds,
            )
            val result = tileWallPresenter.present(presentation)
            logger.debug("publishWallStructure gameId={} present() result={}", gameId, result)
        }
    }

    /**
     * 一般回合動作，不需要 [busyTracker] 或延遲，直接同步呈現；跟 [publishDiceRoll] 同理，世界／entity
     * 存取一併丟回伺服器主執行緒執行。
     */
    override fun publishDeadWallRevealUpdated(gameId: Uuid, revealedTileIds: Set<Uuid>) {
        logger.debug("publishDeadWallRevealUpdated gameId={} revealedTileCount={}", gameId, revealedTileIds.size)
        if (serverHolder.current() == null) {
            logger.warn("publishDeadWallRevealUpdated gameId={} skipped: no active server", gameId)
            return
        }
        scope.launch(dispatchers.main) {
            val location = tableLocationRegistry.get(gameId)?.location
            if (location == null) {
                logger.warn("publishDeadWallRevealUpdated gameId={} skipped: no known table location", gameId)
                return@launch
            }
            val result = tileWallPresenter.revealDeadWallTiles(gameId, location, revealedTileIds)
            logger.debug("publishDeadWallRevealUpdated gameId={} revealDeadWallTiles() result={}", gameId, result)
        }
    }

    /**
     * 積棒跟牌牆同一個時機點觸發（呼叫端緊接在 [publishWallStructure] 之後呼叫，見
     * [MahjongScoringStickPresenter] KDoc）——每回合結束（換局）就重新生成一批，不是每次打牌/摸牌/
     * 鳴牌都觸發。一般回合動作不會呼叫這個方法，不需要 [busyTracker] 或延遲，直接同步呈現；跟
     * [publishDiceRoll] 同理，世界／entity 存取一併丟回伺服器主執行緒執行。
     */
    override fun publishScoringSticksUpdated(gameId: Uuid, dealerSeatIndex: Int, stickCount: Int) {
        logger.debug("publishScoringSticksUpdated gameId={} dealerSeatIndex={} stickCount={}", gameId, dealerSeatIndex, stickCount)
        if (serverHolder.current() == null) {
            logger.warn("publishScoringSticksUpdated gameId={} skipped: no active server", gameId)
            return
        }
        scope.launch(dispatchers.main) {
            val resolved = resolveTableContext(gameId, "publishScoringSticksUpdated") ?: return@launch

            val presentation = MahjongScoringStickPresentation(
                tableId = gameId,
                tableLocation = resolved.location,
                tableFacing = resolved.facing,
                dealerSeatIndex = dealerSeatIndex,
                stickCount = stickCount,
            )
            val result = scoringStickPresenter.present(presentation)
            logger.debug("publishScoringSticksUpdated gameId={} present() result={}", gameId, result)
        }
    }

    /**
     * 一般回合動作（捨牌、摸牌、鳴牌）呼叫，沒有動畫可等，直接同步呈現；開局/換局的初次發牌改走
     * [publishInitialDealAnimation]，不會呼叫這個方法。
     */
    override fun publishPlayerAreaUpdated(
        gameId: Uuid,
        seatIndex: Int,
        standingTileIds: List<Uuid>,
        drawnTileId: Uuid?,
        melds: List<MeldPresentation>,
        comboStickCount: Int,
    ) {
        logger.debug(
            "publishPlayerAreaUpdated gameId={} seatIndex={} standingTileCount={} drawnTileId={} meldCount={} comboStickCount={}",
            gameId,
            seatIndex,
            standingTileIds.size,
            drawnTileId,
            melds.size,
            comboStickCount,
        )
        if (serverHolder.current() == null) {
            logger.warn("publishPlayerAreaUpdated gameId={} skipped: no active server", gameId)
            return
        }
        presentPlayerArea(gameId, seatIndex, standingTileIds, drawnTileId, melds, comboStickCount)
    }

    /**
     * 初次發牌要等牌牆＋擲骰動畫都播完才觸發（跟牌牆的王牌分離同一個時機點），不能在骰子還在動畫時
     * 就直接讓手牌出現——不像牌牆／王牌是「先生成、之後才移動」，手牌是整個延遲到那個時間點才真正
     * 呼叫 [playerAreaPresenter.presentInitialDeal]，之前完全不存在任何 entity；等待時長跟
     * [publishDiceRoll] 同一套 [wallDropTicksByTable] 機制，涵蓋牌牆掉落動畫時長。
     *
     * [busyTracker] 的標記額外加上 [OPENING_SEQUENCE_EXTRA_VIEWING_TICKS] 這段緩衝，並涵蓋發牌動畫
     * 本身的時長（[MahjongTileTableLayout.dealAnimationTicks]）——這是「建牌 → 擲骰開門 → 分牌」整個
     * 開局節奏裡最後一步觸發的呼叫（[StartGameUseCase]／[AdvanceRoundUseCase] 呼叫順序固定在
     * `publishWallStructure`／`publishDiceRoll` 之後），所以由這裡負責把桌子的忙碌時長從擲骰動畫
     * 長度，延長到涵蓋王牌分離／發牌動畫／寶牌翻開整段開局呈現，讓莊家的強制自動摸牌
     * （[GameFlowCoordinator.driveAutomatedPlayers]）確實等到玩家看得到手牌、發牌動畫都播完後才開始，
     * 不是單純等骰子動畫播完就搶跑。
     *
     * [MahjongTileTableLayout.dealAnimationTicks] 吃的是「總抓取次數」，不是單純的 `dealBatchSizes.size`
     * ——[playerAreaPresenter.presentInitialDeal] 是每一輪依序輪流對每個座位各抓一次（見該方法 KDoc），
     * 總抓取次數是「輪數 × 座位數」，這裡要用同一套算法才能對得上實際動畫時長。
     */
    override fun publishInitialDealAnimation(
        gameId: Uuid,
        handTileIdsBySeatIndex: Map<Int, List<Uuid>>,
        dealerSeatIndex: Int,
        comboStickCount: Int,
        dealBatchSizes: List<Int>,
        diceCount: Int,
    ) {
        logger.debug(
            "publishInitialDealAnimation gameId={} seatCount={} dealerSeatIndex={} comboStickCount={} dealBatchSizes={} diceCount={}",
            gameId,
            handTileIdsBySeatIndex.size,
            dealerSeatIndex,
            comboStickCount,
            dealBatchSizes,
            diceCount,
        )
        if (serverHolder.current() == null) {
            logger.warn("publishInitialDealAnimation gameId={} skipped: no active server", gameId)
            return
        }
        val wallDropTicks = wallDropTicksByTable[gameId] ?: 0
        val diceTicks = if (diceCount > 0) MahjongDiceTableLayout.totalAnimationTicks(diceCount) else 0
        val delayTicks = wallDropTicks + diceTicks
        val totalDealTurnCount = dealBatchSizes.size * handTileIdsBySeatIndex.size
        busyTracker.markBusyFor(gameId, delayTicks + MahjongTileTableLayout.dealAnimationTicks(totalDealTurnCount) + OPENING_SEQUENCE_EXTRA_VIEWING_TICKS)
        tickClock.scheduleAfter(delayTicks * MILLIS_PER_TICK) {
            scope.launch(dispatchers.main) {
                val resolved = resolveTableContext(gameId, "publishInitialDealAnimation") ?: return@launch

                val presentation = MahjongInitialDealPresentation(
                    tableId = gameId,
                    tableLocation = resolved.location,
                    tableFacing = resolved.facing,
                    handTileIdsBySeatIndex = handTileIdsBySeatIndex,
                    dealerSeatIndex = dealerSeatIndex,
                    comboStickCount = comboStickCount,
                    dealBatchSizes = dealBatchSizes,
                )
                val result = playerAreaPresenter.presentInitialDeal(presentation)
                logger.debug("publishInitialDealAnimation gameId={} presentInitialDeal() result={}", gameId, result)
            }
        }
    }

    /** 實際呼叫 [playerAreaPresenter]，跟 [publishDiceRoll] 同理丟回伺服器主執行緒執行。 */
    private fun presentPlayerArea(
        gameId: Uuid,
        seatIndex: Int,
        standingTileIds: List<Uuid>,
        drawnTileId: Uuid?,
        melds: List<MeldPresentation>,
        comboStickCount: Int,
    ) {
        scope.launch(dispatchers.main) {
            val resolved = resolveTableContext(gameId, "publishPlayerAreaUpdated") ?: return@launch

            val presentation = MahjongPlayerAreaPresentation(
                tableId = gameId,
                tableLocation = resolved.location,
                tableFacing = resolved.facing,
                seatIndex = seatIndex,
                standingTileIds = standingTileIds,
                drawnTileId = drawnTileId,
                melds = melds.map {
                    MahjongMeldTileGroup(it.type, it.tileIds, it.calledTileId, it.sourceDirection, it.allTilesFaceDown)
                },
                comboStickCount = comboStickCount,
            )
            val result = playerAreaPresenter.present(presentation)
            logger.debug("publishPlayerAreaUpdated gameId={} present() result={}", gameId, result)
        }
    }

    /**
     * 清除整桌所有玩家的手牌/摸牌位/副露/積棒呈現——回房間等清空情境使用（見 `ReturnToRoomUseCase`），
     * 沒有座位分組資料可傳，直接呼叫 [playerAreaPresenter]／[scoringStickPresenter] 各自的 `clear()`
     * （以 `managedTableId` 範圍搜尋清除，不需要逐座位資料）。
     */
    override fun clearPlayerAreas(gameId: Uuid) {
        logger.debug("clearPlayerAreas gameId={}", gameId)
        if (serverHolder.current() == null) {
            logger.warn("clearPlayerAreas gameId={} skipped: no active server", gameId)
            return
        }
        scope.launch(dispatchers.main) {
            val location = tableLocationRegistry.get(gameId)?.location
            if (location == null) {
                logger.warn("clearPlayerAreas gameId={} skipped: no known table location", gameId)
                return@launch
            }
            val removedTileCount = playerAreaPresenter.clear(gameId, location)
            val removedStickCount = scoringStickPresenter.clear(gameId, location)
            logger.debug(
                "clearPlayerAreas gameId={} removedTileCount={} removedStickCount={}",
                gameId,
                removedTileCount,
                removedStickCount,
            )
        }
    }

    /** 跟 [publishDiceRoll] 同理，座位傳送也需要碰觸世界／entity，一併丟回伺服器主執行緒執行。 */
    override fun publishGameStarted(gameId: Uuid, seatedPlayerIds: List<Uuid>) {
        if (serverHolder.current() == null) return
        scope.launch(dispatchers.main) { seatingPresenter.present(gameId, seatedPlayerIds) }
    }

    /** 理由同 [publishDiscardPileUpdated]：一般回合動作，不需要 [busyTracker] 或延遲，直接同步呈現。 */
    override fun publishDiscardPileUpdated(
        gameId: Uuid,
        seatIndex: Int,
        discardTileIds: List<Uuid>,
        sidewaysMarkedTileId: Uuid?,
    ) {
        logger.debug(
            "publishDiscardPileUpdated gameId={} seatIndex={} tileCount={} sidewaysMarkedTileId={}",
            gameId,
            seatIndex,
            discardTileIds.size,
            sidewaysMarkedTileId,
        )
        if (serverHolder.current() == null) {
            logger.warn("publishDiscardPileUpdated gameId={} skipped: no active server", gameId)
            return
        }
        scope.launch(dispatchers.main) {
            val resolved = resolveTableContext(gameId, "publishDiscardPileUpdated") ?: return@launch

            val presentation = MahjongDiscardPresentation(
                tableId = gameId,
                tableLocation = resolved.location,
                tableFacing = resolved.facing,
                seatIndex = seatIndex,
                discardTileIds = discardTileIds,
                sidewaysMarkedTileId = sidewaysMarkedTileId,
            )
            val result = discardPresenter.present(presentation)
            logger.debug("publishDiscardPileUpdated gameId={} present() result={}", gameId, result)
        }
    }

    /** 由版本無關 dimension ID 取得目前 server session 的世界。 */
    private fun resolveWorld(location: TableLocation): ServerWorld? {
        val identifier = Identifier.tryParse(location.dimensionId) ?: return null
        val worldKey = RegistryKey.of(RegistryKeys.WORLD, identifier)
        return serverHolder.current()?.getWorld(worldKey)
    }

    /**
     * 解析 [gameId] 目前的桌子位置與世界水平朝向，供各 `publish*` 方法在 [scope.launch] 裡呼叫——把
     * 「查桌子位置 → 解析世界 → 讀 controller 方塊狀態 → 確認有 `HORIZONTAL_FACING` → 換算朝向」這一
     * 串六個方法都重複的樣板抽成一個共用 helper。任何一步失敗都回傳 `null` 並記一則 DEBUG log（帶
     * [methodName] 方便追蹤是哪個呼叫端放棄的），呼叫端收到 `null` 直接 `return@launch`，比照本介面
     * best-effort 的既有慣例。
     */
    private fun resolveTableContext(gameId: Uuid, methodName: String): ResolvedTableContext? {
        val location = tableLocationRegistry.get(gameId)?.location
        if (location == null) {
            logger.warn("$methodName gameId={} skipped: no known table location", gameId)
            return null
        }
        val world = resolveWorld(location)
        if (world == null) {
            logger.warn("$methodName gameId={} skipped: could not resolve world for location={}", gameId, location)
            return null
        }
        val controllerPos = BlockPos(location.x, location.y, location.z)
        val state = world.getBlockState(controllerPos)
        if (!state.contains(Properties.HORIZONTAL_FACING)) {
            logger.warn(
                "$methodName gameId={} skipped: block at {} has no HORIZONTAL_FACING (state={})",
                gameId,
                controllerPos,
                state,
            )
            return null
        }
        val facing = state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing()
        return ResolvedTableContext(world, location, facing)
    }

    /** [resolveTableContext] 的解析結果。 */
    private data class ResolvedTableContext(
        val world: ServerWorld,
        val location: TableLocation,
        val facing: MahjongTableFacing,
    )

    /** 正式擲骰／開局呈現使用的固定參數。 */
    private companion object {
        /** 把局數換算成 `rollSequence` 時的本場數進位基準；本場數在真實對局中不可能達到此量級。 */
        const val ROLL_SEQUENCE_ROUND_MULTIPLIER: Long = 1_000_000L

        /**
         * 手牌落地／王牌分離都完成後，額外多留給玩家看清楚開局結果的 tick 數，才讓莊家的強制自動摸牌
         * 開始——量級比照 [DiceRollAnimationSpec.EXTRA_VIEWING_TICKS]。
         */
        const val OPENING_SEQUENCE_EXTRA_VIEWING_TICKS: Int = 25

        /** Minecraft 正常運行時每個 tick 對應的毫秒數（20 TPS），換算 [FabricTickMonotonicClock.scheduleAfter] 的延遲毫秒數用。 */
        const val MILLIS_PER_TICK: Long = 50L
    }
}
