package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.config.dealBatchSizes
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RIICHI_GAME_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardEntry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPosition
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.layout.InitialPhysicalWallLayoutContext
import com.doublemoon1119.mahjongcraft.logic.table.layout.InitialPhysicalWallLayoutDecision
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionContext
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionDecision
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayoutResult
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.layout.createInitialLayoutValidated
import com.doublemoon1119.mahjongcraft.logic.table.layout.resolveTransitionValidated
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import kotlin.uuid.Uuid

/** 供實體牌牆演進驗收使用的首批四人日麻權威情境。 */
object RiichiDebugGameScenarios {
    /** 所有內建日麻 debug 情境。 */
    val all: List<DebugGameScenario> = listOf(
        RiichiBeforeAnkanScenario("mahjongcraft:riichi_before_ankan_1", 0),
        RiichiBeforeAnkanScenario("mahjongcraft:riichi_before_ankan_2", 1),
        RiichiBeforeAnkanScenario("mahjongcraft:riichi_before_ankan_3", 2),
        RiichiBeforeAnkanScenario("mahjongcraft:riichi_before_ankan_4", 3),
        RiichiBeforeAnkanScenario("mahjongcraft:riichi_before_ankan_at_break_1", 0, AT_BREAK_WALL_OPENING),
        RiichiBeforeAnkanScenario("mahjongcraft:riichi_before_ankan_at_break_2", 1, AT_BREAK_WALL_OPENING),
        RiichiBeforeAnkanScenario("mahjongcraft:riichi_before_ankan_at_break_3", 2, AT_BREAK_WALL_OPENING),
        RiichiBeforeAnkanScenario("mahjongcraft:riichi_before_ankan_at_break_4", 3, AT_BREAK_WALL_OPENING),
        RiichiBeforeMinkanScenario("mahjongcraft:riichi_before_minkan_1"),
        RiichiBeforeMinkanScenario("mahjongcraft:riichi_before_minkan_at_break_1", AT_BREAK_WALL_OPENING),
        RiichiBeforeSuuchaRiichiScenario,
        RiichiWallOpeningScenario,
    )
}

/**
 * 情境預設的開門位置。
 *
 * 這個位置下保留牌軌道的頭端會被 `SingleSideReservedWallTrackPlanner` 的最小頭端限制推離開門點，王牌區
 * 因此不與活牌末端相鄰。
 */
private val DEFAULT_WALL_OPENING = WallOpening(wallSideOffsetFromDealer = 0, stacksFromRight = 8)

/**
 * 王牌區緊貼開門點、因此與活牌末端相鄰的開門位置。
 *
 * 王牌區與活牌區的分界、以及槓後補入牌與活牌末端的相對位置，只有在兩區相鄰時才會真正被檢驗；
 * [DEFAULT_WALL_OPENING] 下兩區隔得太遠，這類幾何問題不會顯現。
 */
private val AT_BREAK_WALL_OPENING = WallOpening(wallSideOffsetFromDealer = 0, stacksFromRight = 10)

/** 建立前三家已立直、呼叫者可合法宣告第四家立直的四人日麻情境。 */
private object RiichiBeforeSuuchaRiichiScenario : DebugGameScenario {
    override val id: String = "mahjongcraft:riichi_before_suucha_riichi"

