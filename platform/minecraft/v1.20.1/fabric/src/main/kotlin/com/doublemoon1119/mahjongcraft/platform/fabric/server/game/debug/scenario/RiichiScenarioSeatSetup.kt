package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RIICHI_GAME_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardEntry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPosition
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.layout.InitialPhysicalWallLayoutContext
import com.doublemoon1119.mahjongcraft.logic.table.layout.InitialPhysicalWallLayoutDecision
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayoutResult
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.createInitialLayoutValidated

/**
 * 一個座位的情境設定。
 *
 * @property standingTiles 指定的立牌；不足的張數以牌種互異的填充牌補齊。
 * @property ponTiles 每個牌種各一組已碰出的副露。
 * @property drawnTile 剛摸到、尚未打出的牌；只有呼叫者使用。
 * @property discards 預先打出的牌。
 * @property riichiTile 不為 null 時，這張牌作為立直宣告牌打出，玩家處於立直狀態。
 * @property strategyKey 對手使用的腳本 AI；呼叫者不使用。
 */
internal data class SeatSpec(
    val standingTiles: List<Tile> = emptyList(),
    val ponTiles: List<Tile> = emptyList(),
    val drawnTile: Tile? = null,
    val discards: List<Tile> = emptyList(),
    val riichiTile: Tile? = null,
    val strategyKey: String = DebugScriptedAiStrategy.TSUMOGIRI_KEY,
)

/**
 * 依座位設定與活牌最前端順序建立的日麻情境。
 *
 * 呼叫者為莊家，座位順序為呼叫者 → 下家 → 對家 → 上家，情境從呼叫者剛摸牌、準備捨牌開始。其他三家必須是 AI，
 * 載入後改用 [DebugScriptedAiStrategy]，依 [liveWallFront] 排定的順序摸牌並立即打出，因此接下來每一張摸牌與
 * 捨牌都是固定的。
 *
 * @property id 完整的 namespaced 情境 ID。
 * @property invoker 呼叫者的座位設定。
 * @property downstream 下家的座位設定。
 * @property across 對家的座位設定。
 * @property upstream 上家的座位設定。
 * @property liveWallFront 活牌最前端的排定牌種順序，決定載入後每一次摸牌。
 * @property waitingTiles 驗收用的和牌張；這些牌種不會被放進王牌區，避免成為寶牌指示牌或嶺上牌。
 * @property multiRonPolicy 不為 null 時覆寫這一桌的一炮多響設定，其餘設定一律沿用。
 */
