package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceRollAnimationSpec
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDicePresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.seatIndexToTableSide
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.MahjongSeatingPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
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
 * @property tableLocationRegistry 麻將桌最後已知位置索引。
 * @property serverHolder 目前運行中的 server，供世界／方塊狀態查詢使用。
 * @property busyTracker 呈現動畫播放期間標記該桌暫時忙碌，供輸入分派入口與自動操作心跳擋下操作。
 * @property scope 承接世界／方塊狀態查詢需要切回伺服器主執行緒的工作。
 * @property dispatchers 切回伺服器主執行緒用的 dispatcher。
 */
@Single(binds = [GamePresentationPublisher::class])
class FabricGamePresentationPublisher(
    private val seatingPresenter: MahjongSeatingPresenter,
    private val diceRollPresenter: MahjongDiceRollPresenter,
    private val tileWallPresenter: MahjongTileWallPresenter,
    private val tableLocationRegistry: TableLocationRegistry,
    private val serverHolder: FabricServerHolder,
    private val busyTracker: TablePresentationBusyTracker,
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
) : GamePresentationPublisher {
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    /**
     * 查無桌子位置、世界、或 controller 目前不是合法麻將桌方塊時直接放棄——比照本介面 best-effort
     * 的既有慣例，不拋例外、不影響呼叫端的權威狀態變更；每個放棄點都留一則 DEBUG log，方便排查
     * 「規則有配置骰子、但畫面上完全沒出現」這種靜默失敗。
     *
     * 世界／方塊狀態查詢一定要丟回伺服器主執行緒才能執行——`GameFlowCoordinator` 的呼叫鏈跑在
     * `Dispatchers.Default`（見 [FabricAppCoroutineScope][com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.FabricAppCoroutineScope]），
     * 直接在這裡讀 `world.getBlockState`／`getBlockEntity` 會在非主執行緒上跟伺服器自己的 tick
     * 並發存取同一份 chunk／block entity 資料，讀到不一致或直接讀不到，結果就是靜默呈現
     * `TABLE_NOT_FOUND`——這是曾經在這裡踩過、log 追出來的真實問題，不是假設。用 [dispatchers] 的
     * `main`（[ServerThreadCoroutineDispatcher][com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.ServerThreadCoroutineDispatcher]）
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
    override fun publishDiceRoll(gameId: Uuid, dice: DiceRollResult, dealerSeatIndex: Int, roundNumber: Int, comboCount: Int) {
        logger.debug("publishDiceRoll gameId={} dice={} dealerSeatIndex={} roundNumber={} comboCount={}", gameId, dice.values, dealerSeatIndex, roundNumber, comboCount)
        if (serverHolder.current() == null) {
            logger.warn("publishDiceRoll gameId={} skipped: no active server", gameId)
            return
        }
        val busyTicks = MahjongDiceTableLayout.maxStartDelayTicks(dice.values.size) +
            DiceRollAnimationSpec.DEFAULT_DURATION_TICKS +
            DiceRollAnimationSpec.EXTRA_VIEWING_TICKS
        busyTracker.markBusyFor(gameId, busyTicks)
        scope.launch(dispatchers.main) {
            val location = tableLocationRegistry.get(gameId)?.location
            if (location == null) {
                logger.warn("publishDiceRoll gameId={} skipped: no known table location", gameId)
                return@launch
            }
            val world = resolveWorld(location)
            if (world == null) {
                logger.warn("publishDiceRoll gameId={} skipped: could not resolve world for location={}", gameId, location)
                return@launch
            }
            val controllerPos = BlockPos(location.x, location.y, location.z)
            val state = world.getBlockState(controllerPos)
            if (!state.contains(Properties.HORIZONTAL_FACING)) {
                logger.warn("publishDiceRoll gameId={} skipped: block at {} has no HORIZONTAL_FACING (state={})", gameId, controllerPos, state)
                return@launch
            }
            val facing = state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing()

            val presentation = MahjongDiceRollPresentation(
                tableId = gameId,
                tableLocation = location,
                tableFacing = facing,
                throwSide = seatIndexToTableSide(dealerSeatIndex),
                rollSequence = roundNumber.toLong() * ROLL_SEQUENCE_ROUND_MULTIPLIER + comboCount,
                dice = dice.values.map { point -> MahjongDicePresentation(point = point, animationSeed = Random.nextLong()) },
            )
            val result = diceRollPresenter.present(presentation)
            logger.debug("publishDiceRoll gameId={} present() result={}", gameId, result)
        }
    }

    /** 跟 [publishDiceRoll] 同理，牌牆呈現也需要碰觸世界／entity，一併丟回伺服器主執行緒執行。 */
    override fun publishWallStructure(gameId: Uuid, structure: Map<Uuid, TileWallPosition>, dealerSeatIndex: Int) {
        logger.debug("publishWallStructure gameId={} tileCount={} dealerSeatIndex={}", gameId, structure.size, dealerSeatIndex)
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
                logger.warn("publishWallStructure gameId={} skipped: could not resolve world for location={}", gameId, location)
                return@launch
            }
            val controllerPos = BlockPos(location.x, location.y, location.z)
            val state = world.getBlockState(controllerPos)
            if (!state.contains(Properties.HORIZONTAL_FACING)) {
                logger.warn("publishWallStructure gameId={} skipped: block at {} has no HORIZONTAL_FACING (state={})", gameId, controllerPos, state)
                return@launch
            }
            val facing = state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing()

            val presentation = MahjongTileWallPresentation(
                tableId = gameId,
                tableLocation = location,
                tableFacing = facing,
                dealerSeatIndex = dealerSeatIndex,
                structure = structure,
            )
            val result = tileWallPresenter.present(presentation)
            logger.debug("publishWallStructure gameId={} present() result={}", gameId, result)
        }
    }

    /** 跟 [publishDiceRoll] 同理，座位傳送也需要碰觸世界／entity，一併丟回伺服器主執行緒執行。 */
    override fun publishGameStarted(gameId: Uuid, seatedPlayerIds: List<Uuid>) {
        if (serverHolder.current() == null) return
        scope.launch(dispatchers.main) { seatingPresenter.present(gameId, seatedPlayerIds) }
    }

    /** 由版本無關 dimension ID 取得目前 server session 的世界。 */
    private fun resolveWorld(location: TableLocation): ServerWorld? {
        val identifier = Identifier.tryParse(location.dimensionId) ?: return null
        val worldKey = RegistryKey.of(RegistryKeys.WORLD, identifier)
        return serverHolder.current()?.getWorld(worldKey)
    }

    /** 正式擲骰呈現使用的固定參數。 */
    private companion object {
        /** 把局數換算成 `rollSequence` 時的本場數進位基準；本場數在真實對局中不可能達到此量級。 */
        const val ROLL_SEQUENCE_ROUND_MULTIPLIER: Long = 1_000_000L
    }
}
