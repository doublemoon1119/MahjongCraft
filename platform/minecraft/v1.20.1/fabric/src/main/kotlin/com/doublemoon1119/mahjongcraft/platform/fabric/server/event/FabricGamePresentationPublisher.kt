package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

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
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionPhase
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.opening.OpeningDiceRoll
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongSoundTimelineEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.FabricSessionBackgroundScope
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.FabricEntitySpawnGateway
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricExhaustiveDrawSettlementPresentationScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricMatchSettlementPresentationScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricWinCelebrationEffectScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricWinCelebrationShowcaseScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricWinSettlementPresentationScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinShowcaseOverride
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.showcaseWingCards
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.RoundResultSender
import com.doublemoon1119.mahjongcraft.platform.fabric.server.player.ServerPlayerIdentityStore
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableLifecycleService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.PersistentTableOverlayCoordinator
import com.doublemoon1119.mahjongcraft.platform.fabric.server.tile.TileAnimationSteps
import com.doublemoon1119.mahjongcraft.platform.fabric.server.time.FabricWorldTickWaiter
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.WinPresentationCleanupPlan
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDicePresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollTimeline
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.aiPlayerDisplayName
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.MahjongSeatingPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongPlayerInfoPresentationFactory
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongPlayerInfoPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongRoundInfoPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongRoundInfoPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTileSelectionConfirmPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTileSelectionConfirmPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableCornerWidthTracker
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropDescriber
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropDescriberRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.cornerWidthsBySeat
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.exhaustiveDrawSettlementStageInputs
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.findTile
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.tileAssetKeysById
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDiscardPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDiscardPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongInitialDealPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongMeldTileGroup
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongPlayerAreaPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongPlayerAreaPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongPlayerAreaPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallOpeningPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallProjectionContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallSideLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallTransitionPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallTransitionResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongWinCelebrationPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.toAssetKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.property.Properties
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import kotlin.random.Random
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 將對局呈現請求交給 Fabric 呈現元件；世界與實體操作在伺服器主執行緒執行。
 *
 * @property seatingPresenter 開局座位傳送的實際呈現邏輯。
 * @property diceRollPresenter 正式擲骰的實際呈現邏輯。
 * @property tileWallPresenter 正式牌牆的實際呈現邏輯。
 * @property playerAreaPresenter 手牌、摸牌位、副露與另置牌的呈現元件。
 * @property discardPresenter 正式牌河的實際呈現邏輯。
 * @property tablePropPresenter 規則桌面物件（例如點棒）的差量同步邏輯。
 * @property tablePropDescriberRegistry 依規則模組查詢桌上應擺哪些物件的描述。
 * @property tableCornerWidths 桌上物件目前佔用的副露角落寬度紀錄，手牌、胡牌演出與結算舞台依此讓開角落。
 * @property roundInfoPresenter 桌面中央局況顯示的實際呈現邏輯。
 * @property roundInfoLineDisplayRegistry 依規則模組建立局況行並查詢其翻譯資訊。
 * @property playerInfoPresenter 座位玩家資訊的呈現邏輯。
 * @property tileSelectionConfirmPresenter 實體選牌確認提示的呈現邏輯。
 * @property tableLocationRegistry 麻將桌最後已知位置索引。
 * @property serverHolder 目前運行中的 server，供世界／方塊狀態查詢使用。
 * @property playerIdentities 真人最後已知普通名稱來源。
 * @property openingOperations 開局呈現操作的生命週期追蹤器。
 * @property spawnGateway 主執行緒實體生成閘道。
 * @property debugWinShowcaseOverride 開發場景的胡牌展示覆寫值。
 * @property debugWinRoundContinuationState 開發場景的胡牌後流程狀態。
 * @property busyTracker 查詢／標記該桌是否呈現動畫播放中，供輸入分派入口與自動操作心跳擋下操作，見
 *   [TablePresentationBusyTracker] KDoc。
 * @property gameRepository 唯讀取得桌況、手牌與副露，建立呈現資料；不透過此元件修改權威狀態。
 * @property moduleRegistry 解析對局採用的規則模組，取得 [publishWinCelebration] 算牌序需要的 `tileOrder`。
 * @property effectScheduler 胡牌慶祝演出降臨特效（粒子聚合光柱）的排程器。
 * @property showcaseScheduler 胡牌手牌展示排程器。
 * @property exhaustiveDrawSettlementScheduler 流局結算呈現排程器。
 * @property winSettlementScheduler 胡牌結算呈現排程器。
 * @property matchSettlementScheduler 整場排名結算呈現排程器。
 * @property tableOverlayCoordinator 桌級持久面板協調器。
 * @property tileAssetRegistry 牌面資產與材質註冊表。
 * @property gameActionSoundPresentationRegistry 動作聲音與宣告語音呈現註冊表。
 * @property roundResultSender 在和牌或流局結算當下把前後分數與名次送給入座的真人玩家。
 * @property scope 承接世界／方塊狀態查詢需要切回伺服器主執行緒的呈現工作；呈現可以捨棄，關服時直接取消。
 * @property dispatchers 切回伺服器主執行緒用的 dispatcher。
 */
