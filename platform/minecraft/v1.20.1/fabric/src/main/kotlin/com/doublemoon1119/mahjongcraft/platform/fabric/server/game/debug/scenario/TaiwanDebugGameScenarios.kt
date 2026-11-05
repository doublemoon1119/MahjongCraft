package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TAIWAN_REVEAL_FLOWER_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPlayerTileAreaIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import kotlin.uuid.Uuid

/**
 * 台灣麻將的驗收情境，四人桌適用。
 *
 * 所有情境都以正式初始化流程發牌，再交換指定牌張的牌面，讓花牌或指定的牌落在需要的位置；牌張識別碼、牌牆結構與
 * 實體座標都沿用正式初始化的結果。座位以這一桌目前的座位為準，莊家由正式初始化決定。載入後改用台灣麻將；這一桌原本
 * 是台灣麻將時沿用它的設定。
 */
internal object TaiwanDebugGameScenarios {
    /** 所有台灣麻將驗收情境。 */
    val all: List<DebugGameScenario> = listOf(
        // 局首補花與途中連續補花：莊家起手兩張花、下家一張、上家一張，對家沒有花。莊家的第一張補牌又是花，連補一次。
        // 開局後第一次從活牌摸到的牌是花，補到的牌又是花，再連補一次。第八張花在活牌中段。
        TaiwanOpeningScenario(id = "mahjongcraft:taiwan_flower_replacement") { dealt, _ ->
            val dealer = dealt.dealerIndex
            val supplementalOrder = dealt.reservedWallTiles
            flowerFaces(
                dealt.handTilesOf(dealer).take(2) +
                    dealt.handTilesOf(dealer + 1).take(1) +
                    dealt.handTilesOf(dealer + 3).take(1) +
                    supplementalOrder[0] +
                    dealt.tileWall.getAllTiles().first() +
                    supplementalOrder[FIRST_MID_GAME_SUPPLEMENT] +
                    dealt.tileWall.getAllTiles()[dealt.tileWall.remainingCount / 2],
            )
        },
        // 七搶一：呼叫者起手七張花，呼叫者的下家起手一張花；下家亮出第八張花時，呼叫者可以選擇胡牌或放棄。
        TaiwanOpeningScenario(id = "mahjongcraft:taiwan_seven_flowers") { dealt, invoker ->
            flowerFaces(dealt.handTilesOf(invoker).take(SEVEN_FLOWERS) + dealt.handTilesOf(invoker + 1).take(1))
        },
        // 八仙過海：呼叫者起手八張花，補完花後可以選擇胡牌或放棄。
        TaiwanOpeningScenario(id = "mahjongcraft:taiwan_eight_flowers") { dealt, invoker ->
            flowerFaces(dealt.handTilesOf(invoker).take(ALL_FLOWERS))
        },
        // 宣告聽牌：呼叫者起手就是一至九萬、一至六筒、九條，單吊九條聽牌。第一次摸牌後打出摸到的牌就能宣告聽牌：
        // 呼叫者是莊家時為天聽，不是莊家時為地聽。八張花都在活牌尾端，開局不補花。
        TaiwanOpeningScenario(id = "mahjongcraft:taiwan_ready_declaration") { dealt, invoker ->
            dealt.handTilesOf(invoker).zip(READY_HAND) { tile, face -> tile.id to face } +
                flowerFaces(dealt.tileWall.getAllTiles().takeLast(ALL_FLOWERS))
        },
        TaiwanLastTileFlowerScenario,
    )
}

/**
 * 以正式初始化流程發牌、交換牌面後播放完整開局的台灣麻將情境；載入後與正式開局相同，接著處理局首補花。
 *
 * @property id 完整的 namespaced 情境 ID。
 * @property faces 依發牌後的桌況與呼叫者座位，列出要放到各牌張的牌面。
 */