    override fun build(context: DebugGameScenarioContext): DebugGameScenarioResult {
        val currentGame = context.currentGame
        require(currentGame.tableState.players.size == PLAYER_COUNT) { "Riichi debug scenarios require four players" }
        require(currentGame.tableState.config is RiichiRuleConfig) { "Riichi debug scenarios require a Riichi game" }
        val invokingPlayerIndex = currentGame.tableState.players.indexOfFirst { it.id == context.invokingPlayerId }
        require(invokingPlayerIndex >= 0) { "Invoking player does not belong to the game" }

        val config = scenarioConfig(currentGame)
        val module = RiichiRuleModule(BuiltInRuleModuleIds.RIICHI, config)
        val opening = DEFAULT_WALL_OPENING
        val inventory = module.createWallFactory().create().getAllTiles().sortedBy { it.tile.stableSortKey() }
        val availableTiles = inventory.toMutableList()
        val invokingStandingTiles = takeTiles(
            availableTiles,
            listOf(1, 1, 1, 2, 3, 4, 5, 6, 7, 8, 9, 9, 9)
                .map { value -> Tile.Numeric(Tile.Suit.Character, value) },
        )
        val invokingDrawnTile = takeTiles(availableTiles, listOf(Tile.Honor.North)).single()
        val opponentHands = List(PLAYER_COUNT - 1) { takeDistinctFillers(availableTiles, INITIAL_HAND_SIZE) }
        val opponentRiichiTiles = takeDistinctFillers(availableTiles, PLAYER_COUNT - 1)
        val consumedTiles = invokingStandingTiles + opponentHands.flatten() + opponentRiichiTiles + invokingDrawnTile
        val initialReservedTiles = takeFirst(availableTiles, config.deadTileCount)
        val initialLiveTiles = consumedTiles + availableTiles
        val templateLayout = requireNotNull(module.createWallLayout()).resolve(inventory, opening)
        val structure = remapStructure(templateLayout, initialLiveTiles, initialReservedTiles)
        val layoutResult = TileWallLayoutResult(initialLiveTiles, initialReservedTiles, structure)
        val visibleWallTileIds = (availableTiles + initialReservedTiles).mapTo(mutableSetOf()) { tile -> tile.id }
        val initialPhysicalLayout = when (
            val decision = module.createPhysicalWallLayoutPolicy().createInitialLayoutValidated(
                InitialPhysicalWallLayoutContext(layoutResult, opening, visibleWallTileIds),
            )
        ) {
            is InitialPhysicalWallLayoutDecision.Completed -> decision.layout
            is InitialPhysicalWallLayoutDecision.Rejected -> error(decision.reasonId)
        }
        val physicalLayout = TileWallPhysicalLayout(
            initialPhysicalLayout.placements.filterKeys { tileId -> tileId in visibleWallTileIds },
        )

        var opponentIndex = 0
        val players = currentGame.tableState.players.mapIndexed { index, oldPlayer ->
            if (index == invokingPlayerIndex) {
                MahjongPlayer(
                    id = oldPlayer.id,
                    initialSeatIndex = oldPlayer.initialSeatIndex,
                    hand = Hand(tiles = invokingStandingTiles, lastDrawn = invokingDrawnTile),
                    discardPile = RiichiDiscardPile(),
                    playerRuleState = RiichiPlayerState(),
                    score = config.scoreConfig.initialScore,
                    aiStrategyKey = oldPlayer.aiStrategyKey,
                    seatWind = Wind.entries[(index - invokingPlayerIndex + PLAYER_COUNT) % PLAYER_COUNT],
                )
            } else {
                val riichiTile = opponentRiichiTiles[opponentIndex]
                val discardAction = GameAction.Discard(riichiTile.id)
                MahjongPlayer(
                    id = oldPlayer.id,
                    initialSeatIndex = oldPlayer.initialSeatIndex,
                    hand = Hand(tiles = opponentHands[opponentIndex]),
                    discardPile = RiichiDiscardPile().discard(RiichiDiscardEntry(riichiTile, isRiichi = true)),
                    playerRuleState = RiichiPlayerState(riichiTile = riichiTile),
                    score = config.scoreConfig.initialScore - RIICHI_STICK_SCORE,
                    aiStrategyKey = oldPlayer.aiStrategyKey,
                    actionHistory = listOf(RIICHI_GAME_ACTION, discardAction),
                    seatWind = Wind.entries[(index - invokingPlayerIndex + PLAYER_COUNT) % PLAYER_COUNT],
                ).also { opponentIndex++ }
            }
        }
        val state = TableState(
            id = currentGame.id,
            players = players,
            config = config,
            tileWall = TileWall(availableTiles),
            dealerPlayerId = context.invokingPlayerId,
            roundPosition = MatchRoundPosition(sequenceIndex = 0, prevalentWind = Wind.EAST, localRoundNumber = 1),
            currentPlayerIndex = invokingPlayerIndex,
            dynamicRuleState = RiichiDynamicState(riichiStickCount = PLAYER_COUNT - 1),
            wallOpening = opening,
            initialDeadWall = initialReservedTiles,
            physicalWallLayout = physicalLayout,
        )
        return DebugGameScenarioResult(
            game = Game(
                tableState = state,
                flowConfig = currentGame.flowConfig,
                hostId = currentGame.hostId,
                roomPlayerIds = currentGame.roomPlayerIds,
            ),
            wallStructure = structure,
            wallLayout = initialPhysicalLayout,
        )
    }
}

