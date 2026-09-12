package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug

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
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPosition
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayoutResult
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
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
        RiichiBeforeMinkanScenario,
        RiichiBeforeAnkanScenario("mahjongcraft:riichi_wall_initial", null),
    )
}

/** 建立固定停在下一次暗槓宣告前的四人日麻情境。 */
private class RiichiBeforeAnkanScenario(
    override val id: String,
    /** 已在桌上成立並完成補牌的槓數；null 代表單純初始牌牆情境。 */
    private val completedKanCount: Int?,
) : DebugGameScenario {
    override fun build(context: DebugGameScenarioContext): DebugGameScenarioResult {
        val currentGame = context.currentGame
        require(currentGame.tableState.players.size == PLAYER_COUNT) { "Riichi debug scenarios require four players" }
        require(currentGame.tableState.config is RiichiRuleConfig) { "Riichi debug scenarios require a Riichi game" }
        require(currentGame.tableState.players.any { it.id == context.invokingPlayerId }) {
            "Invoking player does not belong to the game"
        }

        val config = RiichiRuleConfig()
        val module = RiichiRuleModule(BuiltInRuleModuleIds.RIICHI, config)
        val opening = WallOpening(wallSideOffsetFromDealer = 0, stacksFromRight = 8)
        val inventory = module.createWallFactory().create().getAllTiles().sortedBy { it.tile.stableSortKey() }
        val availableTiles = inventory.toMutableList()
        val requestedKanCount = completedKanCount?.plus(1) ?: 0
        val kanGroups = selectCompleteGroups(availableTiles, requestedKanCount)
        val targetKan = completedKanCount?.let { kanGroups.last() }
        val establishedKans = if (completedKanCount == null) emptyList() else kanGroups.dropLast(1)
        val dealerIndex = currentGame.tableState.players.indexOfFirst { it.id == context.invokingPlayerId }
        val initialHands = createInitialHands(availableTiles, dealerIndex, targetKan, establishedKans)
        val initialDealOrder = interleaveInitialDeal(initialHands, dealerIndex, config.dealBatchSizes())
        val priorNormalDraws = List(establishedKans.size) { takeFirst(availableTiles, 1).single() }
        val dealerDraw = targetKan?.last()
        val consumedFromFront = initialDealOrder + priorNormalDraws + listOfNotNull(dealerDraw)
        val initialReservedTiles = takeFirst(availableTiles, config.deadTileCount)
        val initialLiveTiles = consumedFromFront + availableTiles
        val templateLayout = requireNotNull(module.createWallLayout()).resolve(inventory, opening)
        val structure = remapStructure(templateLayout, initialLiveTiles, initialReservedTiles).toMutableMap()
        val liveTiles = initialLiveTiles.drop(consumedFromFront.size).toMutableList()
        val reservedTiles = initialReservedTiles.toMutableList()
        val supplementalDiscards = mutableListOf<IdentifiedTile>()

        repeat(establishedKans.size) {
            val drawnTile = reservedTiles.removeAt(0)
            val replenishmentTile = liveTiles.removeLast()
            reservedTiles += replenishmentTile
            supplementalDiscards += drawnTile
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
        val tableState = TableState(
            id = currentGame.id,
            players = players,
            config = config,
            tileWall = TileWall(liveTiles),
            dealerPlayerId = context.invokingPlayerId,
            roundPosition = MatchRoundPosition(sequenceIndex = 0, prevalentWind = Wind.EAST, localRoundNumber = 1),
            currentPlayerIndex = dealerIndex,
            dynamicRuleState = RiichiDynamicState(completedSupplementalDrawCount = completedKanCount ?: 0),
            wallOpening = opening,
            initialDeadWall = reservedTiles,
        )
        return DebugGameScenarioResult(
            Game(
                tableState = tableState,
                flowConfig = currentGame.flowConfig,
                hostId = currentGame.hostId,
                roomPlayerIds = currentGame.roomPlayerIds,
            ),
            structure,
        )
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

    /** 把指定活牌與保留牌配置到正式 layout 的同一組物理格位。 */
    private fun remapStructure(
        template: TileWallLayoutResult,
        liveTiles: List<IdentifiedTile>,
        reservedTiles: List<IdentifiedTile>,
    ): Map<Uuid, TileWallPosition> = buildMap {
        require(liveTiles.size == template.drawOrder.size) { "Debug live wall size does not match layout" }
        require(reservedTiles.size == template.reservedWallTiles.size) { "Debug reserved wall size does not match layout" }
        liveTiles.zip(template.drawOrder).forEach { (tile, slot) ->
            put(tile.id, template.structure.getValue(slot.id))
        }
        reservedTiles.zip(template.reservedWallTiles).forEach { (tile, slot) ->
            put(tile.id, template.structure.getValue(slot.id))
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

    /** 取出牌種互異的填充牌，避免替預定玩家意外建立第二組暗槓。 */
    private fun takeDistinctFillers(tiles: MutableList<IdentifiedTile>, count: Int): List<IdentifiedTile> {
        val selected = tiles.distinctBy { it.tile }.take(count)
        require(selected.size == count) { "Not enough distinct filler tiles" }
        selected.forEach(tiles::remove)
        return selected
    }

    /** 從清單前端取出固定張數並同步移除。 */
    private fun takeFirst(tiles: MutableList<IdentifiedTile>, count: Int): List<IdentifiedTile> = List(count) {
        require(tiles.isNotEmpty()) { "Debug scenario ran out of tiles" }
        tiles.removeAt(0)
    }

    /** 產生不受 factory 洗牌結果影響的牌種排序鍵。 */
    private fun Tile.stableSortKey(): String = when (this) {
        is Tile.Numeric -> "0:${suit.ordinal}:$value"
        is Tile.Honor -> "1:${HONORS.indexOf(this)}"
        is Tile.Extension -> "2:${typeId.namespace}:${typeId.path}"
    }

    private companion object {
        /** 四人日麻固定玩家數。 */
        const val PLAYER_COUNT: Int = 4

        /** 未副露時的立牌張數。 */
        const val INITIAL_HAND_SIZE: Int = 13

        /** 一組槓的實體牌數。 */
        const val KAN_SIZE: Int = 4

        /** 暗槓宣告前放在一般立牌區的同種牌數。 */
        const val TARGET_KAN_IN_HAND: Int = 3

        /** 字牌的穩定排序順序。 */
        val HONORS: List<Tile.Honor> = listOf(
            Tile.Honor.East,
            Tile.Honor.South,
            Tile.Honor.West,
            Tile.Honor.North,
            Tile.Honor.White,
            Tile.Honor.Green,
            Tile.Honor.Red,
        )
    }
}

/** 建立固定停在呼叫者可對上一張捨牌宣告大明槓的四人日麻情境。 */
private object RiichiBeforeMinkanScenario : DebugGameScenario {
    override val id: String = "mahjongcraft:riichi_before_minkan_1"

    override fun build(context: DebugGameScenarioContext): DebugGameScenarioResult {
        val base = RiichiBeforeAnkanScenario(id, 0).build(context)
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
