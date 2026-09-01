package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.MatchSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.MeldPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.RoundInfoLine
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.FabricAppCoroutineScope
import com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.ServerThreadCoroutineDispatcher
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.DebugWinShowcaseOverride
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricExhaustiveDrawSettlementPresentationScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricMatchSettlementPresentationScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricWinCelebrationEffectScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricWinCelebrationShowcaseScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricWinSettlementPresentationScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.PersistentTableOverlayCoordinator
import com.doublemoon1119.mahjongcraft.platform.fabric.server.tile.TileAnimationSteps
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.WinPresentationCleanupPlan
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDicePresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.seatIndexToTableSide
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.aiPlayerDisplayName
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.MahjongSeatingPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongRiichiStickPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongRiichiStickPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongPlayerInfoPresentationFactory
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongPlayerInfoPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongRoundInfoPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongRoundInfoPresenter
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
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongWinCelebrationPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.toAssetKey
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
import kotlin.uuid.toJavaUuid

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
 * @property riichiStickPresenter 正式立直棒的實際呈現邏輯，生命週期綁在立直宣告，見
 *   [MahjongRiichiStickPresenter] KDoc。
 * @property roundInfoPresenter 桌面中央局況顯示的實際呈現邏輯。
 * @property tableLocationRegistry 麻將桌最後已知位置索引。
 * @property serverHolder 目前運行中的 server，供世界／方塊狀態查詢使用。
 * @property busyTracker 查詢／標記該桌是否呈現動畫播放中，供輸入分派入口與自動操作心跳擋下操作，見
 *   [TablePresentationBusyTracker] KDoc。
 * @property gameRepository 讀取贏家目前的權威手牌／副露內容，供 [publishWinCelebration] 算出強制理牌
 *   重排的目標順序——這個介面本身刻意不攜帶完整手牌內容（見 [GamePresentationPublisher.publishWinCelebration]
 *   KDoc），只讀不寫，不違反本類別 best-effort、不影響權威狀態的既有慣例。
 * @property moduleRegistry 解析對局採用的規則模組，取得 [publishWinCelebration] 算牌序需要的 `tileOrder`。
 * @property effectScheduler 胡牌慶祝演出降臨特效（粒子聚合光柱）的排程器。
 * @property scope 承接世界／方塊狀態查詢需要切回伺服器主執行緒的工作。
 * @property dispatchers 切回伺服器主執行緒用的 dispatcher。
 */