/** 使用正式初始化流程建立完整日麻開局呈現情境。 */
private object RiichiWallOpeningScenario : DebugGameScenario {
    override val id: String = "mahjongcraft:riichi_wall_opening"

    override fun build(context: DebugGameScenarioContext): DebugGameScenarioResult {
        val currentGame = context.currentGame
        val config = currentGame.tableState.config as? RiichiRuleConfig
            ?: error("Riichi debug scenarios require a Riichi game")
        val module = RiichiRuleModule(BuiltInRuleModuleIds.RIICHI, config)
        val playerIds = currentGame.tableState.players.map { player -> player.id }
        val aiPlayerStrategyKeys = currentGame.tableState.players.mapNotNull { player ->
            player.aiStrategyKey?.let { strategyKey -> player.id to strategyKey }
        }.toMap()
        val initialization = GameInitializer.initialize(
            id = currentGame.id,
            playerIds = playerIds,
            module = module,
            aiPlayerStrategyKeys = aiPlayerStrategyKeys,
        )
        val dealtState = initialization.tableState
        val dealOrderHandTileIdsBySeatIndex = dealtState.players.withIndex().associate { (seatIndex, player) ->
            seatIndex to player.hand.tiles.map { tile -> tile.id }
        }
        val organizedState = dealtState.copy(
            players = dealtState.players.map { player ->
                player.copy(hand = player.hand.organize(module.tileOrder))
            },
        )
        val postFlipHandTileIdsBySeatIndex = organizedState.players.withIndex().associate { (seatIndex, player) ->
            seatIndex to player.hand.tiles.map { tile -> tile.id }
        }
        val diceRoll = requireNotNull(initialization.diceRoll) { "Riichi initialization did not produce dice" }
        val wallStructure = requireNotNull(initialization.wallStructure) {
            "Riichi initialization did not produce wall structure"
        }
        val initialPhysicalWallLayout = requireNotNull(initialization.initialPhysicalWallLayout) {
            "Riichi initialization did not produce physical wall layout"
        }
        return DebugGameScenarioResult(
            game = Game(
                tableState = organizedState,
                flowConfig = currentGame.flowConfig,
                hostId = currentGame.hostId,
                roomPlayerIds = currentGame.roomPlayerIds,
            ),
            wallStructure = wallStructure,
            wallLayout = initialPhysicalWallLayout,
            presentation = DebugGameScenarioPresentation.InitialRound(
                diceRoll = diceRoll,
                dealOrderHandTileIdsBySeatIndex = dealOrderHandTileIdsBySeatIndex,
                postFlipHandTileIdsBySeatIndex = postFlipHandTileIdsBySeatIndex,
            ),
        )
    }
}