class FabricGamePresentationPublisher(
    private val seatingPresenter: MahjongSeatingPresenter,
    private val diceRollPresenter: MahjongDiceRollPresenter,
    private val tileWallPresenter: MahjongTileWallPresenter,
    private val playerAreaPresenter: MahjongPlayerAreaPresenter,
    private val discardPresenter: MahjongDiscardPresenter,
    private val tablePropPresenter: TablePropPresenter,
    private val tablePropDescriberRegistry: TablePropDescriberRegistry,
    private val tableCornerWidths: TableCornerWidthTracker,
    private val roundInfoPresenter: MahjongRoundInfoPresenter,
    private val roundInfoLineDisplayRegistry: RoundInfoLineDisplayRegistry,
    private val playerInfoPresenter: MahjongPlayerInfoPresenter,
    private val tileSelectionConfirmPresenter: MahjongTileSelectionConfirmPresenter,
    private val tableLocationRegistry: TableLocationRegistry,
    private val serverHolder: FabricServerHolder,
    private val playerIdentities: ServerPlayerIdentityStore,
    private val busyTracker: TablePresentationBusyTracker,
    private val openingOperations: TableOpeningPresentationOperationTracker,
    private val spawnGateway: FabricEntitySpawnGateway,
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
    private val gameActionSoundPresentationRegistry: GameActionSoundPresentationRegistry,
    private val roundResultSender: RoundResultSender,
    private val scope: FabricSessionBackgroundScope,
    private val dispatchers: CoroutineDispatchers,
    private val worldTickWaiter: FabricWorldTickWaiter,
) : GamePresentationPublisher {
    /** 呈現略過、生成失敗與排程狀態的診斷記錄。 */
    private val logger = mahjongCraftLogger(FabricGamePresentationPublisher::class)

    /**
     * 開局四個階段之間的暫存資料。
     *
     * 呼叫端（`StartGameUseCase`／`AdvanceRoundUseCase`）固定先呼叫 [publishWallStructure] 才呼叫
     * [publishOpeningRolls]／[publishInitialDeal]，但這裡不強制要求該順序：讀不到值時一律有安全的
     * 預設行為，見 [TableOpeningPresentationState] KDoc。
     */
    private val openingState = TableOpeningPresentationState()

    /** 伺服器 session 結束時清除尚未完成的開局呈現暫存資料。 */
    fun clearOpeningSession() {
        openingState.clearAll()
    }

    override fun publishGameActionDeclared(gameId: Uuid, actorId: Uuid, action: GameAction) {
        publish(gameId, "publishGameActionDeclared", blocksTable = false) { resolved, state, _ ->
            val actorSeatIndex = state.players.indexOfFirst { player -> player.id == actorId }
            if (actorSeatIndex < 0) return@publish null
            val ruleModuleId = moduleRegistry.getModule(state.config).id
            val sound = gameActionSoundPresentationRegistry.find(ruleModuleId, action) ?: return@publish null
            val placement = MahjongTileTableLayout.handPlacement(
                controllerX = resolved.location.x,
                controllerY = resolved.location.y,
                controllerZ = resolved.location.z,
                tableFacing = resolved.facing,
                seatLayout = MahjongTableSeatLayout.forPlayerCount(state.players.size),
                seatIndex = actorSeatIndex,
                handSize = 1,
                tileIndex = 0,
            )
            val entity = MahjongSoundTimelineEntity(world = resolved.world).apply {
                refreshPositionAndAngles(placement.x, placement.y, placement.z, placement.yaw, 0.0f)
                configure(gameId, sound.soundId, sound.volume, sound.pitch, resolved.world.time)
            }
            if (!spawnGateway.spawn(resolved.world, entity, "game-action-sound", gameId)) {
                logger.warn("publishGameActionDeclared gameId={} actorId={} failed to spawn sound timeline", gameId, actorId)
            }
            null
        }
    }

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
                    "handDisclosure" to player.handDisclosure,
                    "waitingTiles" to player.waitingTiles.map(Any::toString),
                )
            },
        )
        roundResultSender.sendDraw(gameId, request)
        launchPendingPresentation(gameId, "publishExhaustiveDrawSettlement") {
            val resolved = resolveTableContext(gameId, "publishExhaustiveDrawSettlement") ?: return@launchPendingPresentation
            val tableState = gameRepository.getTableState(gameId)
            val stageInputs = exhaustiveDrawSettlementStageInputs(
                request = request,
                tableState = tableState,
                cornerWidthsBySeat = cornerWidthsBySeat(gameId, tableState),
                setAsideTiles = tableState?.let { moduleRegistry.getModule(it.config)::setAsideTiles } ?: { emptyList() },
                tileAssetRegistry = tileAssetRegistry,
            )
            val endGameTime = exhaustiveDrawSettlementScheduler.schedule(
                world = resolved.world,
                tableId = gameId,
                controllerPos = BlockPos(resolved.location.x, resolved.location.y, resolved.location.z),
                tableFacing = resolved.facing,
                placement = MahjongTileTableLayout.showcaseStagePlacement(
                    resolved.location.x,
                    resolved.location.y,
                    resolved.location.z,
                ),
                request = request,
                waitingTileAssetsBySeat = stageInputs.waitingTileAssetsBySeat,
                revealedTileAssetsById = stageInputs.revealedTileAssetsById,
                reservedCornerWidthsBySeat = stageInputs.reservedCornerWidthsBySeat,
            )
            if (endGameTime == null) {
                logger.warn("publishExhaustiveDrawSettlement gameId={} skipped: stage spawn failed", gameId)
            } else {
                resolved.table.extendPresentationUntil(endGameTime)
                logger.debug("Exhaustive draw settlement presentation created gameId={} endGameTime={}", gameId, endGameTime)
            }
        }
    }

    override fun publishMatchSettlement(gameId: Uuid, request: MatchSettlementPresentationRequest) {
        logger.debug(
            "Match settlement gameId={} rule={} players={}",
            gameId,
            request.ruleModuleId,
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
        launchPendingPresentation(gameId, "publishMatchSettlement") {
            try {
                val resolved = resolveTableContext(gameId, "publishMatchSettlement") ?: return@launchPendingPresentation
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
            }
        }
    }

    /**
     * 將骰子呈現排到主執行緒，並把牌牆落地的等待時間加入骰子動畫。
     * 開局工作透過同批操作識別碼追蹤；排程前先取得忙碌標記，工作結束時釋放。
     * 找不到桌子或世界時略過並記錄警告；生成失敗交由開局工作處理。
     */
    override fun publishOpeningRolls(
        gameId: Uuid,
        rolls: List<OpeningDiceRoll>,
    ) {
        if (rolls.isEmpty()) return
        if (serverHolder.current() == null) {
            logger.warn("publishOpeningRolls gameId={} skipped: no active server", gameId)
            return
        }
        val wallDropTicks = openingState.wallDropTicks(gameId)
        val openingOperation = openingOperations.getRegisteringTicket(gameId)
        launchOpeningStage(gameId, "opening-dice-rolls", openingOperation, pendingOperation = "publishOpeningRolls") {
            val timeline = MahjongDiceRollTimeline.segments(rolls.map { it.dice.values.size }, wallDropTicks)
            val resolved = resolveTableContext(gameId, "publishOpeningRolls") ?: return@launchOpeningStage
            val state = gameRepository.getTableState(gameId) ?: return@launchOpeningStage
            val seatLayout = MahjongTableSeatLayout.forPlayerCount(state.players.size)
            val timelineAnchor = openingState.awaitWallStartGameTime(gameId) ?: resolved.world.time
            if (!openingOperations.mayRun(openingOperation)) return@launchOpeningStage

            rolls.zip(timeline).forEachIndexed { index, (roll, segment) ->
                worldTickWaiter.await(resolved.world, timelineAnchor + segment.startOffsetTicks)
                if (!openingOperations.mayRun(openingOperation)) return@launchOpeningStage
                val actorSeatIndex = state.players.indexOfFirst { it.id == roll.playerId }
                check(actorSeatIndex >= 0) { "Opening dice roll actor is not seated: ${roll.playerId}" }
                val presentation = MahjongDiceRollPresentation(
                    tableId = gameId,
                    tableLocation = resolved.location,
                    tableFacing = resolved.facing,
                    throwSide = seatLayout.sideOf(actorSeatIndex),
                    rollSequence = openingState.openingRollSequence(gameId, roll.rollIndex) ?: index.toLong(),
                    dice = roll.dice.values.map { point ->
                        MahjongDicePresentation(
                            point = point,
                            animationSeed = Random.nextLong(),
                        )
                    },
                    extraLeadDelayTicks = 0,
                )
                val result = diceRollPresenter.present(presentation)
                if (result == MahjongDiceRollPresentationResult.SPAWN_FAILED) {
                    error("Dice roll presentation failed to spawn")
                }
                worldTickWaiter.await(resolved.world, timelineAnchor + segment.endOffsetTicks)
                if (!openingOperations.mayRun(openingOperation)) return@launchOpeningStage
            }
        }
    }

    /**
     * 在主執行緒建立牌牆呈現，並記錄後續擲骰、發牌所需的落地等待時間。
     * 只有新開局且牌牆呈現成功時，才保留後續開門資料；生成失敗交由開局工作處理。
     */
    override fun publishWallStructure(
        gameId: Uuid,
        assemblyStructure: Map<Uuid, TileWallPosition>,
        layout: TileWallPhysicalLayout,
        dealerSeatIndex: Int,
        deadWallTileIds: Set<Uuid>,
        openingDiceCounts: List<Int>,
        isNewOpening: Boolean,
        revealedTileIds: Set<Uuid>,
    ) {
        if (serverHolder.current() == null) {
            logger.warn("publishWallStructure gameId={} skipped: no active server", gameId)
            return
        }
        if (assemblyStructure.isEmpty() && layout.placements.isEmpty()) {
            openingState.clear(gameId)
            openingOperations.invalidate(gameId)
            launchPendingPresentation(gameId, "clearWallStructure") {
                val resolved = resolveTableContext(gameId, "clearWallStructure") ?: return@launchPendingPresentation
                tileWallPresenter.clear(gameId, resolved.location)
            }
            return
        }
        val stacksPerSide = assemblyStructure.values
            .filter { position -> position.side == 0 }
            .maxOfOrNull { position -> position.stack + 1 } ?: 0
        val wallDropTicks = MahjongTileTableLayout.wallDropAnimationTicks(stacksPerSide)
        val openingOperation = if (isNewOpening) openingOperations.begin(gameId) else null
        openingState.beginWall(
            gameId = gameId,
            wallDropTicks = wallDropTicks,
            stacksPerSide = stacksPerSide,
            sideCount = MahjongTileTableLayout.wallSideCount(assemblyStructure.values),
            isNewOpening = isNewOpening,
        )
        launchOpeningStage(gameId, "wall-structure", openingOperation, pendingOperation = "publishWallStructure") {
            val resolved = checkNotNull(resolveTableContext(gameId, "publishWallStructure")) { "Opening table is unavailable" }
            val state = checkNotNull(gameRepository.getTableState(gameId)) { "Opening game is unavailable" }
            val wallStartGameTime = resolved.world.time

            val presentation = MahjongTileWallPresentation(
                tableId = gameId,
                tableLocation = resolved.location,
                tableFacing = resolved.facing,
                sideLayout = wallSideLayout(state, dealerSeatIndex),
                assemblyStructure = assemblyStructure,
                finalLayout = layout,
                deadWallTileIds = deadWallTileIds,
                openingDiceCounts = openingDiceCounts,
                animateOpening = isNewOpening,
                revealedTileIds = revealedTileIds,
            )
            val result = tileWallPresenter.present(presentation)
            if (isNewOpening && result == MahjongTileWallPresentationResult.PRESENTED) {
                openingState.armOpening(gameId, presentation)
                openingState.markWallStarted(gameId, wallStartGameTime)
            } else if (result != MahjongTileWallPresentationResult.PRESENTED) {
                openingState.cancelOpening(gameId)
            }
            if (isNewOpening && result != MahjongTileWallPresentationResult.PRESENTED) {
                error("Tile wall presentation failed: $result")
            }
        }
    }

    /** 將規則提供的牌牆布局 transition 排入既有牌張的持久化動畫佇列。 */
    override fun publishWallLayoutTransition(gameId: Uuid, phases: List<PhysicalWallLayoutTransitionPhase>) {
        if (phases.isEmpty()) return
        publish(gameId, "publishWallLayoutTransition", blocksTable = true) { resolved, state, startAt ->
            val stacksPerSide = openingState.wallStacksPerSide(gameId)
            val sideCount = openingState.wallSideCount(gameId)
            if (stacksPerSide == null || sideCount == null) {
                logger.warn("publishWallLayoutTransition gameId={} skipped: wall stack count is unavailable", gameId)
                return@publish null
            }
            val result = tileWallPresenter.presentTransition(
                MahjongTileWallTransitionPresentation(
                    tableId = gameId,
                    tableLocation = resolved.location,
                    tableFacing = resolved.facing,
                    sideLayout = wallSideLayout(state, state.dealerIndex),
                    stacksPerSide = stacksPerSide,
                    phases = phases,
                    startGameTime = startAt,
                ),
            )
            if (result != MahjongTileWallTransitionResult.PRESENTED) {
                logger.warn("publishWallLayoutTransition gameId={} failed: result={}", gameId, result)
            }
            null
        }
    }

    /** 更新既有牌牆位置與公開姿態，不建立新牌或重播開局。 */
    override fun publishWallLayoutUpdated(
        gameId: Uuid,
        layout: TileWallPhysicalLayout,
        dealerSeatIndex: Int,
        revealedTileIds: Set<Uuid>,
    ) {
        if (serverHolder.current() == null) {
            logger.warn("publishWallLayoutUpdated gameId={} skipped: no active server", gameId)
            return
        }
        launchPendingPresentation(gameId, "publishWallLayoutUpdated") {
            val resolved = resolveTableContext(gameId, "publishWallLayoutUpdated") ?: return@launchPendingPresentation
            val state = gameRepository.getTableState(gameId) ?: return@launchPendingPresentation
            val stacksPerSide = openingState.wallStacksPerSide(gameId)
            val sideCount = openingState.wallSideCount(gameId)
            if (stacksPerSide == null || sideCount == null) {
                logger.warn("publishWallLayoutUpdated gameId={} skipped: wall dimensions are unavailable", gameId)
                return@launchPendingPresentation
            }
            val result = tileWallPresenter.synchronizeLayout(
                tableId = gameId,
                tableLocation = resolved.location,
                context = MahjongTileWallProjectionContext(
                    controllerX = resolved.location.x,
                    controllerY = resolved.location.y,
                    controllerZ = resolved.location.z,
                    tableFacing = resolved.facing,
                    sideLayout = wallSideLayout(state, dealerSeatIndex),
                    stacksPerSide = stacksPerSide,
                ),
                layout = layout,
                revealedTileIds = revealedTileIds,
            )
            if (result != MahjongTileWallPresentationResult.PRESENTED) {
                logger.warn("publishWallLayoutUpdated gameId={} failed: result={}", gameId, result)
            }
        }
    }

    /**
     * 在主執行緒更新指定牌牆牌張的可見面，不取得整桌忙碌標記。
     */
    override fun publishWallTilesRevealed(gameId: Uuid, revealedTileIds: Set<Uuid>) {
        if (serverHolder.current() == null) {
            logger.warn("publishWallTilesRevealed gameId={} skipped: no active server", gameId)
            return
        }
        scope.launch(dispatchers.main) {
            val location = tableLocationRegistry.get(gameId)?.location
            if (location == null) {
                logger.warn("publishWallTilesRevealed gameId={} skipped: no known table location", gameId)
                return@launch
            }
            tileWallPresenter.revealDeadWallTiles(gameId, location, revealedTileIds)
        }
    }

    /**
     * 呼叫端在開局／換局緊接 [publishWallStructure] 之後、以及規則狀態改變桌上物件之後呼叫；依對局規則在
     * [tablePropDescriberRegistry] 登記的描述算出桌上應有的物件，交給 [tablePropPresenter] 差量同步，並把
     * 同一份桌況算出的角落寬度記進 [tableCornerWidths]。規則沒有登記描述時，同步為空清單、寬度皆為 `0.0`。
     * 不需要 [busyTracker] 或延遲，世界／entity 存取丟回伺服器主執行緒執行。
     */
    override fun publishRuleStateUpdated(gameId: Uuid) {
        if (serverHolder.current() == null) {
            logger.warn("publishRuleStateUpdated gameId={} skipped: no active server", gameId)
            return
        }
        val openingOperation = openingOperations.getRegisteringTicket(gameId)
        launchOpeningStage(gameId, "table-props", openingOperation) {
            val resolved = resolveTableContext(gameId, "publishRuleStateUpdated") ?: return@launchOpeningStage
            val tableState = gameRepository.getTableState(gameId) ?: return@launchOpeningStage
            val describer = describerFor(tableState)
            tableCornerWidths.record(gameId, describer.cornerWidthsBySeat(tableState))
            val placements = describer?.describe(tableState).orEmpty()
            tablePropPresenter.present(
                TablePropPresentation(
                    tableId = gameId,
                    tableLocation = resolved.location,
                    tableFacing = resolved.facing,
                    seatLayout = MahjongTableSeatLayout.forPlayerCount(tableState.players.size),
                    placements = placements,
                ),
            )
        }
    }

    /**
     * 委派給 [tileSelectionConfirmPresenter]，比照 [publishRoundInfoUpdated] 的「建 Presentation、丟給
     * 專屬 presenter」分工——桌子被移除時的清除走 [FabricTableLifecycleService.onBlockReplaced] 直接呼叫
     * 同一個 presenter 的 `clear()`，不經過這裡。
     */
    override fun publishTileSelectionStarted(gameId: Uuid, playerId: Uuid) {
        if (serverHolder.current() == null) {
            logger.warn("publishTileSelectionStarted gameId={} skipped: no active server", gameId)
            return
        }
        scope.launch(dispatchers.main) {
            val resolved = resolveTableContext(gameId, "publishTileSelectionStarted") ?: return@launch
            val tableState = gameRepository.getTableState(gameId) ?: return@launch
            val seatIndex = tableState.players.indexOfFirst { it.id == playerId }
            if (seatIndex < 0) return@launch

            tileSelectionConfirmPresenter.present(
                MahjongTileSelectionConfirmPresentation(
                    tableId = gameId,
                    tableLocation = resolved.location,
                    tableFacing = resolved.facing,
                    seatLayout = MahjongTableSeatLayout.forPlayerCount(tableState.players.size),
                    seatIndex = seatIndex,
                    holderId = playerId,
                ),
            )
        }
    }

    /** 找不到既有面板時視為 no-op，不記警告——選牌提前結束、面板已被 fallback 逾時清除都是正常情境。 */
    override fun publishTileSelectionEnded(gameId: Uuid, playerId: Uuid) {
        if (serverHolder.current() == null) return
        scope.launch(dispatchers.main) {
            val resolved = resolveTableContext(gameId, "publishTileSelectionEnded") ?: return@launch
            tileSelectionConfirmPresenter.clearForPlayer(gameId, resolved.location, playerId)
        }
    }

    /**
     * 開局/換局（跟 [publishWallStructure] 同一批呼叫）跟每次摸牌都會觸發，不需要 [busyTracker] 或
     * 延遲——純文字更新是瞬間的；跟 [publishOpeningRolls] 同理，世界／entity 存取一併丟回伺服器主執行緒
     * 執行。
     */
    override fun publishRoundInfoUpdated(gameId: Uuid, tableState: TableState) {
        if (serverHolder.current() == null) {
            logger.warn("publishRoundInfoUpdated gameId={} skipped: no active server", gameId)
            return
        }
        val openingOperation = openingOperations.getRegisteringTicket(gameId)
        if (openingOperation != null) {
            openingState.deferRoundInfo(gameId, tableState)
            return
        }
        launchOpeningStage(gameId, "round-info", openingOperation) {
            val resolved = resolveTableContext(gameId, "publishRoundInfoUpdated") ?: return@launchOpeningStage
            presentRoundAndPlayerInfo(gameId, resolved, tableState)
        }
    }

    /** 交付局況與玩家資訊；開局擲骰揭示完成後也會使用這個共用路徑。 */
    private suspend fun presentRoundAndPlayerInfo(gameId: Uuid, resolved: ResolvedTableContext, tableState: TableState) {
        val ruleModuleId = moduleRegistry.getModule(tableState.config).id
        val lines = roundInfoLineDisplayRegistry.buildLines(ruleModuleId, tableState)
        roundInfoPresenter.present(
            MahjongRoundInfoPresentation(
                tableId = gameId,
                tableLocation = resolved.location,
                tableFacing = resolved.facing,
                lines = lines,
            ),
        )
        val game = gameRepository.getGame(gameId) ?: return
        val module = moduleRegistry.getModule(game.tableState.config)
        val orderedAiPlayerIds = game.roomPlayerIds.filter(game::isAi)
        val playerInfo = MahjongPlayerInfoPresentationFactory.create(game.tableState, module, game.aiPlayerIds) { player ->
            if (game.isAi(player.id)) aiPlayerDisplayName(player.id, orderedAiPlayerIds) else playerIdentities.resolveKnownName(player.id)
        }
        playerInfoPresenter.present(playerInfo, resolved.location, resolved.facing)
    }

    /**
     * 將玩家牌區更新排到主執行緒，並在排程至完成之間取得整桌忙碌標記。
     * 摸牌、副露與另置牌的動畫由牌區呈現元件處理；初次發牌另走 [publishInitialDeal]。
     */
    override fun publishPlayerTilesUpdated(
        gameId: Uuid,
        seatIndex: Int,
        standingTileIds: List<Uuid>,
        drawnTileId: Uuid?,
        melds: List<MeldPresentation>,
        setAsideTileIds: List<Uuid>,
        isNewlyDrawn: Boolean,
        newlyClaimedMeldTileIds: Set<Uuid>,
        newlySetAsideTileIds: Set<Uuid>,
    ) {
        if (serverHolder.current() == null) {
            logger.warn("publishPlayerTilesUpdated gameId={} skipped: no active server", gameId)
            return
        }
        presentPlayerArea(
            gameId = gameId,
            seatIndex = seatIndex,
            standingTileIds = standingTileIds,
            drawnTileId = drawnTileId,
            melds = melds,
            setAsideTileIds = setAsideTileIds,
            isNewlyDrawn = isNewlyDrawn,
            newlyClaimedMeldTileIds = newlyClaimedMeldTileIds,
            newlySetAsideTileIds = newlySetAsideTileIds,
        )
    }

    /**
     * 立刻排定初次發牌，將牌牆落地與擲骰的等待時間寫入每張牌的動畫佇列。
     * 依發牌完成時間銜接牌牆開門，並結束這一批開局工作的登記。
     * 實體動畫與待執行的呈現工作共同決定桌子是否忙碌。
     */
    override fun publishInitialDeal(
        gameId: Uuid,
        handTileIdsBySeatIndex: Map<Int, List<Uuid>>,
        postFlipHandTileIdsBySeatIndex: Map<Int, List<Uuid>>,
        dealerSeatIndex: Int,
        dealBatchSizes: List<Int>,
        openingDiceCounts: List<Int>,
    ) {
        if (serverHolder.current() == null) {
            logger.warn("publishInitialDeal gameId={} skipped: no active server", gameId)
            return
        }
        val wallDropTicks = openingState.wallDropTicks(gameId)
        val openingOperation = openingOperations.getRegisteringTicket(gameId)
        launchOpeningStage(gameId, "initial-deal", openingOperation, pendingOperation = "publishInitialDeal") {
            val resolved = resolveTableContext(gameId, "publishInitialDeal") ?: return@launchOpeningStage
            val state = gameRepository.getTableState(gameId) ?: return@launchOpeningStage
            val timelineAnchor = openingState.awaitWallStartGameTime(gameId) ?: resolved.world.time
            if (!openingOperations.mayRun(openingOperation)) return@launchOpeningStage
            val openingEndGameTime = timelineAnchor + MahjongDiceRollTimeline.totalTicks(openingDiceCounts, wallDropTicks)
            // 開局發牌與這一局的桌上物件更新讀的是同一份新局桌況；直接由它算出寬度並記錄，不依賴兩個呈現
            // 工作在主執行緒上的先後順序。
            val cornerWidthBySeatIndex = gameRepository.getTableState(gameId)
                ?.let { tableState -> describerFor(tableState).cornerWidthsBySeat(tableState) }
                ?.also { widths -> tableCornerWidths.record(gameId, widths) }
                .orEmpty()

            val presentation = MahjongInitialDealPresentation(
                tableId = gameId,
                tableLocation = resolved.location,
                tableFacing = resolved.facing,
                seatLayout = MahjongTableSeatLayout.forPlayerCount(state.players.size),
                handTileIdsBySeatIndex = handTileIdsBySeatIndex,
                postFlipHandTileIdsBySeatIndex = postFlipHandTileIdsBySeatIndex,
                dealerSeatIndex = dealerSeatIndex,
                cornerWidthBySeatIndex = cornerWidthBySeatIndex,
                dealBatchSizes = dealBatchSizes,
                extraLeadDelayTicks = (openingEndGameTime - resolved.world.time).coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            )
            val result = playerAreaPresenter.presentInitialDeal(presentation)
            val opening = openingState.consumeOpening(gameId)
            if (opening != null && result != MahjongPlayerAreaPresentationResult.TABLE_NOT_FOUND) {
                val totalTurnCount = dealBatchSizes.size * state.players.size
                val handFlipEndGameTime = resolved.world.time + presentation.extraLeadDelayTicks +
                    MahjongTileTableLayout.dealAnimationTicks(totalTurnCount)
                val openingResult = tileWallPresenter.presentOpening(
                    MahjongTileWallOpeningPresentation(opening, handFlipEndGameTime),
                )
                if (openingResult != MahjongTileWallPresentationResult.PRESENTED) {
                    logger.warn("Initial wall opening gameId={} failed: result={}", gameId, openingResult)
                }
            }
            tableOverlayCoordinator.showNow(
                resolved.world,
                gameId,
                BlockPos(resolved.location.x, resolved.location.y, resolved.location.z),
            )
            openingState.consumeRoundInfo(gameId)?.let { deferredTableState ->
                worldTickWaiter.await(resolved.world, openingEndGameTime)
                if (!openingOperations.mayRun(openingOperation)) return@launchOpeningStage
                val current = resolveTableContext(gameId, "publishRoundInfoUpdated") ?: return@launchOpeningStage
                presentRoundAndPlayerInfo(gameId, current, deferredTableState)
            }
        }
        if (openingOperation != null) openingOperations.finishRegistration(openingOperation)
    }

    /**
     * 在主執行緒更新指定玩家的牌區，排程至完成期間維持整桌忙碌標記。
     *
     * @param gameId 桌子識別碼。
     * @param seatIndex 要更新的玩家座位。
     * @param standingTileIds 立牌顯示順序。
     * @param drawnTileId 獨立摸牌位的牌；沒有時為 null。
     * @param melds 副露牌組與方向資訊。
     * @param setAsideTileIds 規則要求另置的牌。
     * @param isNewlyDrawn 是否播放這次摸牌的動畫。
     * @param newlyClaimedMeldTileIds 這次新取得、需要播放副露移動動畫的牌。
     * @param newlySetAsideTileIds 這次新另置、需要播放移動動畫的牌。
     */
    private fun presentPlayerArea(
        gameId: Uuid,
        seatIndex: Int,
        standingTileIds: List<Uuid>,
        drawnTileId: Uuid?,
        melds: List<MeldPresentation>,
        setAsideTileIds: List<Uuid>,
        isNewlyDrawn: Boolean,
        newlyClaimedMeldTileIds: Set<Uuid>,
        newlySetAsideTileIds: Set<Uuid>,
    ) {
        launchPendingPresentation(gameId, "publishPlayerTilesUpdated") {
            val resolved = resolveTableContext(gameId, "publishPlayerTilesUpdated") ?: return@launchPendingPresentation
            val tableState = gameRepository.getTableState(gameId) ?: return@launchPendingPresentation
            val cornerWidthsBySeat = tableCornerWidths.find(gameId) ?: cornerWidthsBySeat(gameId, tableState)
            val presentation = MahjongPlayerAreaPresentation(
                tableId = gameId,
                tableLocation = resolved.location,
                tableFacing = resolved.facing,
                seatLayout = MahjongTableSeatLayout.forPlayerCount(tableState.players.size),
                seatIndex = seatIndex,
                standingTileIds = standingTileIds,
                drawnTileId = drawnTileId,
                melds = melds.map {
                    MahjongMeldTileGroup(
                        it.type,
                        it.tileIds,
                        it.calledTileId,
                        it.sourceDirection,
                        it.allTilesFaceDown,
                        it.faceDownTileIds,
                    )
                },
                cornerWidth = cornerWidthsBySeat[seatIndex] ?: 0.0,
                animateDrawnTile = isNewlyDrawn,
                animatedMeldClaimTileIds = newlyClaimedMeldTileIds,
                setAsideTileIds = setAsideTileIds,
                animatedSetAsideTileIds = newlySetAsideTileIds,
            )
            playerAreaPresenter.present(presentation)
        }
    }

    /**
     * 清除整桌所有玩家的手牌/摸牌位/副露/規則桌面物件/局況顯示呈現——回房間等清空情境使用（見
     * `ReturnToRoomUseCase`），沒有座位分組資料可傳，直接呼叫 [playerAreaPresenter]／
     * [tablePropPresenter]／[roundInfoPresenter] 各自的 `clear()`（以 `managedTableId` 範圍搜尋清除，
     * 不需要逐座位資料）。
     */
    override fun clearPlayerTiles(gameId: Uuid) {
        if (serverHolder.current() == null) {
            logger.warn("clearPlayerTiles gameId={} skipped: no active server", gameId)
            return
        }
        scope.launch(dispatchers.main) {
            val location = tableLocationRegistry.get(gameId)?.location
            if (location == null) {
                logger.warn("clearPlayerTiles gameId={} skipped: no known table location", gameId)
                return@launch
            }
            playerAreaPresenter.clear(gameId, location)
            tablePropPresenter.clear(gameId, location)
            tableCornerWidths.clear(gameId)
            roundInfoPresenter.clear(gameId, location)
            playerInfoPresenter.clear(gameId, location)
        }
    }

    /** 跟 [publishOpeningRolls] 同理，座位傳送也需要碰觸世界／entity，一併丟回伺服器主執行緒執行。 */
    override fun publishGameStarted(gameId: Uuid, seatedPlayerIds: List<Uuid>) {
        if (serverHolder.current() == null) return
        scope.launch(dispatchers.main) { seatingPresenter.present(gameId, seatedPlayerIds) }
    }

    /**
     * 一般回合動作，即使 [newlyDiscardedTileId] 非 `null` 觸發捨牌動畫，那段動畫本身的排程完全交給
     * `FabricMahjongDiscardPresenter` 內部處理，理由同 [publishPlayerTilesUpdated] 的 `isNewlyDrawn`
     * 同款設計；但這裡的 entity 操作仍是丟回伺服器主執行緒非同步執行，一樣需要
     * [busyTracker.beginPending] 的 lease 覆蓋「已排定呈現、entity 還沒真正生成/
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
        launchPendingPresentation(gameId, "publishDiscardPileUpdated") {
            val resolved = resolveTableContext(gameId, "publishDiscardPileUpdated") ?: return@launchPendingPresentation
            val state = gameRepository.getTableState(gameId) ?: return@launchPendingPresentation

            val presentation = MahjongDiscardPresentation(
                tableId = gameId,
                tableLocation = resolved.location,
                tableFacing = resolved.facing,
                seatLayout = MahjongTableSeatLayout.forPlayerCount(state.players.size),
                seatIndex = seatIndex,
                discardTileIds = discardTileIds,
                sidewaysMarkedTileId = sidewaysMarkedTileId,
                newlyDiscardedTileId = newlyDiscardedTileId,
            )
            discardPresenter.present(presentation)
        }
    }

    /**
     * 讀取贏家目前的權威手牌（[gameRepository]，唯讀，不寫回任何 `TableState`）算出強制理牌重排的目標
     * 順序（`Hand.organize`，不論贏家原本是否啟用自動整理手牌），委託
     * [playerAreaPresenter.presentWinCelebration] 排定「重排 → （自摸牌單獨倒下 →）等待 → 立牌一起
     * 倒下」序列，最後把算出來的「立牌全部倒下完成」絕對 game time 交給 [effectScheduler] 接續排定
     * 降臨特效——特效鎖定的目標位置固定是 [WinCelebrationRequest.winningTileId] 目前所在座標：自摸時它已經併入贏家手牌、
     * 停在整理後的格位；榮和／搶槓時它仍在放銃者的牌河或副露區，天然就是「胡牌張目前所在座標」，
     * 不需要額外判斷。
     *
     * 同一請求包含所有贏家，逐位排定理牌與倒牌，再以最晚的倒牌完成時間排定一次共用特效。
     * 一炮多響的贏家共用 [WinCelebrationRequest.winningTileId] 指定的和牌張。
     */
    override fun publishWinCelebration(gameId: Uuid, request: WinCelebrationRequest) {
        publish(gameId, "publishWinCelebration", blocksTable = true) { resolved, state, startAt ->
            runCelebration(gameId, resolved, state, request, startAt)?.endGameTime
                ?.also { resolved.table.extendPresentationUntil(it) }
        }
    }

    /** 通知平台顯示逐位贏家詳情與共用分數排行。 */
    override fun publishWinSettlement(gameId: Uuid, request: WinSettlementPresentationRequest) {
        roundResultSender.sendWin(gameId, request, roundContinues = false)
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
     * - `roundContinues == true`：只有選出的展示定義要求等待（[WinCelebrationShowcaseDefinition.pausesContinuingRound]）
     *   時，showcase 那一段才延展整桌共用時間軸（暫停玩家／AI／強制自動操作／決策計時器）；一般倒牌特效、
     *   不要求等待的 showcase 與**整個結算面板**都只延展中途胡牌時間軸，不阻塞其他人。
     *
     * `roundContinues == true` 時，整段結束後一律把贏家真實手牌收尾（恢復可見、蓋成牌背）——即使這次
     * 兩個請求都是 null（`NONE` 模式）也一樣，否則已完成玩家的手牌會一直立在桌上。
     */
    override fun publishWinPresentation(gameId: Uuid, request: WinPresentationRequest) {
        roundResultSender.sendWin(gameId, request.settlement, request.roundContinues)
        // 開發用的一次性 showcase 覆寫必須在 blocksTable 判定之前套用，之後
        // 整段流程（阻塞判定、兩條時間軸的切分、收尾恢復可見的範圍）才會一致，見
        // DebugWinShowcaseOverride KDoc。正式產物裡這一步永遠是原樣回傳。
        val effectiveRequest = request.copy(celebration = debugWinShowcaseOverride.applyTo(gameId, request.celebration))
        val pausesContinuingRound = showcaseScheduler.pausesContinuingRound(effectiveRequest.celebration)
        val blocksTable = !effectiveRequest.roundContinues || pausesContinuingRound
        publish(gameId, "publishWinPresentation", blocksTable) { resolved, state, startAt ->
            var cursor = startAt
            val celebration = runCelebration(gameId, resolved, state, effectiveRequest.celebration, cursor)
            celebration?.let { cursor = it.endGameTime }
            val settlementEnd = runSettlement(gameId, resolved, state, effectiveRequest.settlement, cursor)
            settlementEnd?.let { cursor = it }

            if (effectiveRequest.roundContinues) {
                // 只有要求等待的 showcase 才擋住全桌；其餘（含結算面板）走不阻塞的那條時間軸。
                if (pausesContinuingRound) celebration?.showcaseEndGameTime?.let { resolved.table.extendPresentationUntil(it) }
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
     * 在主執行緒取得最新桌況與桌子，從兩條呈現時間軸中較晚的時間銜接演出。
     * 找不到資料時略過，執行區塊失敗時記錄警告。
     *
     * @param gameId 桌子識別碼。
     * @param operation 診斷記錄與忙碌標記使用的操作名稱。
     * @param blocksTable 是否在工作排程至完成期間阻止整桌操作。
     * @param block 接收桌子情境、最新桌況與最早開始時間的呈現工作；回傳值不由此函式使用。
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
        launchPresentation(
            gameId = gameId,
            operation = operation,
            blocksTable = blocksTable,
            block = presentation@{
                try {
                    val resolved = resolveTableContext(gameId, operation) ?: return@presentation
                    val state = gameRepository.getTableState(gameId) ?: return@presentation
                    val startAt = maxOf(
                        resolved.world.time,
                        resolved.table.presentationBusyUntilGameTime,
                        resolved.table.continuingWinPresentationBusyUntilGameTime,
                    )
                    block(resolved, state, startAt)
                } catch (cause: Exception) {
                    logger.warn("Failed to run {} for gameId={}", operation, gameId, cause)
                }
            },
        )
    }

    /**
     * 將呈現工作排到主執行緒；同批開局已失敗時，不再執行尚未開始的工作。
     * 取消向外傳遞；其他失敗會清除開局暫存資料，並標記該批開局失敗。
     * 沒有開局識別碼時，例外直接交給協程處理。
     *
     * @param gameId 桌子識別碼。
     * @param stage 開局階段名稱，用於失敗診斷。
     * @param openingOperation 所屬開局操作；一般呈現工作可為 null。
     * @param pendingOperation 需要整桌忙碌標記時使用的操作名稱；null 表示不取得標記。
     * @param block 要執行的呈現工作。
     */
    private fun launchOpeningStage(
        gameId: Uuid,
        stage: String,
        openingOperation: TableOpeningPresentationOperationTracker.Ticket?,
        pendingOperation: String? = null,
        block: suspend () -> Unit,
    ) {
        launchPresentation(
            gameId = gameId,
            operation = pendingOperation ?: stage,
            blocksTable = pendingOperation != null,
            block = openingStage@{
                try {
                    if (!openingOperations.mayRun(openingOperation)) {
                        logger.debug(
                            "Opening presentation stage skipped: gameId={} generation={} stage={}",
                            gameId,
                            openingOperation?.generation,
                            stage,
                        )
                        return@openingStage
                    }
                    block()
                } catch (cause: CancellationException) {
                    throw cause
                } catch (cause: Throwable) {
                    if (openingOperation == null) throw cause
                    throw openingOperations.fail(openingOperation, stage, cause) { openingState.clear(gameId) }
                }
            },
        )
    }

    /**
     * 依阻塞設定選擇主執行緒工作是否取得整桌忙碌標記。
     *
     * @param gameId 桌子識別碼。
     * @param operation 忙碌標記的操作名稱。
     * @param blocksTable 是否在排程至完成期間阻止整桌操作。
     * @param block 要在主執行緒執行的工作。
     * @return 已建立的協程工作。
     */
    private fun launchPresentation(
        gameId: Uuid,
        operation: String,
        blocksTable: Boolean,
        block: suspend () -> Unit,
    ): Job = if (blocksTable) {
        launchPendingPresentation(gameId, operation, block)
    } else {
        scope.launch(dispatchers.main) { block() }
    }

    /**
     * 同步取得整桌忙碌標記，再將工作排到主執行緒。
     * 工作完成、取消或啟動失敗時，都釋放自己的標記。
     *
     * @param gameId 桌子識別碼。
     * @param operation 忙碌標記的操作名稱。
     * @param block 要在主執行緒執行的工作。
     * @return 已建立的協程工作。
     * @throws Throwable 建立工作失敗時，釋放標記後重新拋出原始例外。
     */
    private fun launchPendingPresentation(
        gameId: Uuid,
        operation: String,
        block: suspend () -> Unit,
    ): Job {
        val lease = busyTracker.beginPending(gameId, operation)
        return try {
            scope.launch(dispatchers.main) { block() }.also { job ->
                job.invokeOnCompletion { lease.complete() }
            }
        } catch (cause: Throwable) {
            lease.complete()
            throw cause
        }
    }

    /**
     * [runCelebration] 的結果。
     *
     * @property endGameTime 整段慶祝演出的結束時間。
     * @property showcaseEndGameTime 役滿 showcase 的結束時間；沒播 showcase 時為 null。
     * @property showcaseHiddenTileIds 實際交接給 showcase 舞台、因而被設成隱形的真實牌（手牌、副露與和牌張）——
     * 收尾時要恢復可見的就是**這一組**，不能拿手牌全集去猜（沒有展示的贏家不會被碰過）。沒播 showcase
     * 時為空集合。
     */
    private data class CelebrationSegment(
        val endGameTime: Long,
        val showcaseEndGameTime: Long?,
        val showcaseHiddenTileIds: Set<Uuid>,
    )

    /**
     * 依序排定理牌、倒牌、慶祝特效及符合條件的展示；呼叫端負責延展時間軸。
     *
     * @param gameId 桌子識別碼。
     * @param resolved 已解析的桌子與世界。
     * @param state 用來取得贏家手牌、牌面與規則的桌況。
     * @param request 贏家、和牌張及展示提示。
     * @param earliestStartGameTime 演出最早可開始的絕對世界遊戲時間。
     * @return 各段結束時間與展示隱藏的牌；贏家座位無效或未取得倒牌結束時間時為 null。
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
        val cornerWidthsBySeat = cornerWidthsBySeat(gameId, state)
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
                    faceDownTileIds = p.faceDownTileIds,
                )
            }
            val presentation = MahjongWinCelebrationPresentation(
                tableId = gameId,
                tableLocation = resolved.location,
                tableFacing = resolved.facing,
                seatLayout = MahjongTableSeatLayout.forPlayerCount(state.players.size),
                seatIndex = requestedWinner.seatIndex,
                organizedStandingTileIds = organizedHand.tiles.map { it.id },
                melds = melds,
                cornerWidth = cornerWidthsBySeat[requestedWinner.seatIndex] ?: 0.0,
                setAsideTileCount = module.setAsideTiles(winner).size,
                winningTileId = request.winningTileId,
                isTsumo = request.isTsumo,
                earliestStartGameTime = earliestStartGameTime,
            )
            val result = playerAreaPresenter.presentWinCelebration(presentation)
            result.handLaydownEndGameTime?.let { endTime ->
                handLaydownEndGameTime = maxOf(handLaydownEndGameTime ?: endTime, endTime)
            }
            requestedWinner.seatIndex to (organizedHand.tiles to melds)
        }
        if (handLaydownEndGameTime != null) {
            val effectStartGameTime = checkNotNull(handLaydownEndGameTime) + MahjongTileTableLayout.WIN_PRE_EFFECT_DELAY_TICKS
            val effectEndGameTime = effectStartGameTime + MahjongTileTableLayout.WIN_EFFECT_DURATION_TICKS
            effectScheduler.schedule(
                world = resolved.world,
                tableId = gameId,
                targetTileId = request.winningTileId,
                startGameTime = effectStartGameTime,
                endGameTime = effectEndGameTime,
            )
            val eligibleWings = request.winners.filter { showcaseScheduler.hasShowcase(it.cueIds) }.map { requestedWinner ->
                val (standingTiles, melds) = organizedBySeat.getValue(requestedWinner.seatIndex)
                FabricWinCelebrationShowcaseScheduler.Wing(
                    seatIndex = requestedWinner.seatIndex,
                    cueIds = requestedWinner.cueIds,
                    cards = showcaseWingCards(
                        standingTileIds = standingTiles.map { it.id }.filterNot { it == request.winningTileId },
                        melds = melds,
                        assetKeyOf = { id -> state.findTile(id)?.tile?.toAssetKey(tileAssetRegistry) },
                    ),
                    standYaw = MahjongTileTableLayout.seatYaw(resolved.facing, MahjongTableSeatLayout.forPlayerCount(state.players.size), requestedWinner.seatIndex),
                )
            }
            val winningTile = state.findTile(request.winningTileId)
            // showcase 會把這些真實牌設成隱形交接給舞台代理（見 FabricWinCelebrationShowcaseScheduler），
            // 收尾時要恢復可見的就是這一組。
            val showcaseHiddenTileIds = if (eligibleWings.isNotEmpty() && winningTile != null) {
                eligibleWings.flatMapTo(mutableSetOf()) { wing -> wing.cards.map { it.tileId } } +
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

    /**
     * 將和牌結算面板排在指定時間之後，牌面資產由傳入桌況查詢。
     *
     * @param gameId 桌子識別碼。
     * @param resolved 已解析的桌子與世界。
     * @param state 用來查詢牌面資產的桌況。
     * @param request 贏家詳情與分數排行。
     * @param earliestStartGameTime 面板最早可開始的絕對世界遊戲時間。
     * @return 面板結束時間；排程器未建立面板時為 null。
     */
    private fun runSettlement(
        gameId: Uuid,
        resolved: ResolvedTableContext,
        state: TableState,
        request: WinSettlementPresentationRequest,
        earliestStartGameTime: Long,
    ): Long? {
        val tileIds = buildSet {
            request.winners.forEach { winner ->
                addAll(winner.standingTileIds)
                winner.melds.forEach { addAll(it.tileIds) }
                winner.winningTileId?.let(::add)
                winner.detailFields.forEach { field ->
                    (field.value as? WinSettlementDetailValue.Tiles)?.let { addAll(it.tileIds) }
                }
            }
        }
        val assets = state.tileAssetKeysById(tileIds, tileAssetRegistry)
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
     * 在中途和牌演出結束後，還原展示期間隱藏的真實牌，再將贏家立牌蓋成牌背。
     * 副露與榮和張不隨立牌蓋牌；收尾動畫不阻塞其他玩家，並寫入各牌的持久化動畫佇列。
     *
     * @param resolved 已解析的桌子與世界。
     * @param state 用來取得贏家手牌的桌況。
     * @param request 決定哪些贏家需要收尾的呈現請求。
     * @param celebration 慶祝演出結果；null 表示沒有展示所隱藏的牌。
     * @param endGameTime 開始收尾的絕對世界遊戲時間。
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

    /**
     * 依 UUID 查詢桌子所在世界的牌實體。
     *
     * @param tileId 要查詢的實體識別碼。
     * @return 對應的麻將牌實體；不存在或實體類型不符時為 null。
     */
    private fun ResolvedTableContext.findTile(tileId: Uuid): MahjongTileEntity? = world.getEntity(tileId.toJavaUuid()) as? MahjongTileEntity

    /**
     * 由維度識別碼查詢目前伺服器的世界。
     *
     * @param location 提供維度識別碼的桌子位置。
     * @return 對應世界；識別碼無效、沒有伺服器或世界未載入時為 null。
     */
    private fun resolveWorld(location: TableLocation): ServerWorld? {
        val identifier = Identifier.tryParse(location.dimensionId) ?: return null
        val worldKey = RegistryKey.of(RegistryKeys.WORLD, identifier)
        return serverHolder.current()?.getWorld(worldKey)
    }

    /**
     * 查詢桌況所採用規則的桌面物件描述。
     *
     * @param tableState 決定規則模組的桌況。
     * @return 已登記的描述；規則未登記時為 null。
     */
    private fun describerFor(tableState: TableState): TablePropDescriber? = tablePropDescriberRegistry.find(moduleRegistry.getModule(tableState.config).id)

    /**
     * 取得目前桌面物件占用的座位角落寬度，優先使用已記錄的實際擺放結果。
     *
     * @param gameId 桌子識別碼。
     * @param tableState 沒有寬度紀錄時，用來查詢規則描述的桌況。
     * @return 以座位為鍵的寬度；沒有紀錄且沒有桌況時為空集合。
     */
    private fun cornerWidthsBySeat(gameId: Uuid, tableState: TableState?): Map<Int, Double> = tableCornerWidths.find(gameId) ?: tableState?.let { state -> describerFor(state).cornerWidthsBySeat(state) }.orEmpty()

    /**
     * 從同一份規則配置與完整玩家名單重建牌牆的桌邊對應。
     *
     * @param state 此局完整權威桌況，只讀取規則配置與座位資料。
     * @param dealerSeatIndex 此次牌牆呈現使用的莊家座位。
     * @return 與砌牆、開門及後續更新共用的桌邊對應。
     */
    private fun wallSideLayout(state: TableState, dealerSeatIndex: Int): MahjongTileWallSideLayout {
        val arrangement = checkNotNull(moduleRegistry.getModule(state.config).createWallLayout(state.players.size)) {
            "Rule provides no wall layout"
        }.sideArrangement(dealerSeatIndex)
        return MahjongTileWallSideLayout.resolve(
            arrangement = arrangement,
            dealerSide = MahjongTableSeatLayout.forPlayerCount(state.players.size).sideOf(dealerSeatIndex),
        )
    }

    /**
     * 在伺服器主執行緒解析位置、世界、水平朝向及對應的麻將桌方塊實體。
     *
     * @param gameId 用來查詢位置並驗證桌子身分的識別碼。
     * @param methodName 寫入診斷記錄的呼叫端名稱。
     * @return 完整的桌子情境；任何必要資料缺失或身分不符時記錄警告並回傳 null。
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

    /**
     * 已確認身分的桌子及其世界位置。
     *
     * @property world 桌子所在世界。
     * @property location 索引中記錄的桌子位置。
     * @property facing 桌子的水平朝向。
     * @property table 身分與請求識別碼相符的麻將桌方塊實體。
     */
    private data class ResolvedTableContext(
        val world: ServerWorld,
        val location: TableLocation,
        val facing: MahjongTableFacing,
        val table: MahjongTableBlockEntity,
    )

    /** 正式擲骰／開局呈現使用的固定參數。 */
    private companion object
}