internal class SeatSetupScenario(
    override val id: String,
    private val invoker: SeatSpec,
    private val downstream: SeatSpec = SeatSpec(),
    private val across: SeatSpec = SeatSpec(),
    private val upstream: SeatSpec = SeatSpec(),
    private val liveWallFront: List<Tile>,
    private val waitingTiles: Set<Tile>,
    private val multiRonPolicy: MultiRonPolicy? = null,
) : DebugGameScenario {
    override fun build(context: DebugGameScenarioContext): DebugGameScenarioResult {
        val currentGame = context.currentGame
        val tableState = currentGame.tableState
        require(tableState.players.size == PLAYER_COUNT) { "Riichi debug scenarios require four players" }
        require(tableState.config is RiichiRuleConfig) { "Riichi debug scenarios require a Riichi game" }
        val invokingPlayerIndex = tableState.players.indexOfFirst { it.id == context.invokingPlayerId }
        require(invokingPlayerIndex >= 0) { "Invoking player does not belong to the game" }
        require(tableState.players.filterIndexed { index, _ -> index != invokingPlayerIndex }.all { it.isAi }) {
            "Seat setup debug scenarios require the other three players to be AI"
        }

        val config = scenarioConfig(currentGame).let { current ->
            multiRonPolicy?.let { current.copy(multiRonPolicy = it) } ?: current
        }
        val module = RiichiRuleModule(BuiltInRuleModuleIds.RIICHI, config)
        val opening = DEFAULT_WALL_OPENING
        val inventory = module.createWallFactory().create().getAllTiles().sortedBy { it.tile.stableSortKey() }
        val availableTiles = inventory.toMutableList()

        val seatSpecs = listOf(invoker, downstream, across, upstream)
        val explicitSeats = seatSpecs.map { spec -> spec.takeExplicitTiles(availableTiles) }
        val wallFront = takeTiles(availableTiles, liveWallFront)
        val seats = seatSpecs.zip(explicitSeats) { spec, explicit -> explicit.withFillers(spec, availableTiles) }
        val initialReservedTiles = takeAvoiding(availableTiles, config.deadTileCount, waitingTiles)
        val liveTiles = wallFront + availableTiles
        val consumedTiles = seats.flatMap { it.allTiles }
        val initialLiveTiles = consumedTiles + liveTiles
        val templateLayout = requireNotNull(module.createWallLayout()).resolve(inventory, opening)
        val structure = remapStructure(templateLayout, initialLiveTiles, initialReservedTiles)
        val layoutResult = TileWallLayoutResult(initialLiveTiles, initialReservedTiles, structure)
        val visibleWallTileIds = (liveTiles + initialReservedTiles).mapTo(mutableSetOf()) { tile -> tile.id }
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

        val players = tableState.players.mapIndexed { index, oldPlayer ->
            val seatOffset = (index - invokingPlayerIndex + PLAYER_COUNT) % PLAYER_COUNT
            val seat = seats[seatOffset]
            MahjongPlayer(
                id = oldPlayer.id,
                initialSeatIndex = oldPlayer.initialSeatIndex,
                hand = Hand(tiles = seat.standingTiles, melds = seat.melds, lastDrawn = seat.drawnTile),
                discardPile = seat.discardPile(),
                playerRuleState = RiichiPlayerState(riichiTile = seat.riichiTile),
                score = config.scoreConfig.initialScore - if (seat.riichiTile != null) RIICHI_STICK_SCORE else 0,
                aiStrategyKey = if (seatOffset == 0) oldPlayer.aiStrategyKey else seatSpecs[seatOffset].strategyKey,
                actionHistory = seat.actionHistory(),
                seatWind = Wind.entries[seatOffset],
            )
        }
        val state = TableState(
            id = currentGame.id,
            players = players,
            config = config,
            tileWall = TileWall(liveTiles),
            dealerPlayerId = context.invokingPlayerId,
            roundPosition = MatchRoundPosition(sequenceIndex = 0, prevalentWind = Wind.EAST, localRoundNumber = 1),
            currentPlayerIndex = invokingPlayerIndex,
            dynamicRuleState = RiichiDynamicState(riichiStickCount = seats.count { it.riichiTile != null }),
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

/**
 * 一個座位實際取得的牌。
 *
 * @property standingTiles 立牌。
 * @property melds 副露。
 * @property drawnTile 剛摸到的牌。
 * @property discards 立直宣告牌以外的預先捨牌。
 * @property riichiTile 立直宣告牌。
 */
internal data class SeatTiles(
    val standingTiles: List<IdentifiedTile>,
    val melds: List<Meld>,
    val drawnTile: IdentifiedTile?,
    val discards: List<IdentifiedTile>,
    val riichiTile: IdentifiedTile?,
) {
    /** 這個座位持有的全部實體牌。 */
    val allTiles: List<IdentifiedTile>
        get() = standingTiles + melds.flatMap { it.tiles } + listOfNotNull(drawnTile) + discards + listOfNotNull(riichiTile)

    /** 預先捨牌依序放入牌河，立直宣告牌放在最後。 */
    fun discardPile(): RiichiDiscardPile {
        val pile = discards.fold(RiichiDiscardPile()) { current, tile -> current.discardTile(tile) }
        return riichiTile?.let { pile.discard(RiichiDiscardEntry(it, isRiichi = true)) } ?: pile
    }

    /** 與預先捨牌一致的摸牌、捨牌與立直紀錄。 */
    fun actionHistory(): List<GameAction> = discards.flatMap { tile -> listOf(GameAction.Draw, GameAction.Discard(tile.id)) } +
        (riichiTile?.let { tile -> listOf(GameAction.Draw, RIICHI_GAME_ACTION, GameAction.Discard(tile.id)) } ?: emptyList())

    /** 以牌種互異的填充牌補齊立牌。 */
    fun withFillers(spec: SeatSpec, availableTiles: MutableList<IdentifiedTile>): SeatTiles {
        val fillerCount = INITIAL_HAND_SIZE - spec.ponTiles.size * MELD_TILE_COUNT - standingTiles.size
        return copy(standingTiles = standingTiles + takeDistinctFillers(availableTiles, fillerCount))
    }
}

/** 取出座位設定指定的牌；填充牌在所有指定牌取出後才補上。 */
internal fun SeatSpec.takeExplicitTiles(availableTiles: MutableList<IdentifiedTile>): SeatTiles = SeatTiles(
    standingTiles = takeTiles(availableTiles, standingTiles),
    melds = ponTiles.map { tile ->
        Meld(
            type = MeldType.PON,
            tiles = takeTiles(availableTiles, List(MELD_TILE_COUNT) { tile }),
            sourceDirection = RelativeDirection.Across,
        )
    },
    drawnTile = drawnTile?.let { tile -> takeTiles(availableTiles, listOf(tile)).single() },
    discards = takeTiles(availableTiles, discards),
    riichiTile = riichiTile?.let { tile -> takeTiles(availableTiles, listOf(tile)).single() },
)

/** 從清單前端取出 [count] 張不屬於 [excludedTiles] 的牌，讓嶺上牌與寶牌不會是驗收用的和牌張。 */
internal fun takeAvoiding(
    tiles: MutableList<IdentifiedTile>,
    count: Int,
    excludedTiles: Set<Tile>,
): List<IdentifiedTile> {
    val selected = tiles.filter { tile -> tile.tile !in excludedTiles }.take(count)
    require(selected.size == count) { "Debug scenario ran out of tiles" }
    selected.forEach(tiles::remove)
    return selected
}

/** 一組碰的張數。 */
internal const val MELD_TILE_COUNT: Int = 3

/** 一萬。 */
internal val ONE_CHARACTER: Tile = Tile.Numeric(Tile.Suit.Character, 1)

/** 四萬。 */
internal val FOUR_CHARACTER: Tile = Tile.Numeric(Tile.Suit.Character, 4)

/** 九萬。 */
internal val NINE_CHARACTER: Tile = Tile.Numeric(Tile.Suit.Character, 9)

/** 九條。 */
internal val NINE_BAMBOO: Tile = Tile.Numeric(Tile.Suit.Bamboo, 9)

/**
 * 呼叫者驗收用的和牌張：一萬與四萬。
 *
 * 以下手牌每個五最多使用一張普通牌，赤寶牌設定取走部分普通五時仍可建立。
 */
internal val WAITING_TILES: Set<Tile> = setOf(ONE_CHARACTER, FOUR_CHARACTER)

/** 二三萬、四五六筒、七八九筒、中中中、二二條：聽一、四萬，兩張都有役牌中。 */
internal val YAKUHAI_TWO_SIDED_WAIT: List<Tile> = listOf(
    Tile.Numeric(Tile.Suit.Character, 2),
    Tile.Numeric(Tile.Suit.Character, 3),
    Tile.Numeric(Tile.Suit.Dot, 4),
    Tile.Numeric(Tile.Suit.Dot, 5),
    Tile.Numeric(Tile.Suit.Dot, 6),
    Tile.Numeric(Tile.Suit.Dot, 7),
    Tile.Numeric(Tile.Suit.Dot, 8),
    Tile.Numeric(Tile.Suit.Dot, 9),
    Tile.Honor.Red,
    Tile.Honor.Red,
    Tile.Honor.Red,
    Tile.Numeric(Tile.Suit.Bamboo, 2),
    Tile.Numeric(Tile.Suit.Bamboo, 2),
)