/** 建立固定停在下一次暗槓宣告前的四人日麻情境。 */
private class RiichiBeforeAnkanScenario(
    override val id: String,
    /** 已在桌上成立並完成補牌的槓數。 */
    private val completedKanCount: Int,
    /** 本情境使用的開門位置，決定王牌區落在哪裡。 */
    private val opening: WallOpening = DEFAULT_WALL_OPENING,
) : DebugGameScenario {
    override fun build(context: DebugGameScenarioContext): DebugGameScenarioResult {
        val currentGame = context.currentGame
        require(currentGame.tableState.players.size == PLAYER_COUNT) { "Riichi debug scenarios require four players" }
        require(currentGame.tableState.config is RiichiRuleConfig) { "Riichi debug scenarios require a Riichi game" }
        require(currentGame.tableState.players.any { it.id == context.invokingPlayerId }) {
            "Invoking player does not belong to the game"
        }

        val config = scenarioConfig(currentGame)
        val module = RiichiRuleModule(BuiltInRuleModuleIds.RIICHI, config)
        val inventory = module.createWallFactory().create().getAllTiles().sortedBy { it.tile.stableSortKey() }
        val availableTiles = inventory.toMutableList()
        val requestedKanCount = completedKanCount + 1
        val kanGroups = selectCompleteGroups(availableTiles, requestedKanCount)
        val targetKan = kanGroups.last()
        val establishedKans = kanGroups.dropLast(1)
        val dealerIndex = currentGame.tableState.players.indexOfFirst { it.id == context.invokingPlayerId }
        val initialHands = createInitialHands(availableTiles, dealerIndex, targetKan, establishedKans)
        val initialDealOrder = interleaveInitialDeal(initialHands, dealerIndex, config.dealBatchSizes())
        val priorNormalDraws = List(establishedKans.size) { takeFirst(availableTiles, 1).single() }
        val dealerDraw = targetKan.last()
        val consumedFromFront = initialDealOrder + priorNormalDraws + dealerDraw
        val initialReservedTiles = takeFirst(availableTiles, config.deadTileCount)
        val initialLiveTiles = consumedFromFront + availableTiles
        val templateLayout = requireNotNull(module.createWallLayout()).resolve(inventory, opening)
        val structure = remapStructure(templateLayout, initialLiveTiles, initialReservedTiles).toMutableMap()
        val initialLayoutResult = TileWallLayoutResult(initialLiveTiles, initialReservedTiles, structure)
        val initialPhysicalLayout = when (
            val decision = module.createPhysicalWallLayoutPolicy().createInitialLayoutValidated(
                InitialPhysicalWallLayoutContext(
                    initialLayoutResult,
                    opening,
                    (initialLiveTiles.drop(consumedFromFront.size) + initialReservedTiles)
                        .mapTo(mutableSetOf()) { tile -> tile.id },
                ),
            )
        ) {
            is InitialPhysicalWallLayoutDecision.Completed -> decision.layout
            is InitialPhysicalWallLayoutDecision.Rejected -> error(decision.reasonId)
        }
        val liveTiles = initialLiveTiles.drop(consumedFromFront.size).toMutableList()
        val reservedTiles = initialReservedTiles.toMutableList()
        val supplementalDiscards = mutableListOf<IdentifiedTile>()
        val wallStages = mutableListOf(WallStage(liveTiles.toList(), reservedTiles.toList()))

        repeat(establishedKans.size) {
            val drawnTile = reservedTiles.removeAt(0)
            val replenishmentTile = liveTiles.removeLast()
            reservedTiles += replenishmentTile
            supplementalDiscards += drawnTile
            wallStages += WallStage(liveTiles.toList(), reservedTiles.toList())
        }

        val players = currentGame.tableState.players.mapIndexed { index, oldPlayer ->
            val establishedKanIndex = opponentOrder(index, dealerIndex)
            val establishedKan = establishedKans.getOrNull(establishedKanIndex)
            val melds = establishedKan?.let { tiles ->
                listOf(Meld(MeldType.CLOSED_KAN, tiles, sourceDirection = RelativeDirection.Self))
            }.orEmpty()
            val tiles = initialHands[index].filterNot { tile -> establishedKan?.contains(tile) == true }
            val discardPile = module.createDiscardPile().let { pile ->
                if (establishedKan == null) {
                    pile
                } else {
                    pile.discardTile(priorNormalDraws[establishedKanIndex])
                        .discardTile(supplementalDiscards[establishedKanIndex])
                }
            }
            MahjongPlayer(
                id = oldPlayer.id,
                initialSeatIndex = oldPlayer.initialSeatIndex,
                hand = Hand(tiles = tiles, melds = melds, lastDrawn = dealerDraw.takeIf { index == dealerIndex }),
                discardPile = discardPile,
                playerRuleState = module.createInitialPlayerRuleState(),
                score = config.scoreConfig.initialScore,
                aiStrategyKey = oldPlayer.aiStrategyKey,
                seatWind = Wind.entries[(index - dealerIndex + PLAYER_COUNT) % PLAYER_COUNT],
            )
        }
        val tableStateWithoutLayout = TableState(
            id = currentGame.id,
            players = players,
            config = config,
            tileWall = TileWall(liveTiles),
            dealerPlayerId = context.invokingPlayerId,
            roundPosition = MatchRoundPosition(sequenceIndex = 0, prevalentWind = Wind.EAST, localRoundNumber = 1),
            currentPlayerIndex = dealerIndex,
            dynamicRuleState = RiichiDynamicState(completedSupplementalDrawCount = completedKanCount),
            wallOpening = opening,
            initialDeadWall = reservedTiles,
        )
        val currentPhysicalLayout = resolveCurrentPhysicalLayout(
            module,
            tableStateWithoutLayout,
            initialPhysicalLayout,
            wallStages,
        )
        val tableState = tableStateWithoutLayout.copy(physicalWallLayout = currentPhysicalLayout)
        val presentationLayout = TileWallPhysicalLayout(initialPhysicalLayout.placements + currentPhysicalLayout.placements)
        return DebugGameScenarioResult(
            Game(
                tableState = tableState,
                flowConfig = currentGame.flowConfig,
                hostId = currentGame.hostId,
                roomPlayerIds = currentGame.roomPlayerIds,
            ),
            structure,
            presentationLayout,
        )
    }

    /** 依正式 policy 重播既有槓數，取得目前仍留在牌牆中的權威 placement。 */
    private fun resolveCurrentPhysicalLayout(
        module: RiichiRuleModule,
        stateTemplate: TableState,
        initialLayout: TileWallPhysicalLayout,
        wallStages: List<WallStage>,
    ): TileWallPhysicalLayout {
        val firstStageIds = (wallStages.first().liveTiles + wallStages.first().reservedTiles)
            .mapTo(mutableSetOf()) { tile -> tile.id }
        var currentLayout = TileWallPhysicalLayout(initialLayout.placements.filterKeys { tileId -> tileId in firstStageIds })
        wallStages.zipWithNext().forEachIndexed { index, (beforeStage, afterStage) ->
            val beforeState = stateTemplate.copy(
                tileWall = TileWall(beforeStage.liveTiles),
                initialDeadWall = beforeStage.reservedTiles,
                dynamicRuleState = RiichiDynamicState(completedSupplementalDrawCount = index),
                physicalWallLayout = currentLayout,
            )
            val afterState = stateTemplate.copy(
                tileWall = TileWall(afterStage.liveTiles),
                initialDeadWall = afterStage.reservedTiles,
                dynamicRuleState = RiichiDynamicState(completedSupplementalDrawCount = index + 1),
            )
            val markerTile = beforeStage.reservedTiles.first()
            val decision = module.createPhysicalWallLayoutPolicy().resolveTransitionValidated(
                PhysicalWallLayoutTransitionContext(
                    tableStateBeforeAction = beforeState,
                    tableStateAfterAction = afterState,
                    currentLayout = currentLayout,
                    actorPlayerId = stateTemplate.currentPlayer.id,
                    action = GameAction.Kan(GameAction.KanType.CLOSED_KAN, markerTile.id, emptyList()),
                ),
            )
            currentLayout = when (decision) {
                is PhysicalWallLayoutTransitionDecision.Completed -> decision.layout
                is PhysicalWallLayoutTransitionDecision.Rejected -> error(decision.reasonId)
                PhysicalWallLayoutTransitionDecision.Unchanged -> error("Riichi kan must change the physical wall layout")
            }
        }
        return currentLayout
    }

    /** 建立四家開局十三張手牌；預定槓牌只會出現在指定玩家手中。 */
    private fun createInitialHands(
        availableTiles: MutableList<IdentifiedTile>,
        dealerIndex: Int,
        targetKan: List<IdentifiedTile>?,
        establishedKans: List<List<IdentifiedTile>>,
    ): List<List<IdentifiedTile>> = List(PLAYER_COUNT) { playerIndex ->
        val establishedKan = establishedKans.getOrNull(opponentOrder(playerIndex, dealerIndex))
        when {
            playerIndex == dealerIndex && targetKan != null ->
                targetKan.take(TARGET_KAN_IN_HAND) + takeDistinctFillers(
                    availableTiles,
                    INITIAL_HAND_SIZE - TARGET_KAN_IN_HAND,
                )

            establishedKan != null -> establishedKan + takeDistinctFillers(
                availableTiles,
                INITIAL_HAND_SIZE - KAN_SIZE,
            )

            else -> takeDistinctFillers(availableTiles, INITIAL_HAND_SIZE)
        }
    }

    /** 依正式開局的批次輪轉順序，把四家手牌攤平成牌牆前端消耗順序。 */
    private fun interleaveInitialDeal(
        hands: List<List<IdentifiedTile>>,
        dealerIndex: Int,
        batchSizes: List<Int>,
    ): List<IdentifiedTile> {
        val offsets = IntArray(hands.size)
        return buildList {
            batchSizes.forEach { batchSize ->
                hands.indices.forEach { turnOffset ->
                    val playerIndex = (dealerIndex + turnOffset) % hands.size
                    val start = offsets[playerIndex]
                    addAll(hands[playerIndex].subList(start, start + batchSize))
                    offsets[playerIndex] += batchSize
                }
            }
        }
    }

    /** 將玩家 index 換算成不包含莊家的對手順序；莊家回傳不可能命中的索引。 */
    private fun opponentOrder(playerIndex: Int, dealerIndex: Int): Int = if (playerIndex == dealerIndex) {
        Int.MAX_VALUE
    } else {
        (playerIndex - dealerIndex + PLAYER_COUNT - 1) % PLAYER_COUNT
    }

    /** 從牌組庫選出指定數量、每組四張完全相同的牌。 */
    private fun selectCompleteGroups(tiles: MutableList<IdentifiedTile>, count: Int): List<List<IdentifiedTile>> {
        val groups = tiles.groupBy { it.tile }.values.filter { it.size == KAN_SIZE }.take(count)
        require(groups.size == count) { "Not enough complete tile groups in the wall" }
        groups.flatten().forEach(tiles::remove)
        return groups
    }

    private companion object {
        /** 一組槓的實體牌數。 */
        const val KAN_SIZE: Int = 4

        /** 暗槓宣告前放在一般立牌區的同種牌數。 */
        const val TARGET_KAN_IN_HAND: Int = 3
    }
}

