package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.config.dealBatchSizes
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer.buildOpenedWall
import com.doublemoon1119.mahjongcraft.logic.table.layout.InitialPhysicalWallLayoutContext
import com.doublemoon1119.mahjongcraft.logic.table.layout.InitialPhysicalWallLayoutDecision
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayoutResult
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.layout.createInitialLayoutValidated
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollSource
import com.doublemoon1119.mahjongcraft.logic.table.opening.OpeningDiceRoll
import com.doublemoon1119.mahjongcraft.logic.table.opening.RandomDiceRollSource
import com.doublemoon1119.mahjongcraft.logic.table.opening.RoundOpeningContext
import com.doublemoon1119.mahjongcraft.logic.table.opening.RoundOpeningStep
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import com.doublemoon1119.mahjongcraft.logic.table.seat.SeatWindAssignmentContext
import com.doublemoon1119.mahjongcraft.logic.table.seat.assignValidated
import kotlin.uuid.Uuid

/**
 * 建立一場新對局的初始桌況。
 *
 * 洗座位、擲骰開門、建立牌山、輪流發初始手牌屬於規則無關的機械過程，交由此處統一實作，
 * 避免每個 [MahjongRuleModule] 各自重複實作一次同樣的流程。
 */
object GameInitializer {
    /**
     * 依據 [module] 建立一場新對局的初始 [TableState]。
     *
     * @param id 對局的唯一識別碼。
     * @param playerIds 參與對局的玩家 Uuid 列表（尚未分配座位，內部會隨機排序）。
     * @param module 該對局採用的規則模組，提供牌山工廠、牌河實作與規則配置。
     * @param diceRollSource 權威擲骰來源；正式流程使用隨機六面骰，測試可注入固定來源。
     * @return 已完成洗牌、（若規則支援）擲骰開門、發牌、分數初始化的新結果，含權威 [TableState] 與
     * 供呼叫端處理的牌牆結構資料；完整擲骰記錄保存在回傳桌況的
     * [TableState.openingDiceRolls]。
     * @throws IllegalArgumentException 當玩家人數不在該規則允許的範圍內時拋出。
     */
    fun initialize(
        id: Uuid,
        playerIds: List<Uuid>,
        module: MahjongRuleModule<*>,
        diceRollSource: DiceRollSource = RandomDiceRollSource,
    ): GameInitializationResult {
        require(playerIds.size in module.config.minPlayers..module.config.maxPlayers) {
            "Player count ${playerIds.size} out of range for this rule config " +
                "(${module.config.minPlayers}..${module.config.maxPlayers})"
        }

        val shuffledPlayerIds = playerIds.shuffled()
        val opening = module.resolveRoundOpening(
            playerIdsInTurnOrder = shuffledPlayerIds,
            firstRound = true,
            provisionalPlayerId = shuffledPlayerIds.first(),
            fixedDealerPlayerId = null,
            diceRollSource = diceRollSource,
        )
        val dealerIndex = shuffledPlayerIds.indexOf(opening.dealerPlayerId)
        val dealOrderPlayerIds = List(shuffledPlayerIds.size) { offset ->
            shuffledPlayerIds[(dealerIndex + offset) % shuffledPlayerIds.size]
        }
        val openedWall = module.buildOpenedWall(shuffledPlayerIds.size, opening.wallOpening)
        val seatWinds = module.assignSeatWinds(shuffledPlayerIds, opening.dealerPlayerId, openedWall, opening.diceRolls)
        val (hands, remainingWall) = module.dealInitialHands(openedWall.wall, dealOrderPlayerIds)
        val handsByPlayerId = dealOrderPlayerIds.zip(hands).toMap()
        val physicalLayouts = openedWall.createPhysicalLayouts(module, remainingWall)
        val players = shuffledPlayerIds.mapIndexed { index, playerId ->
            MahjongPlayer(
                id = playerId,
                initialSeatIndex = (index - dealerIndex).mod(shuffledPlayerIds.size),
                seatWind = seatWinds.getValue(playerId),
                hand = Hand(tiles = handsByPlayerId.getValue(playerId)),
                discardPile = module.createDiscardPile(),
                playerRuleState = module.createInitialPlayerRuleState(),
            )
        }

        val tableState = TableState(
            id = id,
            players = players,
            config = module.config,
            tileWall = remainingWall,
            dealerPlayerId = opening.dealerPlayerId,
            roundPosition = module.createMatchProgressionPolicy().initialPosition(players.size),
            currentPlayerIndex = dealerIndex,
            dynamicRuleState = module.createInitialDynamicState(),
            wallOpening = openedWall.wallOpening,
            reservedWallTiles = openedWall.reservedWallTiles,
            physicalWallLayout = physicalLayouts?.current,
            openingDiceRolls = opening.diceRolls,
        ).init()

        return GameInitializationResult(
            tableState = tableState,
            wallStructure = openedWall.structure,
            initialPhysicalWallLayout = physicalLayouts?.complete,
        )
    }

