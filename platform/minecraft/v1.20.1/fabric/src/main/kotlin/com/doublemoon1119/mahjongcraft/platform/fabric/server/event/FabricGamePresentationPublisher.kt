package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
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
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.seatIndexToTableSide
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.MahjongSeatingPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDiscardPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDiscardPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDrawnTilePresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongHandTilesPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongHandTilesPresenter
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
import kotlin.random.Random
import kotlin.uuid.Uuid

/**
 * [GamePresentationPublisher] 的 Fabric 實作，薄分派層。
 *
 * @property seatingPresenter 開局座位傳送的實際呈現邏輯。
 * @property diceRollPresenter 正式擲骰的實際呈現邏輯。
 * @property tileWallPresenter 正式牌牆的實際呈現邏輯。
 * @property handTilesPresenter 正式手牌（含摸牌位）的實際呈現邏輯。
 * @property discardPresenter 正式牌河的實際呈現邏輯。
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
    private val handTilesPresenter: MahjongHandTilesPresenter,
    private val discardPresenter: MahjongDiscardPresenter,
    private val tableLocationRegistry: TableLocationRegistry,
    private val serverHolder: FabricServerHolder,
    private val busyTracker: TablePresentationBusyTracker,
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    private val tickClock: FabricTickMonotonicClock,
) : GamePresentationPublisher {
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

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
     * [busyTracker] 的標記刻意寫在方法最前面、同步執行，不是等 [scope.launch] 裡 `present()` 成功
     * 後才標記——呼叫端（`AdvanceRoundUseCase`／`StartGameUseCase`）呼叫完這個方法就會緊接著繼續走
     * 自己的自動連鎖（莊家自動摸牌、開始思考計時器），如果標記忙碌這件事本身也要等非同步的世界呈現
     * 跑完才發生，兩邊完全沒有因果關係、純粹看哪個先跑完，曾經真的看過自動連鎖贏過這個標記、玩家
     * 動畫都還沒播完就已經被叫著打牌。同步標記才能保證呼叫端往下走之前，忙碌狀態已經生效。代價是
     * 就算之後 [present] 真的失敗（例如桌子被拆掉），這桌還是會被錯誤標記忙碌一小段時間——比起
     * 每一次擲骰都有機會被搶跑，這個機率很低的邊界情況划算得多。
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
        val busyTicks = MahjongDiceTableLayout.totalAnimationTicks(dice.values.size)
        busyTracker.markBusyFor(gameId, busyTicks)
        scope.launch(dispatchers.main) {
            val location = tableLocationRegistry.get(gameId)?.location
            if (location == null) {
                logger.warn("publishDiceRoll gameId={} skipped: no known table location", gameId)
                return@launch
            }
            val world = resolveWorld(location)
            if (world == null) {
                logger.warn(
                    "publishDiceRoll gameId={} skipped: could not resolve world for location={}",
                    gameId,
                    location,
                )
                return@launch
            }
            val controllerPos = BlockPos(location.x, location.y, location.z)
            val state = world.getBlockState(controllerPos)
            if (!state.contains(Properties.HORIZONTAL_FACING)) {
                logger.warn(
                    "publishDiceRoll gameId={} skipped: block at {} has no HORIZONTAL_FACING (state={})",
                    gameId,
                    controllerPos,
                    state,
                )
                return@launch
            }
            val facing = state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing()

            val presentation = MahjongDiceRollPresentation(
                tableId = gameId,
                tableLocation = location,
                tableFacing = facing,
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

    /** 跟 [publishDiceRoll] 同理，牌牆呈現也需要碰觸世界／entity，一併丟回伺服器主執行緒執行。 */
    override fun publishWallStructure(
        gameId: Uuid,
        structure: Map<Uuid, TileWallPosition>,
        dealerSeatIndex: Int,
        deadWallTileIds: Set<Uuid>,
        diceCount: Int,
    ) {
        logger.debug(
            "publishWallStructure gameId={} tileCount={} dealerSeatIndex={} deadWallTileCount={} diceCount={}",
            gameId,
            structure.size,
            dealerSeatIndex,
            deadWallTileIds.size,
            diceCount,
        )
        if (serverHolder.current() == null) {
            logger.warn("publishWallStructure gameId={} skipped: no active server", gameId)
            return
        }
        scope.launch(dispatchers.main) {
            val location = tableLocationRegistry.get(gameId)?.location
            if (location == null) {
                logger.warn("publishWallStructure gameId={} skipped: no known table location", gameId)
                return@launch
            }
            val world = resolveWorld(location)
            if (world == null) {
                logger.warn(
                    "publishWallStructure gameId={} skipped: could not resolve world for location={}",
                    gameId,
                    location,
                )
                return@launch
            }
            val controllerPos = BlockPos(location.x, location.y, location.z)
            val state = world.getBlockState(controllerPos)
            if (!state.contains(Properties.HORIZONTAL_FACING)) {
                logger.warn(
                    "publishWallStructure gameId={} skipped: block at {} has no HORIZONTAL_FACING (state={})",
                    gameId,
                    controllerPos,
                    state,
                )
                return@launch
            }
            val facing = state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing()

            val presentation = MahjongTileWallPresentation(
                tableId = gameId,
                tableLocation = location,
                tableFacing = facing,
                dealerSeatIndex = dealerSeatIndex,
                structure = structure,
                deadWallTileIds = deadWallTileIds,
                diceCount = diceCount,
            )
            val result = tileWallPresenter.present(presentation)
            logger.debug("publishWallStructure gameId={} present() result={}", gameId, result)
        }
    }

    /**
     * 手牌落地要等擲骰動畫播完才觸發（跟牌牆的王牌分離同一個時機點），不能在骰子還在動畫時就直接讓
     * 手牌出現——不像牌牆／王牌是「先生成、之後才移動」，手牌是整個延遲到那個時間點才真正呼叫
     * [handTilesPresenter.present]，之前完全不存在任何 entity。
     *
     * [busyTracker] 的標記額外加上 [OPENING_SEQUENCE_EXTRA_VIEWING_TICKS] 這段緩衝——這是「建牌 →
     * 擲骰開門 → 分牌」整個開局節奏裡最後一步觸發的呼叫（[StartGameUseCase]／[AdvanceRoundUseCase]
     * 呼叫順序固定在 `publishWallStructure` 之後），所以由這裡負責把桌子的忙碌時長從單純的擲骰動畫
     * 長度，延長到涵蓋王牌分離／手牌落地／寶牌翻開整段開局呈現，讓莊家的強制自動摸牌（[GameFlowCoordinator.driveAutomatedPlayers]）
     * 確實等到玩家看得到手牌、王牌分離都完成後才開始，不是單純等骰子動畫播完就搶跑。沒有擲骰
     * （[diceCount] 為 `0`）時沒有動畫可等，直接同步呈現，不排定延遲、不額外標記忙碌。
     */
    override fun publishHandTiles(gameId: Uuid, handsBySeatIndex: Map<Int, List<Uuid>>, diceCount: Int) {
        logger.debug("publishHandTiles gameId={} seatCount={} diceCount={}", gameId, handsBySeatIndex.size, diceCount)
        if (serverHolder.current() == null) {
            logger.warn("publishHandTiles gameId={} skipped: no active server", gameId)
            return
        }
        if (diceCount <= 0) {
            presentHandTiles(gameId, handsBySeatIndex)
            return
        }
        val delayTicks = MahjongDiceTableLayout.totalAnimationTicks(diceCount)
        busyTracker.markBusyFor(gameId, delayTicks + OPENING_SEQUENCE_EXTRA_VIEWING_TICKS)
        tickClock.scheduleAfter(delayTicks * MILLIS_PER_TICK) {
            presentHandTiles(gameId, handsBySeatIndex)
        }
    }

    /** 實際呼叫 [handTilesPresenter]，跟 [publishDiceRoll] 同理丟回伺服器主執行緒執行。 */
    private fun presentHandTiles(gameId: Uuid, handsBySeatIndex: Map<Int, List<Uuid>>) {
        scope.launch(dispatchers.main) {
            val location = tableLocationRegistry.get(gameId)?.location
            if (location == null) {
                logger.warn("publishHandTiles gameId={} skipped: no known table location", gameId)
                return@launch
            }
            val world = resolveWorld(location)
            if (world == null) {
                logger.warn(
                    "publishHandTiles gameId={} skipped: could not resolve world for location={}",
                    gameId,
                    location,
                )
                return@launch
            }
            val controllerPos = BlockPos(location.x, location.y, location.z)
            val state = world.getBlockState(controllerPos)
            if (!state.contains(Properties.HORIZONTAL_FACING)) {
                logger.warn(
                    "publishHandTiles gameId={} skipped: block at {} has no HORIZONTAL_FACING (state={})",
                    gameId,
                    controllerPos,
                    state,
                )
                return@launch
            }
            val facing = state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing()

            val presentation = MahjongHandTilesPresentation(
                tableId = gameId,
                tableLocation = location,
                tableFacing = facing,
                handsBySeatIndex = handsBySeatIndex,
            )
            val result = handTilesPresenter.present(presentation)
            logger.debug("publishHandTiles gameId={} present() result={}", gameId, result)
        }
    }

    /** 跟 [publishDiceRoll] 同理，座位傳送也需要碰觸世界／entity，一併丟回伺服器主執行緒執行。 */
    override fun publishGameStarted(gameId: Uuid, seatedPlayerIds: List<Uuid>) {
        if (serverHolder.current() == null) return
        scope.launch(dispatchers.main) { seatingPresenter.present(gameId, seatedPlayerIds) }
    }

    /**
     * 摸牌/丟牌是整場遊戲隨時都會發生的一般回合動作，不是開局限定的一次性動畫，不需要
     * [busyTracker] 標記，也不需要延遲——直接同步呈現。跟 [publishDiceRoll] 同理，世界／entity 存取
     * 一併丟回伺服器主執行緒執行。
     */
    override fun publishTileDrawn(gameId: Uuid, seatIndex: Int, standingTileCount: Int, drawnTileId: Uuid?) {
        logger.debug(
            "publishTileDrawn gameId={} seatIndex={} standingTileCount={} drawnTileId={}",
            gameId,
            seatIndex,
            standingTileCount,
            drawnTileId,
        )
        if (serverHolder.current() == null) {
            logger.warn("publishTileDrawn gameId={} skipped: no active server", gameId)
            return
        }
        scope.launch(dispatchers.main) {
            val location = tableLocationRegistry.get(gameId)?.location
            if (location == null) {
                logger.warn("publishTileDrawn gameId={} skipped: no known table location", gameId)
                return@launch
            }
            val world = resolveWorld(location)
            if (world == null) {
                logger.warn(
                    "publishTileDrawn gameId={} skipped: could not resolve world for location={}",
                    gameId,
                    location,
                )
                return@launch
            }
            val controllerPos = BlockPos(location.x, location.y, location.z)
            val state = world.getBlockState(controllerPos)
            if (!state.contains(Properties.HORIZONTAL_FACING)) {
                logger.warn(
                    "publishTileDrawn gameId={} skipped: block at {} has no HORIZONTAL_FACING (state={})",
                    gameId,
                    controllerPos,
                    state,
                )
                return@launch
            }
            val facing = state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing()

            val presentation = MahjongDrawnTilePresentation(
                tableId = gameId,
                tableLocation = location,
                tableFacing = facing,
                seatIndex = seatIndex,
                standingTileCount = standingTileCount,
                drawnTileId = drawnTileId,
            )
            val result = handTilesPresenter.presentDrawnTile(presentation)
            logger.debug("publishTileDrawn gameId={} presentDrawnTile() result={}", gameId, result)
        }
    }

    /** 理由同 [publishTileDrawn]：一般回合動作，不需要 [busyTracker] 或延遲，直接同步呈現。 */
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
            val location = tableLocationRegistry.get(gameId)?.location
            if (location == null) {
                logger.warn("publishDiscardPileUpdated gameId={} skipped: no known table location", gameId)
                return@launch
            }
            val world = resolveWorld(location)
            if (world == null) {
                logger.warn(
                    "publishDiscardPileUpdated gameId={} skipped: could not resolve world for location={}",
                    gameId,
                    location,
                )
                return@launch
            }
            val controllerPos = BlockPos(location.x, location.y, location.z)
            val state = world.getBlockState(controllerPos)
            if (!state.contains(Properties.HORIZONTAL_FACING)) {
                logger.warn(
                    "publishDiscardPileUpdated gameId={} skipped: block at {} has no HORIZONTAL_FACING (state={})",
                    gameId,
                    controllerPos,
                    state,
                )
                return@launch
            }
            val facing = state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing()

            val presentation = MahjongDiscardPresentation(
                tableId = gameId,
                tableLocation = location,
                tableFacing = facing,
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