/** 一次已完成槓牌前後重建 policy 所需的活牌與保留牌快照。 */
private data class WallStage(
    /** 此階段仍可一般摸取的活牌。 */
    val liveTiles: List<IdentifiedTile>,
    /** 此階段由規則管理的保留牌。 */
    val reservedTiles: List<IdentifiedTile>,
)

/** 建立固定停在呼叫者可對上一張捨牌宣告大明槓的四人日麻情境。 */
private class RiichiBeforeMinkanScenario(
    override val id: String,
    /** 本情境使用的開門位置，決定王牌區落在哪裡。 */
    private val opening: WallOpening = DEFAULT_WALL_OPENING,
) : DebugGameScenario {
    override fun build(context: DebugGameScenarioContext): DebugGameScenarioResult {
        val base = RiichiBeforeAnkanScenario(id, 0, opening).build(context)
        val state = base.game.tableState
        val claimantIndex = state.players.indexOfFirst { it.id == context.invokingPlayerId }
        val discarderIndex = (claimantIndex + state.players.lastIndex) % state.players.size
        val claimant = state.players[claimantIndex]
        val discardedTile = requireNotNull(claimant.hand.lastDrawn) {
            "Minkan debug scenario requires the fourth matching tile as the prepared draw"
        }
        val discardAction = GameAction.Discard(discardedTile.id)
        val players = state.players.mapIndexed { index, player ->
            when (index) {
                claimantIndex -> player.copy(hand = player.hand.copy(lastDrawn = null))
                discarderIndex -> player.copy(
                    discardPile = player.discardPile.discardTile(discardedTile),
                    actionHistory = player.actionHistory + discardAction,
                )
                else -> player
            }
        }
        val tableState = state.copy(
            players = players,
            currentPlayerIndex = discarderIndex,
            pendingReaction = PendingReaction(
                discarderId = players[discarderIndex].id,
                tileId = discardedTile.id,
                eligiblePlayerIds = setOf(context.invokingPlayerId),
            ),
        )
        return base.copy(game = base.game.copy(tableState = tableState))
    }
}