    /**
     * 依連莊/過莊判定的結果（[roundAdvancement]），建立下一局的桌況：重新擲骰開門、重新建牌山、
     * 重新發手牌，並重置每局狀態（手牌、牌河、規則特有的玩家狀態），但保留跨局狀態（分數、局數/
     * 本場數/場風/各玩家方位，皆直接沿用 [roundAdvancement] 算好的結果）。
     *
     * 動態桌況狀態交由 [MahjongRuleModule.createNextRoundDynamicState] 由 [previousDynamicRuleState]
     * 推導，不整包沿用——同一個物件可能同時保存跨局資料（例如尚未被收下的供託）與只屬於單局的資料
     * （例如本局已完成的槓次），哪些延續只有規則自己知道。供託本身是否歸零仍由胡牌結算階段的
     * [MahjongRuleModule.collectStickPot] 決定，這裡單純延續、不重新判斷。
     *
     * 座位順序（[roundAdvancement] 的 `players` 列表順序）不會重新洗牌，只有發牌本身、每局狀態
     * 會全部重來——跟 [initialize] 開新對局時「連座位順序都重新洗牌、分數歸零」不同。連莊仍是新的
     * 一局，也會重新擲骰開門。
     *
     * @param gameId 對局 Uuid（延續同一場對局，不是新對局）。
     * @param roundAdvancement 連莊/過莊判定後的結果（見 [TableState.advanceRound]），提供下一局的
     *        玩家列表與局位資料；玩家自風會依規則 policy 重新指派。
     * @param previousDynamicRuleState 上一局結束時的動態桌況狀態（例如供託／立直棒數量）。
     * @param module 該對局採用的規則模組。
     * @param diceRollSource 權威擲骰來源；正式流程使用隨機六面骰，測試可注入固定來源。
     * @return 已完成重新擲骰開門、重新建牌山、發牌，並套用連莊/過莊結果的新結果，代表下一局的起始
     * 狀態，含權威 [TableState] 與供呼叫端處理的牌牆結構資料；完整擲骰記錄保存在
     * 回傳桌況的 [TableState.openingDiceRolls]。
     */
    fun startNextRound(
        gameId: Uuid,
        roundAdvancement: RoundAdvancementResult,
        previousDynamicRuleState: DynamicRuleState?,
        module: MahjongRuleModule<*>,
        diceRollSource: DiceRollSource = RandomDiceRollSource,
    ): GameInitializationResult {
        val playerIdsInTurnOrder = roundAdvancement.players.map { it.id }
        val opening = module.resolveRoundOpening(
            playerIdsInTurnOrder = playerIdsInTurnOrder,
            firstRound = false,
            provisionalPlayerId = roundAdvancement.dealerPlayerId,
            fixedDealerPlayerId = roundAdvancement.dealerPlayerId,
            diceRollSource = diceRollSource,
        )
        val openedWall = module.buildOpenedWall(roundAdvancement.players.size, opening.wallOpening)
        // TableState.players 順序整場對局固定不變、不是從莊家開始排的，發牌輪轉順序需要另外把玩家
        // 列表依莊家旋轉過（見 dealInitialHands KDoc 對 playerIds 順序的要求），發完牌後再依原本固定
        // 順序組回 players 列表，不能直接沿用旋轉過的順序。
        val dealerIndex = roundAdvancement.players.indexOfFirst { it.id == roundAdvancement.dealerPlayerId }
        val dealOrderPlayerIds = List(roundAdvancement.players.size) { offset ->
            roundAdvancement.players[(dealerIndex + offset) % roundAdvancement.players.size].id
        }
        val (hands, remainingWall) = module.dealInitialHands(openedWall.wall, dealOrderPlayerIds)
        val physicalLayouts = openedWall.createPhysicalLayouts(module, remainingWall)
        val handsByPlayerId = dealOrderPlayerIds.zip(hands).toMap()
        val seatWinds = module.assignSeatWinds(
            playerIdsInTurnOrder,
            roundAdvancement.dealerPlayerId,
            openedWall,
            opening.diceRolls,
        )
        val players = roundAdvancement.players.map { player ->
            player.copy(
                seatWind = seatWinds.getValue(player.id),
                hand = Hand(tiles = handsByPlayerId.getValue(player.id)),
                discardPile = module.createDiscardPile(),
                playerRuleState = module.createInitialPlayerRuleState(),
                passedTilesInRound = emptySet(),
                actionHistory = emptyList(),
            )
        }

        val tableState = TableState(
            id = gameId,
            players = players,
            config = module.config,
            tileWall = remainingWall,
            dealerPlayerId = roundAdvancement.dealerPlayerId,
            prevalentWind = roundAdvancement.prevalentWind,
            roundNumber = roundAdvancement.roundNumber,
            roundPosition = roundAdvancement.roundPosition,
            comboCount = roundAdvancement.comboCount,
            currentPlayerIndex = dealerIndex,
            dynamicRuleState = module.createNextRoundDynamicState(previousDynamicRuleState),
            wallOpening = openedWall.wallOpening,
            reservedWallTiles = openedWall.reservedWallTiles,
            physicalWallLayout = physicalLayouts?.current,
            openingDiceRolls = opening.diceRolls,
        )

        return GameInitializationResult(
            tableState = tableState,
            wallStructure = openedWall.structure,
            initialPhysicalWallLayout = physicalLayouts?.complete,
        )
    }