@Single(binds = [GamePresentationPublisher::class])
class FabricGamePresentationPublisher(
    private val seatingPresenter: MahjongSeatingPresenter,
    private val diceRollPresenter: MahjongDiceRollPresenter,
    private val tileWallPresenter: MahjongTileWallPresenter,
    private val playerAreaPresenter: MahjongPlayerAreaPresenter,
    private val discardPresenter: MahjongDiscardPresenter,
    private val scoringStickPresenter: MahjongScoringStickPresenter,
    private val riichiStickPresenter: MahjongRiichiStickPresenter,
    private val roundInfoPresenter: MahjongRoundInfoPresenter,
    private val playerInfoPresenter: MahjongPlayerInfoPresenter,
    private val tableLocationRegistry: TableLocationRegistry,
    private val serverHolder: FabricServerHolder,
    private val busyTracker: TablePresentationBusyTracker,
    private val debugWinShowcaseOverride: DebugWinShowcaseOverride,
    private val debugWinRoundContinuationState: DebugWinRoundContinuationState,
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val effectScheduler: FabricWinCelebrationEffectScheduler,
    private val showcaseScheduler: FabricWinCelebrationShowcaseScheduler,
    private val exhaustiveDrawSettlementScheduler: FabricExhaustiveDrawSettlementPresentationScheduler,
    private val winSettlementScheduler: FabricWinSettlementPresentationScheduler,
    private val matchSettlementScheduler: FabricMatchSettlementPresentationScheduler,
    private val tableOverlayCoordinator: PersistentTableOverlayCoordinator,
    private val tileAssetRegistry: MinecraftTileAssetRegistry,
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
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

    override fun publishExhaustiveDrawSettlement(gameId: Uuid, request: ExhaustiveDrawSettlementPresentationRequest) {
        logger.debug(
            "Exhaustive draw settlement gameId={} reason={} players={}",
            gameId,
            request.reasonId,
            request.players.map { player ->
                val ranking = player.ranking
                mapOf(
                    "playerId" to ranking.playerId,
                    "seatIndex" to ranking.seatIndex,
                    "previousScore" to ranking.previousScore,
                    "currentScore" to ranking.currentScore,
                    "previousRank" to ranking.previousRank,
                    "currentRank" to ranking.currentRank,
                    "handPresentation" to player.handPresentation,
                    "waitingTiles" to player.waitingTiles.map(Any::toString),
                )
            },
        )
        busyTracker.markPending(gameId)
        scope.launch(dispatchers.main) {
            try {
                val resolved = resolveTableContext(gameId, "publishExhaustiveDrawSettlement") ?: return@launch
                val waitingAssets = request.players.associate { player ->
                    player.ranking.seatIndex to player.waitingTiles.map { it.toAssetKey(tileAssetRegistry) }
                }
                val tableState = gameRepository.getTableState(gameId)
                val revealedAssets = request.players.flatMap { it.revealedHandTileIds }.distinct().mapNotNull { tileId ->
                    tableState?.findTile(tileId)?.let { tileId to it.tile.toAssetKey(tileAssetRegistry) }
                }.toMap()
                val endGameTime = exhaustiveDrawSettlementScheduler.schedule(
                    world = resolved.world,
                    tableId = gameId,
                    controllerPos = BlockPos(resolved.location.x, resolved.location.y, resolved.location.z),
                    placement = MahjongTileTableLayout.showcaseStagePlacement(
                        resolved.location.x,
                        resolved.location.y,
                        resolved.location.z,
                    ),
                    request = request,
                    waitingTileAssetsBySeat = waitingAssets,
                    revealedTileAssetsById = revealedAssets,
                )
                if (endGameTime == null) {
                    logger.warn("publishExhaustiveDrawSettlement gameId={} skipped: stage spawn failed", gameId)
                } else {
                    resolved.table.extendPresentationUntil(endGameTime)
                    logger.debug("Exhaustive draw settlement presentation created gameId={} endGameTime={}", gameId, endGameTime)
                }
            } finally {
                busyTracker.clearPending(gameId)
            }
        }
    }

    override fun publishMatchSettlement(gameId: Uuid, request: MatchSettlementPresentationRequest) {
        logger.debug(
            "Match settlement gameId={} template={} players={}",
            gameId,
            request.templateKey,
            request.players.map { player ->
                mapOf(
                    "playerId" to player.playerId,
                    "seatIndex" to player.seatIndex,
                    "initialSeatIndex" to player.initialSeatIndex,
                    "finalScore" to player.finalScore,
                    "finalRank" to player.finalRank,
                )
            },
        )
        // 對局結束：清掉這桌的開發用中途胡牌設定與尚未用掉的 showcase 覆寫，不讓它們跨到下一場。
        // 兩者都以 tableId 為鍵，而本專案的 gameId 就是該桌的 tableId（整個呈現層都以它查桌）。
        debugWinRoundContinuationState.clear(gameId)
        debugWinShowcaseOverride.clear(gameId)
        if (serverHolder.current() == null) return
        busyTracker.markPending(gameId)
        scope.launch(dispatchers.main) {
            try {
                val resolved = resolveTableContext(gameId, "publishMatchSettlement") ?: return@launch
                val end = matchSettlementScheduler.schedule(
                    world = resolved.world,
                    tableId = gameId,
                    controllerPos = BlockPos(resolved.location.x, resolved.location.y, resolved.location.z),
                    placement = MahjongTileTableLayout.showcaseStagePlacement(resolved.location.x, resolved.location.y, resolved.location.z),
                    earliestStartGameTime = resolved.table.presentationBusyUntilGameTime,
                    request = request,
                )
                if (end != null) resolved.table.extendPresentationUntil(end)
            } catch (cause: Exception) {
                logger.warn("Failed to publish match settlement for gameId={}", gameId, cause)
            } finally {
                busyTracker.clearPending(gameId)
            }
        }
    }

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
     * 呈現本身（[diceRollPresenter.present]）不再延遲呼叫——骰子 entity 立刻生成，[wallDropTicksByTable]
     * 記錄的牌牆掉落動畫時長改成折算進 [MahjongDiceRollPresentation.extraLeadDelayTicks]，變成每顆
     * 骰子自己動畫佇列最前面的一個等待 step（`MahjongDiceEntity.startRoll`），理由見
     * `AnimatedMahjongEntity` KDoc：延遲呼叫這個方法本身沒辦法撐過伺服器重啟，只有掛在 entity 自己
     * 身上的佇列才可以。[busyTracker] 也已經改成直接查詢桌上管理中 entity 是否還在動畫佇列裡，不需要
     * 再手動標記忙碌時長，見 [TablePresentationBusyTracker] KDoc。
     */
    override fun publishDiceRoll(
        gameId: Uuid,
        dice: DiceRollResult,
        dealerSeatIndex: Int,
        roundNumber: Int,
        comboCount: Int,
    ) {
        if (serverHolder.current() == null) {
            logger.warn("publishDiceRoll gameId={} skipped: no active server", gameId)
            return
        }
        val wallDropTicks = wallDropTicksByTable[gameId] ?: 0
        busyTracker.markPending(gameId)
        scope.launch(dispatchers.main) {
            try {
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
                    extraLeadDelayTicks = wallDropTicks,
                )
                diceRollPresenter.present(presentation)
            } finally {
                busyTracker.clearPending(gameId)
            }
        }
    }

    /**
     * 跟 [publishDiceRoll] 同理，牌牆呈現也需要碰觸世界／entity，一併丟回伺服器主執行緒執行。
     *
     * `stacksPerSide` 的算法跟 [FabricMahjongTileWallPresenter.present] 內部完全一致（取 `side == 0`
     * 的最大 `stack + 1`），兩處必須同步，否則算出來的動畫時長會跟實際動畫時長脫鉤。
     *
     * 算出來的動畫時長寫進 [wallDropTicksByTable]，供緊接著呼叫的 [publishDiceRoll]／
     * [publishInitialDealAnimation] 讀取，折算進擲骰／發牌動畫每個 entity 自己動畫佇列最前面的等待
     * step，讓它們延遲到牌牆完全落地才真正開始播放；呼叫端固定先呼叫這個方法才呼叫另外兩者，見該欄位
     * KDoc。玩家操作／自動操作心跳不會搶在牌牆落地之前執行，現在是靠 [busyTracker] 直接查詢桌上
     * entity 是否還在動畫佇列裡，不需要另外手動標記忙碌時長，見 [TablePresentationBusyTracker] KDoc。
     */
    override fun publishWallStructure(
        gameId: Uuid,
        structure: Map<Uuid, TileWallPosition>,
        dealerSeatIndex: Int,
        deadWallTileIds: Set<Uuid>,
        diceCount: Int,
        revealedTileIds: Set<Uuid>,
    ) {
        if (serverHolder.current() == null) {
            logger.warn("publishWallStructure gameId={} skipped: no active server", gameId)
            return
        }
        val stacksPerSide = structure.values.filter { position -> position.side == 0 }.maxOfOrNull { position -> position.stack + 1 } ?: 0
        val wallDropTicks = MahjongTileTableLayout.wallDropAnimationTicks(stacksPerSide)
        wallDropTicksByTable[gameId] = wallDropTicks
        busyTracker.markPending(gameId)
        scope.launch(dispatchers.main) {
            try {
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
                tileWallPresenter.present(presentation)
            } finally {
                busyTracker.clearPending(gameId)
            }
        }
    }

    /**
     * 一般回合動作，不需要 [busyTracker] 或延遲，直接同步呈現；跟 [publishDiceRoll] 同理，世界／entity
     * 存取一併丟回伺服器主執行緒執行。
     */
    override fun publishDeadWallRevealUpdated(gameId: Uuid, revealedTileIds: Set<Uuid>) {
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
            tileWallPresenter.revealDeadWallTiles(gameId, location, revealedTileIds)
        }
    }

    /**
     * 積棒跟牌牆同一個時機點觸發（呼叫端緊接在 [publishWallStructure] 之後呼叫，見
     * [MahjongScoringStickPresenter] KDoc）——每回合結束（換局）就重新生成一批，不是每次打牌/摸牌/
     * 鳴牌都觸發。一般回合動作不會呼叫這個方法，不需要 [busyTracker] 或延遲，直接同步呈現；跟
     * [publishDiceRoll] 同理，世界／entity 存取一併丟回伺服器主執行緒執行。
     */
    override fun publishScoringSticksUpdated(gameId: Uuid, dealerSeatIndex: Int, stickCount: Int) {
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
            scoringStickPresenter.present(presentation)
        }
    }

    /**
     * 立直棒綁在**立直宣告**的時間點觸發（呼叫端緊接在立直宣告成立、廣播事件之後呼叫，見
     * [MahjongRiichiStickPresenter] KDoc）——跟 [publishScoringSticksUpdated]（綁在牌牆生成）各自
     * 獨立觸發時機；不需要 [busyTracker] 或延遲，直接同步呈現，跟 [publishScoringSticksUpdated] 同理。
     */
    override fun publishRiichiSticksUpdated(
        gameId: Uuid,
        riichiSeatIndices: Set<Int>,
        dealerSeatIndex: Int,
        comboStickCount: Int,
        pooledStickCount: Int,
    ) {
        if (serverHolder.current() == null) {
            logger.warn("publishRiichiSticksUpdated gameId={} skipped: no active server", gameId)
            return
        }
        scope.launch(dispatchers.main) {
            val resolved = resolveTableContext(gameId, "publishRiichiSticksUpdated") ?: return@launch

            val presentation = MahjongRiichiStickPresentation(
                tableId = gameId,
                tableLocation = resolved.location,
                tableFacing = resolved.facing,
                riichiSeatIndices = riichiSeatIndices,
                dealerSeatIndex = dealerSeatIndex,
                comboStickCount = comboStickCount,
                pooledStickCount = pooledStickCount,
            )
            riichiStickPresenter.present(presentation)
        }
    }

    /**
     * 開局/換局（跟 [publishWallStructure] 同一批呼叫）跟每次摸牌都會觸發，不需要 [busyTracker] 或
     * 延遲——純文字更新是瞬間的；跟 [publishDiceRoll] 同理，世界／entity 存取一併丟回伺服器主執行緒
     * 執行。
     */
    override fun publishRoundInfoUpdated(gameId: Uuid, lines: List<RoundInfoLine>) {
        if (serverHolder.current() == null) {
            logger.warn("publishRoundInfoUpdated gameId={} skipped: no active server", gameId)
            return
        }
        scope.launch(dispatchers.main) {
            val resolved = resolveTableContext(gameId, "publishRoundInfoUpdated") ?: return@launch

            val presentation = MahjongRoundInfoPresentation(
                tableId = gameId,
                tableLocation = resolved.location,
                tableFacing = resolved.facing,
                lines = lines,
            )
            roundInfoPresenter.present(presentation)
            val game = gameRepository.getGame(gameId) ?: return@launch
            val module = moduleRegistry.getModule(game.tableState.config)
            val orderedAiPlayerIds = game.roomPlayerIds.filter { id -> game.tableState.players.any { it.id == id && it.isAi } }
            val playerInfo = MahjongPlayerInfoPresentationFactory.create(game.tableState, module) { player ->
                serverHolder.findPlayer(player.id)?.gameProfile?.name
                    ?: if (player.isAi) aiPlayerDisplayName(player.id, orderedAiPlayerIds) else player.id.toString().take(8)
            }
            playerInfoPresenter.present(playerInfo, resolved.location, resolved.facing)
        }
    }

    /**
     * 一般回合動作（捨牌、摸牌、鳴牌）呼叫，不需要 [busyTracker] 或呼叫端延遲——即使 [animateDrawnTile]
     * 為 `true` 觸發摸牌動畫，那段動畫本身的排程完全交給 `FabricMahjongPlayerAreaPresenter` 內部處理
     * （`scheduleDrawnTileAnimation`），這裡仍然是直接同步呈現，理由同其餘一般回合動作。開局/換局的
     * 初次發牌改走 [publishInitialDealAnimation]，不會呼叫這個方法。
     */
    override fun publishPlayerAreaUpdated(
        gameId: Uuid,
        seatIndex: Int,
        standingTileIds: List<Uuid>,
        drawnTileId: Uuid?,
        melds: List<MeldPresentation>,
        comboStickCount: Int,
        animateDrawnTile: Boolean,
        animatedMeldClaimTileIds: Set<Uuid>,
    ) {
        if (serverHolder.current() == null) {
            logger.warn("publishPlayerAreaUpdated gameId={} skipped: no active server", gameId)
            return
        }
        presentPlayerArea(gameId, seatIndex, standingTileIds, drawnTileId, melds, comboStickCount, animateDrawnTile, animatedMeldClaimTileIds)
    }

    /**
     * 初次發牌要等牌牆＋擲骰動畫都播完才輪到——不能在骰子還在動畫時就直接讓手牌出現。過去用外層
     * `tickClock.scheduleAfter` 延遲整個呼叫本身（那段延遲純粹活在記憶體裡，撐不過伺服器重啟）；改成
     * 立刻呼叫 [playerAreaPresenter.presentInitialDeal]，把等待時長（跟 [publishDiceRoll] 同一套
     * [wallDropTicksByTable] 機制，涵蓋牌牆掉落動畫時長，再加上擲骰動畫時長）折算進
     * [MahjongInitialDealPresentation.extraLeadDelayTicks]，變成每張牌自己動畫佇列最前面的一個等待
     * step（見 `FabricMahjongPlayerAreaPresenter.scheduleDealBatchAnimation`），理由見
     * `AnimatedMahjongEntity` KDoc。
     *
     * 玩家操作／自動操作心跳（[GameFlowCoordinator.driveAutomatedPlayers]）不會搶在整段開局呈現
     * （建牌 → 擲骰開門 → 分牌 → 翻牌 → 觀看緩衝）播完前執行，現在是靠 [busyTracker] 直接查詢桌上
     * entity 是否還在動畫佇列裡，不需要另外手動標記忙碌時長，見 [TablePresentationBusyTracker] KDoc；
     * 觀看緩衝本身也已經摺進每張牌佇列尾端（見
     * `FabricMahjongPlayerAreaPresenter.OPENING_SEQUENCE_EXTRA_VIEWING_TICKS`）。
     */
    override fun publishInitialDealAnimation(
        gameId: Uuid,
        handTileIdsBySeatIndex: Map<Int, List<Uuid>>,
        postFlipHandTileIdsBySeatIndex: Map<Int, List<Uuid>>,
        dealerSeatIndex: Int,
        comboStickCount: Int,
        dealBatchSizes: List<Int>,
        diceCount: Int,
    ) {
        if (serverHolder.current() == null) {
            logger.warn("publishInitialDealAnimation gameId={} skipped: no active server", gameId)
            return
        }
        val wallDropTicks = wallDropTicksByTable[gameId] ?: 0
        val diceTicks = if (diceCount > 0) MahjongDiceTableLayout.totalAnimationTicks(diceCount) else 0
        busyTracker.markPending(gameId)
        scope.launch(dispatchers.main) {
            try {
                val resolved = resolveTableContext(gameId, "publishInitialDealAnimation") ?: return@launch

                val presentation = MahjongInitialDealPresentation(
                    tableId = gameId,
                    tableLocation = resolved.location,
                    tableFacing = resolved.facing,
                    handTileIdsBySeatIndex = handTileIdsBySeatIndex,
                    postFlipHandTileIdsBySeatIndex = postFlipHandTileIdsBySeatIndex,
                    dealerSeatIndex = dealerSeatIndex,
                    comboStickCount = comboStickCount,
                    dealBatchSizes = dealBatchSizes,
                    extraLeadDelayTicks = wallDropTicks + diceTicks,
                )
                playerAreaPresenter.presentInitialDeal(presentation)
                tableOverlayCoordinator.showNow(
                    resolved.world,
                    gameId,
                    BlockPos(resolved.location.x, resolved.location.y, resolved.location.z),
                )
            } finally {
                busyTracker.clearPending(gameId)
            }
        }
    }

    /**
     * 實際呼叫 [playerAreaPresenter]，跟 [publishDiceRoll] 同理丟回伺服器主執行緒執行——每次摸牌/
     * 捨牌/鳴牌都會呼叫，是自動操作心跳（[GameFlowCoordinator.driveAutomatedPlayers]）觸發頻率最高
     * 的呈現路徑，因此也需要 [busyTracker.markPending]／[busyTracker.clearPending] 覆蓋「已排定呈現、
     * entity 還沒真正生成/移動」那段窗口，理由見 [TablePresentationBusyTracker] KDoc：AI 連續行動時
     * 若沒有這組保護，偶爾會在這段窗口內搶跑，造成呈現跟權威桌況不同步、殘留幽靈 entity，這是遊戲內
     * 實際驗證過的問題。
     */
    private fun presentPlayerArea(
        gameId: Uuid,
        seatIndex: Int,
        standingTileIds: List<Uuid>,
        drawnTileId: Uuid?,
        melds: List<MeldPresentation>,
        comboStickCount: Int,
        animateDrawnTile: Boolean,
        animatedMeldClaimTileIds: Set<Uuid>,
    ) {
        busyTracker.markPending(gameId)
        scope.launch(dispatchers.main) {
            try {
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
                    animateDrawnTile = animateDrawnTile,
                    animatedMeldClaimTileIds = animatedMeldClaimTileIds,
                )
                playerAreaPresenter.present(presentation)
            } finally {
                busyTracker.clearPending(gameId)
            }
        }
    }

    /**
     * 清除整桌所有玩家的手牌/摸牌位/副露/積棒/立直棒/局況顯示呈現——回房間等清空情境使用（見
     * `ReturnToRoomUseCase`），沒有座位分組資料可傳，直接呼叫 [playerAreaPresenter]／
     * [scoringStickPresenter]／[riichiStickPresenter]／[roundInfoPresenter] 各自的 `clear()`（以
     * `managedTableId` 範圍搜尋清除，不需要逐座位資料）。
     */
    override fun clearPlayerAreas(gameId: Uuid) {
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
            playerAreaPresenter.clear(gameId, location)
            scoringStickPresenter.clear(gameId, location)
            riichiStickPresenter.clear(gameId, location)
            roundInfoPresenter.clear(gameId, location)
            playerInfoPresenter.clear(gameId, location)
        }
    }

    /** 跟 [publishDiceRoll] 同理，座位傳送也需要碰觸世界／entity，一併丟回伺服器主執行緒執行。 */
    override fun publishGameStarted(gameId: Uuid, seatedPlayerIds: List<Uuid>) {
        if (serverHolder.current() == null) return
        scope.launch(dispatchers.main) { seatingPresenter.present(gameId, seatedPlayerIds) }
    }

    /**
     * 一般回合動作，即使 [newlyDiscardedTileId] 非 `null` 觸發捨牌動畫，那段動畫本身的排程完全交給
     * `FabricMahjongDiscardPresenter` 內部處理，理由同 [publishPlayerAreaUpdated] 的 `animateDrawnTile`
     * 同款設計；但這裡的 entity 操作仍是丟回伺服器主執行緒非同步執行，一樣需要
     * [busyTracker.markPending]／[busyTracker.clearPending] 覆蓋「已排定呈現、entity 還沒真正生成/
     * 移動」那段窗口，理由同 [presentPlayerArea]。
     */
    override fun publishDiscardPileUpdated(
        gameId: Uuid,
        seatIndex: Int,
        discardTileIds: List<Uuid>,
        sidewaysMarkedTileId: Uuid?,
        newlyDiscardedTileId: Uuid?,
    ) {
        if (serverHolder.current() == null) {
            logger.warn("publishDiscardPileUpdated gameId={} skipped: no active server", gameId)
            return
        }
        busyTracker.markPending(gameId)
        scope.launch(dispatchers.main) {
            try {
                val resolved = resolveTableContext(gameId, "publishDiscardPileUpdated") ?: return@launch

                val presentation = MahjongDiscardPresentation(
                    tableId = gameId,
                    tableLocation = resolved.location,
                    tableFacing = resolved.facing,
                    seatIndex = seatIndex,
                    discardTileIds = discardTileIds,
                    sidewaysMarkedTileId = sidewaysMarkedTileId,
                    newlyDiscardedTileId = newlyDiscardedTileId,
                )
                discardPresenter.present(presentation)
            } finally {
                busyTracker.clearPending(gameId)
            }
        }
    }

    /**
     * 讀取贏家目前的權威手牌（[gameRepository]，唯讀，不寫回任何 `TableState`）算出強制理牌重排的目標
     * 順序（`Hand.organize`，不論贏家原本是否啟用自動整理手牌），委託
     * [playerAreaPresenter.presentWinCelebration] 排定「重排 → （自摸牌單獨倒下 →）等待 → 立牌一起
     * 倒下」序列，最後把算出來的「立牌全部倒下完成」絕對 game time 交給 [effectScheduler] 接續排定
     * 降臨特效——特效鎖定的目標位置固定是 [winningTileId] 目前所在座標：自摸時它已經併入贏家手牌、
     * 停在整理後的格位；榮和／搶槓時它仍在放銃者的牌河或副露區，天然就是「胡牌張目前所在座標」，
     * 不需要額外判斷。
     *
     * 一炮多響會對每位贏家各自呼叫一次這個方法（見 [GamePresentationPublisher.publishWinCelebration]
     * KDoc），但 `winningTileId` 對每位贏家來說是同一張牌——[effectScheduler] 內部依它去重，不會重複
     * 播放特效，見該類別 KDoc。
     */
    override fun publishWinCelebration(gameId: Uuid, request: WinCelebrationRequest) {
        publish(gameId, "publishWinCelebration", blocksTable = true) { resolved, state, startAt ->
            runCelebration(gameId, resolved, state, request, startAt)?.endGameTime
                ?.also { resolved.table.extendPresentationUntil(it) }
        }
    }

    /** 通知平台顯示逐位贏家詳情與共用分數排行。 */
    override fun publishWinSettlement(gameId: Uuid, request: WinSettlementPresentationRequest) {
        publish(gameId, "publishWinSettlement", blocksTable = true) { resolved, state, startAt ->
            runSettlement(gameId, resolved, state, request, startAt)?.also { resolved.table.extendPresentationUntil(it) }
        }
    }

    /**
     * 一次排定慶祝演出與結算面板，順序由本方法內部保證——兩段在**同一個** coroutine 內依序算完，
     * 第一段的結束時間直接當成第二段的起點，不依賴兩次獨立非同步呼叫的先後順序。
     *
     * 阻塞策略（見 [WinPresentationRequest] KDoc）：
     * - `roundContinues == false`：整段獨佔全桌，維持既有行為。
     * - `roundContinues == true`：只有役滿 showcase 那一段延展整桌共用時間軸（暫停玩家／AI／強制自動
     *   操作／決策計時器）；一般倒牌特效與**整個結算面板**都只延展中途胡牌時間軸，不阻塞其他人。
     *
     * `roundContinues == true` 時，整段結束後一律把贏家真實手牌收尾（恢復可見、蓋成牌背）——即使這次
     * 兩個請求都是 null（`NONE` 模式）也一樣，否則已完成玩家的手牌會一直立在桌上。
     */
    override fun publishWinPresentation(gameId: Uuid, request: WinPresentationRequest) {
        // 開發用的一次性 showcase 覆寫必須在 hasWatchableShowcase／blocksTable 判定之前套用，之後
        // 整段流程（阻塞判定、兩條時間軸的切分、收尾恢復可見的範圍）才會一致，見
        // DebugWinShowcaseOverride KDoc。正式產物裡這一步永遠是原樣回傳。
        val effectiveRequest = request.copy(celebration = debugWinShowcaseOverride.applyTo(gameId, request.celebration))
        val blocksTable = !effectiveRequest.roundContinues || effectiveRequest.hasWatchableShowcase
        publish(gameId, "publishWinPresentation", blocksTable) { resolved, state, startAt ->
            var cursor = startAt
            val celebration = runCelebration(gameId, resolved, state, effectiveRequest.celebration, cursor)
            celebration?.let { cursor = it.endGameTime }
            val settlementEnd = runSettlement(gameId, resolved, state, effectiveRequest.settlement, cursor)
            settlementEnd?.let { cursor = it }

            if (effectiveRequest.roundContinues) {
                // 只有需要觀看的役滿 showcase 才擋住全桌；其餘（含結算面板）走不阻塞的那條時間軸。
                celebration?.showcaseEndGameTime?.let { resolved.table.extendPresentationUntil(it) }
                resolved.table.extendContinuingWinPresentationUntil(cursor)
                // 這一步同時替所有牽涉到的真實牌建立「動畫不阻塞全桌」lease。lease 涵蓋整段演出（含前面
                // 已經排好的理牌／倒牌動畫），在這裡才套用是安全的：整個 block 在伺服器主執行緒上一次
                // 跑完，而 isBusy 的呼叫端（輸入分派、心跳）也都在同一條執行緒，觀察不到中間狀態。
                restoreAndConcealWinnerHands(resolved, state, effectiveRequest, celebration, cursor)
            } else if (celebration != null || settlementEnd != null) {
                resolved.table.extendPresentationUntil(cursor)
            }
            cursor
        }
    }

    /**
     * 共用的呈現派發樣板：解析桌況、切回主執行緒、依 [blocksTable] 決定要不要標記整桌忙碌，並把兩條
     * 呈現時間軸中較晚的那個結束時間當成本次演出最早可開始的時刻——這樣同一桌接連發生的胡牌演出
     * 一定依序播放，不會疊在一起。
     */
    private fun publish(
        gameId: Uuid,
        operation: String,
        blocksTable: Boolean,
        block: (ResolvedTableContext, TableState, Long) -> Long?,
    ) {
        if (serverHolder.current() == null) {
            logger.warn("{} gameId={} skipped: no active server", operation, gameId)
            return
        }
        if (blocksTable) busyTracker.markPending(gameId)
        scope.launch(dispatchers.main) {
            try {
                val resolved = resolveTableContext(gameId, operation) ?: return@launch
                val state = gameRepository.getTableState(gameId) ?: return@launch
                val startAt = maxOf(
                    resolved.world.time,
                    resolved.table.presentationBusyUntilGameTime,
                    resolved.table.continuingWinPresentationBusyUntilGameTime,
                )
                block(resolved, state, startAt)
            } catch (cause: Exception) {
                logger.warn("Failed to run {} for gameId={}", operation, gameId, cause)
            } finally {
                if (blocksTable) busyTracker.clearPending(gameId)
            }
        }
    }

    /**
     * [runCelebration] 的結果。
     *
     * @property endGameTime 整段慶祝演出的結束時間。
     * @property showcaseEndGameTime 役滿 showcase 的結束時間；沒播 showcase 時為 null。
     * @property showcaseHiddenTileIds 實際交接給 showcase 舞台、因而被設成隱形的真實牌——收尾時要恢復
     * 可見的就是**這一組**，不能拿手牌全集去猜（副露從頭到尾沒有被 showcase 碰過）。沒播 showcase
     * 時為空集合。
     */
    private data class CelebrationSegment(
        val endGameTime: Long,
        val showcaseEndGameTime: Long?,
        val showcaseHiddenTileIds: Set<Uuid>,
    )

    /**
     * 排定一次慶祝演出（強制理牌 → 倒牌 → 降臨特效 → 役滿 showcase），整段接在
     * [earliestStartGameTime] 之後開始；回傳各段結束時間，呼叫端負責延展時間軸與銜接後續。
     */
    private fun runCelebration(
        gameId: Uuid,
        resolved: ResolvedTableContext,
        state: TableState,
        request: WinCelebrationRequest,
        earliestStartGameTime: Long,
    ): CelebrationSegment? {
        if (request.winners.any { state.players.getOrNull(it.seatIndex) == null }) {
            logger.warn("runCelebration gameId={} skipped: winner seat not found", gameId)
            return null
        }
        val module = moduleRegistry.getModule(state.config)
        val dealerSeatIndex = state.dealerIndex
        var handLaydownEndGameTime: Long? = null
        val organizedBySeat = request.winners.associate { requestedWinner ->
            val winner = state.players[requestedWinner.seatIndex]
            val organizedHand = winner.hand.organize(module.tileOrder)
            val melds = winner.hand.melds.map { it.toPresentation(state.config.revealsClosedKanTiles) }.map { p ->
                MahjongMeldTileGroup(
                    type = p.type,
                    tileIds = p.tileIds,
                    calledTileId = p.calledTileId,
                    sourceDirection = p.sourceDirection,
                    allTilesFaceDown = p.allTilesFaceDown,
                )
            }
            val presentation = MahjongWinCelebrationPresentation(
                tableId = gameId,
                tableLocation = resolved.location,
                tableFacing = resolved.facing,
                seatIndex = requestedWinner.seatIndex,
                organizedStandingTileIds = organizedHand.tiles.map { it.id },
                melds = melds,
                comboStickCount = if (requestedWinner.seatIndex == dealerSeatIndex) state.comboCount else 0,
                winningTileId = request.winningTileId,
                isTsumo = request.isTsumo,
                earliestStartGameTime = earliestStartGameTime,
            )
            val result = playerAreaPresenter.presentWinCelebration(presentation)
            result.handLaydownEndGameTime?.let { endTime ->
                handLaydownEndGameTime = maxOf(handLaydownEndGameTime ?: endTime, endTime)
            }
            requestedWinner.seatIndex to organizedHand.tiles
        }
        if (handLaydownEndGameTime != null) {
            val effectStartGameTime = checkNotNull(handLaydownEndGameTime) + MahjongTileTableLayout.WIN_PRE_EFFECT_DELAY_TICKS
            val effectEndGameTime = effectStartGameTime + MahjongTileTableLayout.WIN_EFFECT_DURATION_TICKS
            effectScheduler.schedule(
                world = resolved.world,
                targetTileId = request.winningTileId,
                startGameTime = effectStartGameTime,
                endGameTime = effectEndGameTime,
            )
            val eligibleWings = request.winners.filter { it.cue != null }.map { requestedWinner ->
                FabricWinCelebrationShowcaseScheduler.Wing(
                    seatIndex = requestedWinner.seatIndex,
                    cueKey = requestedWinner.cue?.key,
                    tileIdsAndAssets = organizedBySeat.getValue(requestedWinner.seatIndex)
                        .filterNot { it.id == request.winningTileId }
                        .map { it.id to it.tile.toAssetKey(tileAssetRegistry) },
                )
            }
            val winningTile = state.findTile(request.winningTileId)
            // showcase 會把這些真實牌設成隱形交接給舞台代理（見 FabricWinCelebrationShowcaseScheduler），
            // 收尾時要恢復可見的就是這一組。
            val showcaseHiddenTileIds = if (eligibleWings.isNotEmpty() && winningTile != null) {
                eligibleWings.flatMapTo(mutableSetOf()) { wing -> wing.tileIdsAndAssets.map { it.first } } +
                    request.winningTileId
            } else {
                emptySet()
            }
            val showcaseEnd = if (eligibleWings.isNotEmpty() && winningTile != null) {
                showcaseScheduler.schedule(
                    world = resolved.world,
                    tableId = gameId,
                    controllerPos = BlockPos(resolved.location.x, resolved.location.y, resolved.location.z),
                    stagePlacement = MahjongTileTableLayout.showcaseStagePlacement(
                        resolved.location.x,
                        resolved.location.y,
                        resolved.location.z,
                    ),
                    startGameTime = effectEndGameTime,
                    winningTileId = request.winningTileId,
                    winningTileAssetKey = winningTile.tile.toAssetKey(tileAssetRegistry),
                    wings = eligibleWings,
                )
            } else {
                null
            }
            tableOverlayCoordinator.hideUntil(
                resolved.world,
                gameId,
                BlockPos(resolved.location.x, resolved.location.y, resolved.location.z),
                showcaseEnd ?: effectEndGameTime,
            )
            return CelebrationSegment(
                endGameTime = showcaseEnd ?: effectEndGameTime,
                showcaseEndGameTime = showcaseEnd,
                showcaseHiddenTileIds = if (showcaseEnd != null) showcaseHiddenTileIds else emptySet(),
            )
        }
        return null
    }

    /** 排定一次結算面板，接在 [earliestStartGameTime] 之後開始；回傳面板結束時間。 */
    private fun runSettlement(
        gameId: Uuid,
        resolved: ResolvedTableContext,
        state: TableState,
        request: WinSettlementPresentationRequest,
        earliestStartGameTime: Long,
    ): Long? {
        val tileIds = buildSet {
            request.winners.forEach { winner ->
                addAll(winner.handTileIds)
                winner.melds.forEach { addAll(it.tileIds) }
                winner.winningTileId?.let(::add)
                winner.detailFields.forEach { field ->
                    (field.value as? WinSettlementDetailValue.Tiles)?.let { addAll(it.tileIds) }
                }
            }
        }
        val assets = tileIds.mapNotNull { id -> state.findTile(id)?.let { id to it.tile.toAssetKey(tileAssetRegistry) } }.toMap()
        val end = winSettlementScheduler.schedule(
            world = resolved.world,
            tableId = gameId,
            controllerPos = BlockPos(resolved.location.x, resolved.location.y, resolved.location.z),
            placement = MahjongTileTableLayout.showcaseStagePlacement(resolved.location.x, resolved.location.y, resolved.location.z),
            earliestStartGameTime = earliestStartGameTime,
            request = request,
            tileAssetsById = assets,
        )
        return end
    }

    /**
     * 中途胡牌整段演出結束（[endGameTime]）後，把牽涉到的真實牌收尾乾淨。
     *
     * 兩件事都必須做，缺一不可：
     * 1. **恢復可見**——役滿 showcase 會把參與展示的真實牌 `SetInvisible(true)` 交接給舞台代理
     *    （見 [FabricWinCelebrationShowcaseScheduler]），而它本身從不還原。本局結束的胡牌不會有事，
     *    因為下一局會重新發牌；但本局繼續時這些牌會永遠隱形，連帶永久失去碰撞
     *    （`MahjongTileEntity.isCollidable` = `!isInvisible && physicalCollisionEnabled`）。恢復的對象
     *    刻意取 [CelebrationSegment.showcaseHiddenTileIds]——也就是**實際交接出去的那一組**，不是拿
     *    手牌全集去猜；沒播 showcase 時這組是空的，不會有多餘的動畫。
     * 2. **蓋成牌背**——已完成的玩家不再行動，立牌不該繼續攤著。
     *
     * 蓋牌範圍只取 `hand.standingTiles`，**絕不包含副露**：吃、碰、明槓、暗槓本來就是公開資訊，蓋起來
     * 既不合規則，也會摧毀既有的牌面、橫置方向與加槓疊牌版面（見 [MahjongTileTableLayout]）。副露從
     * 頭到尾沒有被慶祝演出或 showcase 碰過，因此這裡也不需要為它們做任何事。
     *
     * 榮和的胡牌張不屬於贏家立牌（它留在放銃者的牌河），因此只會出現在恢復可見那一組，維持原本的
     * 牌河姿態與位置，不跟著蓋牌。
     *
     * 兩者都以絕對時間排進各張牌自己的動畫佇列後就不再追蹤——佇列寫進 entity NBT，重啟會自己接續
     * 播完，沿用 `FabricExhaustiveDrawSettlementPresentationScheduler` 對流局手牌的既有做法。
     */
    private fun restoreAndConcealWinnerHands(
        resolved: ResolvedTableContext,
        state: TableState,
        request: WinPresentationRequest,
        celebration: CelebrationSegment?,
        endGameTime: Long,
    ) {
        val plan = WinPresentationCleanupPlan.of(
            winnerHands = request.winnerPlayerIds.mapNotNull { winnerId ->
                state.players.firstOrNull { it.id == winnerId }?.hand
            },
            showcaseHiddenTileIds = celebration?.showcaseHiddenTileIds.orEmpty(),
        )
        // 收尾動畫本身也要算進豁免範圍，否則蓋牌那幾 tick 又會讓整桌忙碌。
        val exemptUntil = endGameTime + MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS
        plan.animatedTileIds.forEach { tileId ->
            resolved.findTile(tileId)?.exemptFromTableBusyUntil(exemptUntil)
        }
        // 先還原可見度再蓋牌：兩者排進同一條佇列，順序不能顛倒。
        plan.restoreVisibleTileIds.forEach { tileId ->
            resolved.findTile(tileId)?.enqueueAll(
                listOf(AnimationStep.WaitUntil(endGameTime), AnimationStep.SetInvisible(false)),
            )
        }
        plan.concealTileIds.forEach { tileId ->
            resolved.findTile(tileId)?.let { tile -> TileAnimationSteps.scheduleConceal(tile, endGameTime) }
        }
    }

    /** 依 UUID 取得這桌世界裡的牌實體。 */
    private fun ResolvedTableContext.findTile(tileId: Uuid): MahjongTileEntity? = world.getEntity(tileId.toJavaUuid()) as? MahjongTileEntity

    /** 依 UUID 從所有權威牌區尋找牌面。 */
    private fun TableState.findTile(tileId: Uuid): IdentifiedTile? = players.asSequence().flatMap { player ->
        (player.hand.allTiles + player.discardPile.entries.map { it.tile }).asSequence()
    }.plus(tileWall.getAllTiles().asSequence()).plus(initialDeadWall.asSequence()).firstOrNull { it.id == tileId }

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
        val table = world.getBlockEntity(controllerPos) as? MahjongTableBlockEntity
        if (table?.tableId != gameId) {
            logger.warn("$methodName gameId={} skipped: controller block entity is missing or belongs to another table", gameId)
            return null
        }
        val facing = state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing()
        return ResolvedTableContext(world, location, facing, table)
    }

    /** [resolveTableContext] 的解析結果。 */
    private data class ResolvedTableContext(
        val world: ServerWorld,
        val location: TableLocation,
        val facing: MahjongTableFacing,
        val table: MahjongTableBlockEntity,
    )

    /** 正式擲骰／開局呈現使用的固定參數。 */
    private companion object {
        /** 把局數換算成 `rollSequence` 時的本場數進位基準；本場數在真實對局中不可能達到此量級。 */
        const val ROLL_SEQUENCE_ROUND_MULTIPLIER: Long = 1_000_000L
    }
}