private class TaiwanOpeningScenario(
    override val id: String,
    private val faces: (dealt: TableState, invokerIndex: Int) -> List<Pair<Uuid, Tile>>,
) : DebugGameScenario {
    override fun build(context: DebugGameScenarioContext): DebugGameScenarioResult {
        val currentGame = context.currentGame
        val module = taiwanScenarioModule(currentGame)
        val initialization = GameInitializer.initialize(
            id = currentGame.id,
            playerIds = currentGame.tableState.players.map { it.id },
            module = module,
        )
        val dealt = initialization.tableState.withFaces(faces(initialization.tableState, initialization.tableState.seatOf(context.invokingPlayerId)))
        val dealOrderHandTileIdsBySeatIndex = dealt.players.withIndex().associate { (seatIndex, player) ->
            seatIndex to player.hand.tiles.map { tile -> tile.id }
        }
        val organized = dealt.copy(players = dealt.players.map { player -> player.copy(hand = player.hand.organize(module.tileOrder)) })
        val postFlipHandTileIdsBySeatIndex = organized.players.withIndex().associate { (seatIndex, player) ->
            seatIndex to player.hand.tiles.map { tile -> tile.id }
        }
        val diceRoll = requireNotNull(organized.openingDiceRolls.singleOrNull()) {
            "Taiwan initialization did not produce exactly one dice roll"
        }.dice
        return DebugGameScenarioResult(
            game = currentGame.withTableState(organized),
            wallStructure = requireNotNull(initialization.wallStructure) { "Taiwan initialization did not produce wall structure" },
            wallLayout = requireNotNull(initialization.initialPhysicalWallLayout) { "Taiwan initialization did not produce physical wall layout" },
            presentation = DebugGameScenarioPresentation.InitialRound(
                diceRoll = diceRoll,
                dealOrderHandTileIdsBySeatIndex = dealOrderHandTileIdsBySeatIndex,
                postFlipHandTileIdsBySeatIndex = postFlipHandTileIdsBySeatIndex,
            ),
        )
    }
}

/**
 * 尾牌補花流局：活牌只剩一張花，其餘活牌當作先前各家打出的牌與已補出的花。
 *
 * 呼叫者剛摸到牌；呼叫者打出一張牌後，下家摸到最後一張活牌是花，已經沒有牌可以補，本局流局。另外七張花已在各家的
 * 花區。
 */
private object TaiwanLastTileFlowerScenario : DebugGameScenario {
    override val id: String = "mahjongcraft:taiwan_last_tile_flower"

    override fun build(context: DebugGameScenarioContext): DebugGameScenarioResult {
        val currentGame = context.currentGame
        val module = taiwanScenarioModule(currentGame)
        val initialization = GameInitializer.initialize(
            id = currentGame.id,
            playerIds = currentGame.tableState.players.map { it.id },
            module = module,
        )
        val initial = initialization.tableState.withInvokerToAct(context.invokingPlayerId)
        val liveTiles = initial.tileWall.getAllTiles()
        val lastLiveTile = liveTiles.first()
        val revealedFlowers = liveTiles.drop(1).take(ALL_FLOWERS - 1)
        val dealt = initial.withFaces(flowerFaces(listOf(lastLiveTile) + revealedFlowers))
        val faceById = (dealt.tileWall.getAllTiles()).associateBy { it.id }
        val flowersBySeat = revealedFlowers.map { faceById.getValue(it.id) }.withIndex()
            .groupBy({ (index, _) -> (dealt.dealerIndex + index) % dealt.players.size }, { (_, tile) -> tile })
        val discardsBySeat = liveTiles.drop(ALL_FLOWERS).map { faceById.getValue(it.id) }.withIndex()
            .groupBy({ (index, _) -> (dealt.dealerIndex + index) % dealt.players.size }, { (_, tile) -> tile })
        val players = dealt.players.mapIndexed { seatIndex, player ->
            player.withEarlierTurns(discards = discardsBySeat[seatIndex].orEmpty(), flowers = flowersBySeat[seatIndex].orEmpty())
        }
        val remainingWallIds = (listOf(lastLiveTile) + dealt.reservedWallTiles).mapTo(mutableSetOf()) { it.id }
        val initialPhysicalLayout = requireNotNull(initialization.initialPhysicalWallLayout) { "Taiwan initialization did not produce physical wall layout" }
        val state = dealt.copy(
            players = players.map { player -> player.copy(hand = player.hand.organize(module.tileOrder)) },
            tileWall = TileWall(listOf(faceById.getValue(lastLiveTile.id))),
            physicalWallLayout = TileWallPhysicalLayout(initialPhysicalLayout.placements.filterKeys { it in remainingWallIds }),
        )
        return DebugGameScenarioResult(
            game = currentGame.withTableState(state),
            wallStructure = requireNotNull(initialization.wallStructure) { "Taiwan initialization did not produce wall structure" },
            wallLayout = initialPhysicalLayout,
        )
    }