    /**
     * 執行規則提供的開局程序，驗證每次要求並收集權威點數。
     *
     * @param playerIdsInTurnOrder 整場固定的玩家回合順序。
     * @param firstRound 是否正在選定整場的起家。
     * @param provisionalPlayerId 程序開始時的暫定擲骰玩家。
     * @param fixedDealerPlayerId 換局時已由賽程決定的莊家；選起家時為 null。
     * @param diceRollSource 產生每次指定顆數的六面骰點數。
     * @return 最終莊家、開門位置與本局完整擲骰記錄。
     */
    private fun MahjongRuleModule<*>.resolveRoundOpening(
        playerIdsInTurnOrder: List<Uuid>,
        firstRound: Boolean,
        provisionalPlayerId: Uuid,
        fixedDealerPlayerId: Uuid?,
        diceRollSource: DiceRollSource,
    ): RoundOpeningResolution {
        val procedure = createRoundOpeningProcedure(playerIdsInTurnOrder.size)
            ?: return RoundOpeningResolution(fixedDealerPlayerId ?: provisionalPlayerId, null, emptyList())
        require(procedure.supportedDiceCounts.all { it > 0 }) {
            "Opening procedure must declare positive supported dice counts"
        }
        val rolls = mutableListOf<OpeningDiceRoll>()
        for (stepIndex in 0..MAX_OPENING_ROLLS) {
            when (
                val step = procedure.next(
                    RoundOpeningContext(
                        playerIdsInTurnOrder = playerIdsInTurnOrder.toList(),
                        firstRound = firstRound,
                        provisionalPlayerId = provisionalPlayerId,
                        fixedDealerPlayerId = fixedDealerPlayerId,
                        rolls = rolls.toList(),
                    ),
                )
            ) {
                is RoundOpeningStep.Complete -> {
                    require(step.result.dealerPlayerId in playerIdsInTurnOrder) {
                        "Opening procedure selected a player outside the table"
                    }
                    if (!firstRound) {
                        require(step.result.dealerPlayerId == fixedDealerPlayerId) {
                            "A later-round opening procedure must keep the progression dealer"
                        }
                    }
                    return RoundOpeningResolution(step.result.dealerPlayerId, step.result.wallOpening, rolls.toList())
                }

                is RoundOpeningStep.Roll -> {
                    require(stepIndex < MAX_OPENING_ROLLS) {
                        "Opening procedure exceeded the maximum number of rolls: $MAX_OPENING_ROLLS"
                    }
                    require(step.request.playerId in playerIdsInTurnOrder) {
                        "Opening procedure requested a player outside the table"
                    }
                    require(step.request.diceCount in procedure.supportedDiceCounts) {
                        "Opening procedure requested unsupported dice count: ${step.request.diceCount}"
                    }
                    val dice = diceRollSource.roll(step.request.diceCount)
                    require(dice.values.size == step.request.diceCount) {
                        "Dice source returned ${dice.values.size} dice, expected ${step.request.diceCount}"
                    }
                    rolls += OpeningDiceRoll(rolls.size, step.request.playerId, dice)
                }
            }
        }
        error("Opening procedure did not complete")
    }