/**
 * 取得情境要沿用的日麻設定，也就是目前這一桌正在用的那一份。
 *
 * 情境的用途是重現這一桌的狀況，不是換一套規則，因此**整份設定原封不動沿用**，不用預設值覆蓋——對局
 * 長度、紅寶牌、供託規則與初始點數都必須維持玩家在房間裡選的那一份。情境需要的張數與節奏本來就全部由
 * 設定推導（牌張工廠、`deadTileCount`、`dealBatchSizes()`、初始點數），沿用設定不會破壞可重現性。
 */
private fun scenarioConfig(currentGame: Game): RiichiRuleConfig = currentGame.tableState.config as RiichiRuleConfig

/** 從牌庫依指定牌種順序各取出一張具有唯一 UUID 的實體牌。 */
private fun takeTiles(
    tiles: MutableList<IdentifiedTile>,
    requestedTiles: List<Tile>,
): List<IdentifiedTile> = requestedTiles.map { requested ->
    val index = tiles.indexOfFirst { tile -> tile.tile == requested }
    require(index >= 0) { "Debug scenario does not contain requested tile: $requested" }
    tiles.removeAt(index)
}

/** 取出牌種互異的情境填充牌，避免意外建立特殊牌型。 */
private fun takeDistinctFillers(tiles: MutableList<IdentifiedTile>, count: Int): List<IdentifiedTile> {
    val selected = tiles.distinctBy { tile -> tile.tile }.take(count)
    require(selected.size == count) { "Not enough distinct filler tiles" }
    selected.forEach(tiles::remove)
    return selected
}