    /**
     * 補上先前回合打出的牌與已補出的花，以及對應的摸牌、補花與捨牌紀錄。
     *
     * @param discards 先前打出的牌，依打出順序排列。
     * @param flowers 先前補出的花。
     * @return 補上先前回合後的玩家。
     */
    private fun MahjongPlayer.withEarlierTurns(discards: List<IdentifiedTile>, flowers: List<IdentifiedTile>): MahjongPlayer = copy(
        discardPile = discards.fold(TaiwanDiscardPile()) { pile, tile -> pile.discardTile(tile) },
        tileAreas = tileAreas.withArea(TaiwanPlayerTileAreaIds.FLOWERS, flowers),
        actionHistory = flowers.flatMap { listOf(GameAction.Draw, TAIWAN_REVEAL_FLOWER_ACTION) } +
            discards.flatMap { tile -> listOf(GameAction.Draw, GameAction.Discard(tile.id)) },
    )
}

/**
 * 讓 [invokerId] 從活牌最前端摸一張牌，成為輪到行動的玩家。
 *
 * @param invokerId 呼叫者。
 * @return 輪到呼叫者行動的桌況。
 */
private fun TableState.withInvokerToAct(invokerId: Uuid): TableState {
    val invokerIndex = seatOf(invokerId)
    val draw = tileWall.draw()
    val drawn = requireNotNull(draw.tile) { "The live wall is empty" }
    return copy(
        players = players.mapIndexed { index, player ->
            if (index == invokerIndex) player.copy(hand = player.hand.copy(lastDrawn = drawn)) else player
        },
        tileWall = draw.wall,
        physicalWallLayout = physicalWallLayout?.retainOnly((draw.wall.getAllTiles() + reservedWallTiles).mapTo(mutableSetOf()) { it.id }),
        currentPlayerIndex = invokerIndex,
    )
}

/**
 * 取得玩家的座位索引。
 *
 * @param playerId 玩家識別碼。
 * @return 座位索引。
 * @throws IllegalArgumentException 玩家不在桌上時拋出。
 */
private fun TableState.seatOf(playerId: Uuid): Int = players.indexOfFirst { it.id == playerId }.also { index ->
    require(index >= 0) { "Invoking player does not belong to the game" }
}

/**
 * 情境載入後使用的台灣麻將規則；這一桌原本是台灣麻將時沿用它的設定。
 *
 * @param currentGame 目前同桌的權威遊戲。
 * @return 台灣麻將規則模組。
 */
private fun taiwanScenarioModule(currentGame: Game): TaiwanRuleModule {
    require(currentGame.tableState.players.size == PLAYER_COUNT) { "Taiwan debug scenarios require four players" }
    val config = currentGame.tableState.config as? TaiwanRuleConfig ?: TaiwanRuleConfig()
    require(config.useFlowerTiles) { "Taiwan debug scenarios require flower tiles" }
    return TaiwanRuleModule(BuiltInRuleModuleIds.TAIWAN, config)
}