    /**
     * 執行完本局開局程序後的結果。
     *
     * @property dealerPlayerId 本局莊家。
     * @property wallOpening 開門位置；未使用開門時為 null。
     * @property diceRolls 依執行順序排列的完整骰子結果。
     */
    private data class RoundOpeningResolution(
        val dealerPlayerId: Uuid,
        val wallOpening: WallOpening?,
        val diceRolls: List<OpeningDiceRoll>,
    )

    /**
     * 依規則 policy 指派並驗證本局所有玩家的自風。
     *
     * @param playerIdsInTurnOrder 依回合順序排列的玩家 ID。
     * @param dealerPlayerId 本局莊家 ID。
     * @param openedWall 已建立的牌牆及其開門資料。
     * @param diceRolls 本局開局程序的完整擲骰記錄。
     * @return 每位玩家對應的自風。
     */
    private fun MahjongRuleModule<*>.assignSeatWinds(
        playerIdsInTurnOrder: List<Uuid>,
        dealerPlayerId: Uuid,
        openedWall: OpenedWall,
        diceRolls: List<OpeningDiceRoll>,
    ): Map<Uuid, Wind> = createSeatWindAssignmentPolicy().assignValidated(
        SeatWindAssignmentContext(
            playerIdsInTurnOrder = playerIdsInTurnOrder,
            dealerPlayerId = dealerPlayerId,
            diceRolls = diceRolls,
            wallOpening = openedWall.wallOpening,
        ),
    )

    /**
     * 建立洗牌後的牌山，依開局程序已決定的開門位置排列摸牌順序，並切出規則保留牌。
     *
     * 此處不擲骰，也不重新決定開門位置。未指定開門位置時直接使用原始洗牌結果，沒有保留牌或布局；
     * 指定開門位置時規則必須提供牌牆布局，不能默默忽略已決定的結果。
     * [TileWallLayoutResult.drawOrder] 與 [TileWallLayoutResult.reservedWallTiles] 分開保存，
     * 回傳的 [OpenedWall.wall] 只包含一般摸牌序列，後續成為 [TableState.tileWall]。
     *
     * @param playerCount 本局完整玩家名單的人數。
     * @param wallOpening 開局程序已決定的最終開門位置；未使用開門時為 null。
     * @return 已整理的摸牌牌山，以及可用時的開門、保留牌和牌牆布局資料。
     */
    private fun MahjongRuleModule<*>.buildOpenedWall(
        playerCount: Int,
        wallOpening: WallOpening?,
    ): OpenedWall {
        val shuffledWall = createWallFactory(playerCount).create()
        val layout = createWallLayout(playerCount)
        if (wallOpening == null) {
            return OpenedWall(
                wall = shuffledWall,
                wallOpening = null,
                reservedWallTiles = emptyList(),
                structure = null,
                layoutResult = null,
            )
        }

        val layoutResult = requireNotNull(layout) {
            "A round opening requires a wall layout"
        }.resolve(shuffledWall.getAllTiles(), wallOpening)
        return OpenedWall(
            wall = TileWall(layoutResult.drawOrder),
            wallOpening = wallOpening,
            reservedWallTiles = layoutResult.reservedWallTiles,
            structure = layoutResult.structure,
            layoutResult = layoutResult,
        )
    }