/** 從清單前端取出固定張數並同步移除。 */
private fun takeFirst(tiles: MutableList<IdentifiedTile>, count: Int): List<IdentifiedTile> = List(count) {
    require(tiles.isNotEmpty()) { "Debug scenario ran out of tiles" }
    tiles.removeAt(0)
}

/** 把情境指定的活牌與保留牌配置到正式 layout 的物理格位。 */
private fun remapStructure(
    template: TileWallLayoutResult,
    liveTiles: List<IdentifiedTile>,
    reservedTiles: List<IdentifiedTile>,
): Map<Uuid, TileWallPosition> = buildMap {
    require(liveTiles.size == template.drawOrder.size) { "Debug live wall size does not match layout" }
    require(reservedTiles.size == template.reservedWallTiles.size) { "Debug reserved wall size does not match layout" }
    liveTiles.zip(template.drawOrder).forEach { (tile, slot) -> put(tile.id, template.structure.getValue(slot.id)) }
    reservedTiles.zip(template.reservedWallTiles).forEach { (tile, slot) ->
        put(tile.id, template.structure.getValue(slot.id))
    }
}

/** 產生不受 factory 洗牌結果影響的牌種排序鍵。 */
private fun Tile.stableSortKey(): String = when (this) {
    is Tile.Numeric -> "0:${suit.ordinal}:$value"
    is Tile.Honor -> "1:${HONORS.indexOf(this)}"
    is Tile.Extension -> "2:${typeId.namespace}:${typeId.path}"
}

/** 四人日麻固定玩家數。 */
private const val PLAYER_COUNT: Int = 4

/** 未副露時的立牌張數。 */
private const val INITIAL_HAND_SIZE: Int = 13

/** 宣告立直時支付的點棒分數。 */
private const val RIICHI_STICK_SCORE: Int = 1000

/** 字牌的穩定排序順序。 */
private val HONORS: List<Tile.Honor> = listOf(
    Tile.Honor.East,
    Tile.Honor.South,
    Tile.Honor.West,
    Tile.Honor.North,
    Tile.Honor.White,
    Tile.Honor.Green,
    Tile.Honor.Red,
)