/**
 * 依序把八張花的牌面放到 [targets]。
 *
 * @param targets 要放花牌的牌張，不可超過八張。
 * @return 牌張與花牌牌面的對應。
 */
private fun flowerFaces(targets: List<IdentifiedTile>): List<Pair<Uuid, Tile>> {
    require(targets.size <= ALL_FLOWERS) { "There are only eight flowers" }
    return targets.zip(TaiwanTileTypes.ALL.map(Tile::Extension)) { tile, face -> tile.id to face }
}

/**
 * 取得座位的全部手牌，包含摸牌位；座位索引超過人數時繞回。
 *
 * @param seatIndex 座位索引。
 * @return 該座位的手牌。
 */
private fun TableState.handTilesOf(seatIndex: Int): List<IdentifiedTile> {
    val hand = players[seatIndex % players.size].hand
    return hand.tiles + listOfNotNull(hand.lastDrawn)
}

/**
 * 把指定牌面放到指定牌張，與目前持有那個牌面、尚未指定的牌張交換牌面；牌張識別碼與位置不變。
 *
 * @param assignments 牌張與要放上的牌面，依序處理。
 * @return 交換牌面後的桌況。
 * @throws IllegalArgumentException 同一張牌指定兩次，或找不到可交換的牌面時拋出。
 */
private fun TableState.withFaces(assignments: List<Pair<Uuid, Tile>>): TableState {
    require(assignments.map { it.first }.distinct().size == assignments.size) { "Each tile may receive one face" }
    val allTiles = players.flatMap { player -> player.hand.tiles + listOfNotNull(player.hand.lastDrawn) } + tileWall.getAllTiles() + reservedWallTiles
    val faces = allTiles.associateTo(mutableMapOf()) { it.id to it.tile }
    val assigned = mutableSetOf<Uuid>()
    assignments.forEach { (targetId, face) ->
        val current = faces.getValue(targetId)
        if (current != face) {
            val partnerId = requireNotNull(allTiles.firstOrNull { it.id !in assigned && it.id != targetId && faces.getValue(it.id) == face }?.id) {
                "No tile left with face $face"
            }
            faces[partnerId] = current
            faces[targetId] = face
        }
        assigned += targetId
    }

    fun IdentifiedTile.withFace(): IdentifiedTile = copy(tile = faces.getValue(id))
    return copy(
        players = players.map { player ->
            player.copy(hand = player.hand.copy(tiles = player.hand.tiles.map { it.withFace() }, lastDrawn = player.hand.lastDrawn?.withFace()))
        },
        tileWall = TileWall(tileWall.getAllTiles().map { it.withFace() }),
        reservedWallTiles = reservedWallTiles.map { it.withFace() },
    )
}

/**
 * 換成 [tableState]，保留其餘對局資料。
 *
 * @param tableState 情境的桌況。
 * @return 新的遊戲。
 */
private fun Game.withTableState(tableState: TableState): Game = Game(
    tableState = tableState,
    flowConfig = flowConfig,
    matchId = matchId,
    aiPlayerStrategyKeys = aiPlayerStrategyKeys,
    hostId = hostId,
    roomPlayerIds = roomPlayerIds,
)

/** 一至九萬、一至六筒、九條：單吊九條的十六張聽牌。 */
private val READY_HAND: List<Tile> = (1..9).map { Tile.Numeric(Tile.Suit.Character, it) } +
    (1..6).map { Tile.Numeric(Tile.Suit.Dot, it) } +
    Tile.Numeric(Tile.Suit.Bamboo, 9)

/** 花牌總數。 */
private const val ALL_FLOWERS: Int = 8

/** 七搶一需要的花牌張數。 */
private const val SEVEN_FLOWERS: Int = 7

/** 途中第一次補花時，保留區最前端是開局後的第幾張補牌：莊家連補三張、下家與上家各一張之後。 */
private const val FIRST_MID_GAME_SUPPLEMENT: Int = 5