    /**
     * [buildOpenedWall] 的結果：已套用（或未套用）開門結果的一般摸牌序列、保留牌與開門位置，以及
     * 牌牆結構座標。
     *
     * @property wall 已套用開門結果的一般摸牌序列。
     * @property wallOpening 開門位置；規則未提供開門能力時為 null。
     * @property reservedWallTiles 不屬於一般摸牌序列的規則保留牌。
     * @property structure 依開門結果排列的牌牆結構座標；未建立布局時為 null。
     * @property layoutResult 開門布局結果；規則未提供所需布局能力時為 null。
     */
    private data class OpenedWall(
        val wall: TileWall,
        val wallOpening: WallOpening?,
        val reservedWallTiles: List<IdentifiedTile>,
        val structure: Map<Uuid, TileWallPosition>?,
        val layoutResult: TileWallLayoutResult?,
    ) {
        /**
         * 在初次發牌結果已知後建立完整初始布局與目前仍留在牌牆中的布局。
         *
         * @param module 提供牌牆實體布局 policy 的規則模組。
         * @param remainingWall 發牌後的一般摸牌牌山。
         * @return 完整初始牌牆布局與只保留仍在牌牆牌張的目前布局；沒有牌牆布局結果時為 null。
         * @throws IllegalStateException 規則拒絕建立初始布局時拋出。
         */
        fun createPhysicalLayouts(module: MahjongRuleModule<*>, remainingWall: TileWall): PhysicalLayouts? {
            val result = layoutResult ?: return null
            val retainedIds = (remainingWall.getAllTiles() + reservedWallTiles).mapTo(mutableSetOf()) { it.id }
            val opening = requireNotNull(wallOpening)
            val complete = when (
                val decision = module.createPhysicalWallLayoutPolicy().createInitialLayoutValidated(
                    InitialPhysicalWallLayoutContext(result, opening, retainedIds),
                )
            ) {
                is InitialPhysicalWallLayoutDecision.Completed -> decision.layout
                is InitialPhysicalWallLayoutDecision.Rejected -> error(
                    "Physical wall layout initialization was rejected: ${decision.reasonId}",
                )
            }
            return PhysicalLayouts(complete, complete.retainOnly(retainedIds))
        }
    }

    /**
     * 完整初始牌牆布局與發牌後桌況保存的目前布局。
     *
     * @property complete 包含整副牌的初始布局。
     * @property current 僅包含初次發牌後仍留在牌牆與保留牌區的布局。
     */
    private data class PhysicalLayouts(
        val complete: TileWallPhysicalLayout,
        val current: TileWallPhysicalLayout,
    )

    /**
     * 連續從牌山最前方摸取 [count] 張牌。
     *
     * @return 摸到的牌列表（依摸牌順序排列）與摸牌後的新 [TileWall] 實例。
     * @param count 要連續摸取的牌張數。
     * @throws IllegalStateException 當牌山在發牌途中就已摸盡時拋出。
     */
    private fun TileWall.drawN(count: Int): Pair<List<IdentifiedTile>, TileWall> {
        var wall = this
        val tiles = mutableListOf<IdentifiedTile>()
        repeat(count) {
            val (tile, newWall) = wall.draw()
            checkNotNull(tile) { "Tile wall ran out while dealing initial hands" }
            tiles += tile
            wall = newWall
        }
        return tiles to wall
    }

    /**
     * 依 [dealBatchSizes] 決定的節奏，輪流（不是讓某位玩家一次連續摸完整手牌）發
     * 初始手牌——每一輪 [playerIds] 依序各摸一批，模擬真實麻將「莊家先抓兩墩、換下一家抓兩墩、輪完
     * 一圈才回到莊家抓下一輪」的發牌方式，直到每位玩家都湊滿整手牌。
     *
     * [playerIds] 的順序即發牌輪轉順序，必須由呼叫端排成從莊家開始的固定順序；發牌完成後，呼叫端
     * 可以再依原本的固定座位順序組回玩家列表。
     *
     * `internal` 而非 `private`：讓同模組的 [GameInitializerTest] 能直接用可控制的 [TileWall] 測試
     * 輪轉順序本身，不需要透過完整 [initialize] 流程（牌山來自
     * [MahjongRuleModule.createWallFactory] 的隨機洗牌，測試端無法控制牌張順序，驗證不了輪轉節奏）。
     *
     * @return 依 [playerIds] 對應順序排列的手牌列表，與發牌後剩餘的牌山。
     * @param wall 開始發牌前的牌山。
     * @param playerIds 發牌輪轉順序；第一位玩家先拿牌。
     */
    internal fun MahjongRuleModule<*>.dealInitialHands(
        wall: TileWall,
        playerIds: List<Uuid>,
    ): Pair<List<List<IdentifiedTile>>, TileWall> {
        var remainingWall = wall
        val hands = List(playerIds.size) { mutableListOf<IdentifiedTile>() }
        config.dealBatchSizes().forEach { batchSize ->
            playerIds.indices.forEach { playerIndex ->
                val (tiles, newWall) = remainingWall.drawN(batchSize)
                remainingWall = newWall
                hands[playerIndex] += tiles
            }
        }
        return hands to remainingWall
    }

    /** 防止規則程序無限要求下一輪擲骰的上限。 */
    private const val MAX_OPENING_ROLLS: Int = 16
}
