package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TAIWAN_REVEAL_FLOWER_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPlayerTileAreaIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.TurnPhase
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import kotlin.uuid.Uuid

/**
 * 台灣麻將的驗收情境，四人桌適用。
 *
 * 所有情境都以正式初始化流程發牌，再交換指定牌張的牌面，讓花牌或指定的牌落在需要的位置；牌張識別碼、牌牆結構與
 * 實體座標都沿用正式初始化的結果。座位與莊家沿用這一桌目前的座位與莊家。載入後改用台灣麻將；這一桌原本是台灣麻將時
 * 沿用它的設定。
 */
internal object TaiwanDebugGameScenarios {
    /** 所有台灣麻將驗收情境。 */
    val all: List<DebugGameScenario> = listOf(
        // 局首逐輪補花與途中連續補花：莊家先摸開門的第一張，再從莊家開始補花。莊家起手兩張花、下家一張、上家一張，
        // 對家沒有花；莊家第一輪補到的第一張又是花，等這一輪結束後的下一輪再補。莊家打出第一張牌後，下家摸到的牌是花，
        // 補到的牌又是花，當場再補一次。第八張花在活牌中段。
        TaiwanOpeningScenario(id = "mahjongcraft:taiwan/debug/flower_replacement") { dealt, _ ->
            val dealer = dealt.dealerIndex
            val supplementalOrder = dealt.reservedWallTiles
            flowerFaces(
                dealt.handTilesOf(dealer).take(2) +
                    dealt.handTilesOf(dealer + 1).take(1) +
                    dealt.handTilesOf(dealer + 3).take(1) +
                    supplementalOrder[0] +
                    dealt.tileWall.getAllTiles()[DOWNSTREAM_FIRST_DRAW] +
                    supplementalOrder[FIRST_MID_GAME_SUPPLEMENT] +
                    dealt.tileWall.getAllTiles()[dealt.tileWall.remainingCount / 2],
            )
        },
        // 七搶一：呼叫者起手七張花，呼叫者的下家起手一張花；下家亮出第八張花時，呼叫者可以選擇胡牌或放棄。
        TaiwanOpeningScenario(id = "mahjongcraft:taiwan/debug/seven_flowers") { dealt, invoker ->
            flowerFaces(dealt.handTilesOf(invoker).take(SEVEN_FLOWERS) + dealt.handTilesOf(invoker + 1).take(1))
        },
        // 八仙過海：呼叫者起手八張花，補完花後可以選擇胡牌或放棄。
        TaiwanOpeningScenario(id = "mahjongcraft:taiwan/debug/eight_flowers") { dealt, invoker ->
            flowerFaces(dealt.handTilesOf(invoker).take(ALL_FLOWERS))
        },
        // 宣告聽牌：呼叫者起手就是一至九萬、一至六筒、九條，單吊九條聽牌。第一次摸牌後打出摸到的牌就能宣告聽牌：
        // 呼叫者是莊家時為天聽，不是莊家時為地聽。八張花都在活牌尾端，開局不補花。
        TaiwanOpeningScenario(id = "mahjongcraft:taiwan/debug/ready_declaration") { dealt, invoker ->
            dealt.handTilesOf(invoker).zip(READY_HAND) { tile, face -> tile.id to face } +
                flowerFaces(dealt.tileWall.getAllTiles().takeLast(ALL_FLOWERS))
        },
        TaiwanLastTileFlowerScenario,
        TaiwanBeforePaoPonScenario,
        TaiwanCrowdedMeldsScenario,
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
        val initialization = formalFirstRoundKeepingSeats(context, module)
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
    override val id: String = "mahjongcraft:taiwan/debug/last_tile_flower"

    override fun build(context: DebugGameScenarioContext): DebugGameScenarioResult {
        val currentGame = context.currentGame
        val module = taiwanScenarioModule(currentGame)
        val initialization = formalFirstRoundKeepingSeats(context, module)
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
 * 包牌：呼叫者已碰出發、中兩組，上家剛打出白，呼叫者碰白就以第三組三元牌副露湊齊大三元，上家成為包牌者。
 *
 * 呼叫者的立牌為白白、二三萬、五五筒、六七八條與一張北；碰白後打出北即聽一、四萬。活牌最前端交錯排著四萬與一萬：
 * 其他三家摸到後打出即可胡牌（上家打出為包牌者本人放槍，其他兩家打出為平分），輪到呼叫者時第一張即可自摸（包牌
 * 自摸）。其他三家的立牌固定為 [PAO_OPPONENT_HAND]：沒有對子，不含三元牌、北與一、四萬，無法聽牌，也無法碰呼叫者
 * 打出的牌。八張花都在活牌尾端。載入後開啟包牌選項；這一桌原本是台灣麻將時沿用其餘設定。
 */
private object TaiwanBeforePaoPonScenario : DebugGameScenario {
    override val id: String = "mahjongcraft:taiwan/debug/before_pao_pon"

    override fun build(context: DebugGameScenarioContext): DebugGameScenarioResult {
        val baseModule = taiwanScenarioModule(context.currentGame)
        val module = TaiwanRuleModule(BuiltInRuleModuleIds.TAIWAN, baseModule.config.copy(paoLiability = true))
        val initialization = formalFirstRoundKeepingSeats(context, module)
        val initial = initialization.tableState
        val invokerIndex = initial.seatOf(context.invokingPlayerId)
        val discarderIndex = (invokerIndex + PLAYER_COUNT - 1) % PLAYER_COUNT
        val liveTiles = initial.tileWall.getAllTiles()
        val handFaces = initial.players.indices.flatMap { seatIndex ->
            val faces = if (seatIndex == invokerIndex) PAO_INVOKER_HAND else PAO_OPPONENT_HAND
            initial.handTilesOf(seatIndex).zip(faces) { tile, face -> tile.id to face }
        }
        val calledFace = liveTiles.first().id to Tile.Honor.White
        val winningFaces = liveTiles.drop(1).take(PAO_WINNING_DRAWS).mapIndexed { index, tile -> tile.id to PAO_WAITS[index % PAO_WAITS.size] }
        val dealt = initial.withFaces(handFaces + calledFace + winningFaces + flowerFaces(liveTiles.takeLast(ALL_FLOWERS)))

        val draw = dealt.tileWall.draw()
        val calledTile = requireNotNull(draw.tile) { "The live wall is empty" }
        val players = dealt.players.mapIndexed { seatIndex, player ->
            when (seatIndex) {
                invokerIndex -> player.withPaoInvokerHand(dealt.handTilesOf(seatIndex), module)
                discarderIndex -> player.copy(
                    hand = player.hand.organize(module.tileOrder),
                    discardPile = TaiwanDiscardPile().discardTile(calledTile),
                    actionHistory = listOf(GameAction.Draw, GameAction.Discard(calledTile.id)),
                )
                else -> player.copy(hand = player.hand.organize(module.tileOrder))
            }
        }
        val remainingWallIds = (draw.wall.getAllTiles() + dealt.reservedWallTiles).mapTo(mutableSetOf()) { it.id }
        val state = dealt.copy(
            players = players,
            tileWall = draw.wall,
            physicalWallLayout = dealt.physicalWallLayout?.retainOnly(remainingWallIds),
            currentPlayerIndex = discarderIndex,
            lastDiscardTileId = calledTile.id,
            turnPhase = TurnPhase.AwaitingDiscardReaction(
                PendingReaction(
                    discarderId = players[discarderIndex].id,
                    tileId = calledTile.id,
                    eligiblePlayerIds = setOf(context.invokingPlayerId),
                    ronPlayerIds = setOf(context.invokingPlayerId),
                    id = Uuid.random(),
                ),
            ),
        )
        return DebugGameScenarioResult(
            game = context.currentGame.withTableState(state),
            wallStructure = requireNotNull(initialization.wallStructure) { "Taiwan initialization did not produce wall structure" },
            wallLayout = requireNotNull(initialization.initialPhysicalWallLayout) { "Taiwan initialization did not produce physical wall layout" },
        )
    }

    /**
     * 把呼叫者的牌排成發、中兩組碰與其餘立牌。
     *
     * @param tiles 已換好牌面的十六張牌，前六張是兩組碰。
     * @param module 用來整理立牌順序的規則模組。
     * @return 排好手牌的呼叫者。
     */
    private fun MahjongPlayer.withPaoInvokerHand(tiles: List<IdentifiedTile>, module: TaiwanRuleModule): MahjongPlayer = copy(
        hand = Hand(
            tiles = tiles.drop(PAO_INVOKER_MELD_TILES),
            melds = tiles.take(PAO_INVOKER_MELD_TILES).chunked(PON_SIZE).map { pon ->
                Meld(
                    type = MeldType.Pon,
                    tiles = pon,
                    sourceTile = pon.last(),
                    sourceDirection = RelativeDirection.Across,
                    publiclyRevealedTileIds = pon.mapTo(mutableSetOf()) { it.id },
                    faceDownTileIds = emptySet(),
                )
            },
        ).organize(module.tileOrder),
    )
}

/**
 * 副露與花牌多到副露那一列放不下：呼叫者已有三組明槓、一組碰與八張花，副露列放不下的部分移到手牌那一列的右手邊。
 *
 * 呼叫者的明槓依序是一萬、四筒、二條，被鳴的牌分別來自上家、對家、下家，碰的是紅中；立牌是兩張白、四條與六條。上家剛打出
 * 第三張白，呼叫者可以碰白成為第五組副露。全桌只有三個槓，不會觸發四槓流局；碰不需要從牌尾補牌。其他三家的立牌固定為
 * [PAO_OPPONENT_HAND]，不會碰或胡。
 */
private object TaiwanCrowdedMeldsScenario : DebugGameScenario {
    override val id: String = "mahjongcraft:taiwan/debug/crowded_melds"

    override fun build(context: DebugGameScenarioContext): DebugGameScenarioResult {
        val module = taiwanScenarioModule(context.currentGame)
        val initialization = formalFirstRoundKeepingSeats(context, module)
        val initial = initialization.tableState
        val invokerIndex = initial.seatOf(context.invokingPlayerId)
        val discarderIndex = (invokerIndex + PLAYER_COUNT - 1) % PLAYER_COUNT
        val invokerTiles = initial.handTilesOf(invokerIndex)
        val liveTiles = initial.tileWall.getAllTiles()
        // 呼叫者需要的副露與立牌比起手多時，從活牌最前端補足；莊家起手多一張，從活牌取的牌少一張。
        val ownFaces = CROWDED_MELD_FACES + CROWDED_HAND
        val fromLive = ownFaces.size - invokerTiles.size
        val ownTiles = invokerTiles + liveTiles.take(fromLive)
        val calledTile = liveTiles[fromLive]
        val flowerTiles = liveTiles.drop(fromLive + 1).take(ALL_FLOWERS)
        val opponentFaces = initial.players.indices.filter { it != invokerIndex }.flatMap { seatIndex ->
            initial.handTilesOf(seatIndex).zip(PAO_OPPONENT_HAND) { tile, face -> tile.id to face }
        }
        val dealt = initial.withFaces(
            ownTiles.zip(ownFaces) { tile, face -> tile.id to face } +
                (calledTile.id to Tile.Honor.White) +
                flowerFaces(flowerTiles) +
                opponentFaces,
        )
        val faceById = (dealt.players.flatMap { player -> player.hand.tiles + listOfNotNull(player.hand.lastDrawn) } + dealt.tileWall.getAllTiles())
            .associateBy { it.id }
        val own = ownTiles.map { faceById.getValue(it.id) }
        val meldTiles = own.take(CROWDED_MELD_FACES.size)
        val kongs = meldTiles.take(CROWDED_KONG_COUNT * KONG_SIZE).chunked(KONG_SIZE).zip(CROWDED_KONG_SOURCES) { kong, direction ->
            claimedMeld(MeldType.OpenKan, kong, direction)
        }
        val pon = claimedMeld(MeldType.Pon, meldTiles.drop(CROWDED_KONG_COUNT * KONG_SIZE), RelativeDirection.Across)
        val called = faceById.getValue(calledTile.id)
        val flowers = flowerTiles.map { faceById.getValue(it.id) }
        val removedIds = (own + called + flowers).mapTo(mutableSetOf()) { it.id }
        val wall = TileWall(dealt.tileWall.getAllTiles().filterNot { it.id in removedIds })
        val players = dealt.players.mapIndexed { seatIndex, player ->
            when (seatIndex) {
                invokerIndex -> player.copy(
                    hand = Hand(tiles = own.drop(CROWDED_MELD_FACES.size), melds = kongs + pon).organize(module.tileOrder),
                    tileAreas = player.tileAreas.withArea(TaiwanPlayerTileAreaIds.FLOWERS, flowers),
                )
                discarderIndex -> player.copy(
                    hand = player.hand.organize(module.tileOrder),
                    discardPile = TaiwanDiscardPile().discardTile(called),
                    actionHistory = listOf(GameAction.Draw, GameAction.Discard(called.id)),
                )
                else -> player.copy(hand = player.hand.organize(module.tileOrder))
            }
        }
        val remainingWallIds = (wall.getAllTiles() + dealt.reservedWallTiles).mapTo(mutableSetOf()) { it.id }
        val state = dealt.copy(
            players = players,
            tileWall = wall,
            physicalWallLayout = dealt.physicalWallLayout?.retainOnly(remainingWallIds),
            currentPlayerIndex = discarderIndex,
            lastDiscardTileId = called.id,
            turnPhase = TurnPhase.AwaitingDiscardReaction(
                PendingReaction(
                    discarderId = players[discarderIndex].id,
                    tileId = called.id,
                    eligiblePlayerIds = setOf(context.invokingPlayerId),
                    ronPlayerIds = emptySet(),
                    id = Uuid.random(),
                ),
            ),
        )
        return DebugGameScenarioResult(
            game = context.currentGame.withTableState(state),
            wallStructure = requireNotNull(initialization.wallStructure) { "Taiwan initialization did not produce wall structure" },
            wallLayout = requireNotNull(initialization.initialPhysicalWallLayout) { "Taiwan initialization did not produce physical wall layout" },
        )
    }

    /**
     * 建立一組鳴他家的牌而成立的副露，最後一張是被鳴的牌。
     *
     * @param type 副露種類。
     * @param tiles 副露的牌。
     * @param direction 被鳴的牌的來源。
     * @return 副露。
     */
    private fun claimedMeld(type: MeldType, tiles: List<IdentifiedTile>, direction: RelativeDirection): Meld = Meld(
        type = type,
        tiles = tiles,
        sourceTile = tiles.last(),
        sourceDirection = direction,
        publiclyRevealedTileIds = tiles.mapTo(mutableSetOf()) { it.id },
        faceDownTileIds = emptySet(),
    )
}

/**
 * 讓 [invokerId] 從活牌最前端摸一張牌，成為輪到行動的玩家，階段為等行動。
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
        turnPhase = TurnPhase.AwaitingAction,
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

/** 包牌情境呼叫者的十六張牌：前六張是發、中兩組碰，其餘為立牌，碰白並打出北後聽一、四萬。 */
private val PAO_INVOKER_HAND: List<Tile> = List(PON_SIZE) { Tile.Honor.Green } +
    List(PON_SIZE) { Tile.Honor.Red } +
    listOf(
        Tile.Honor.White,
        Tile.Honor.White,
        Tile.Numeric(Tile.Suit.Character, 2),
        Tile.Numeric(Tile.Suit.Character, 3),
        Tile.Numeric(Tile.Suit.Dot, 5),
        Tile.Numeric(Tile.Suit.Dot, 5),
        Tile.Numeric(Tile.Suit.Bamboo, 6),
        Tile.Numeric(Tile.Suit.Bamboo, 7),
        Tile.Numeric(Tile.Suit.Bamboo, 8),
        Tile.Honor.North,
    )

/** 包牌情境其他三家的十六張牌：沒有對子，不含三元牌、北與一、四萬。 */
private val PAO_OPPONENT_HAND: List<Tile> = listOf(
    Tile.Numeric(Tile.Suit.Character, 5),
    Tile.Numeric(Tile.Suit.Character, 6),
    Tile.Numeric(Tile.Suit.Character, 7),
    Tile.Numeric(Tile.Suit.Character, 9),
    Tile.Numeric(Tile.Suit.Dot, 1),
    Tile.Numeric(Tile.Suit.Dot, 3),
    Tile.Numeric(Tile.Suit.Dot, 7),
    Tile.Numeric(Tile.Suit.Dot, 8),
    Tile.Numeric(Tile.Suit.Dot, 9),
    Tile.Numeric(Tile.Suit.Bamboo, 1),
    Tile.Numeric(Tile.Suit.Bamboo, 3),
    Tile.Numeric(Tile.Suit.Bamboo, 5),
    Tile.Numeric(Tile.Suit.Bamboo, 9),
    Tile.Honor.East,
    Tile.Honor.South,
    Tile.Honor.West,
)

/** 副露擁擠情境的明槓組數；全桌合計四個槓會流局。 */
private const val CROWDED_KONG_COUNT: Int = 3

/** 副露擁擠情境呼叫者副露的牌面：一萬、四筒、二條各一組明槓，再一組碰紅中；都不在 [PAO_OPPONENT_HAND] 中。 */
private val CROWDED_MELD_FACES: List<Tile> = listOf(
    Tile.Numeric(Tile.Suit.Character, 1),
    Tile.Numeric(Tile.Suit.Dot, 4),
    Tile.Numeric(Tile.Suit.Bamboo, 2),
).flatMap { face -> List(KONG_SIZE) { face } } + List(PON_SIZE) { Tile.Honor.Red }

/** 副露擁擠情境三組明槓被鳴的牌的來源。 */
private val CROWDED_KONG_SOURCES: List<RelativeDirection> = listOf(
    RelativeDirection.Left,
    RelativeDirection.Across,
    RelativeDirection.Right,
)

/** 副露擁擠情境呼叫者的立牌：兩張白、四條與六條，可以碰上家打出的白。 */
private val CROWDED_HAND: List<Tile> = listOf(
    Tile.Honor.White,
    Tile.Honor.White,
    Tile.Numeric(Tile.Suit.Bamboo, 4),
    Tile.Numeric(Tile.Suit.Bamboo, 6),
)

/** 一組槓的張數。 */
private const val KONG_SIZE: Int = 4

/** 包牌情境呼叫者聽的牌，依序交錯排在活牌最前端。 */
private val PAO_WAITS: List<Tile> = listOf(Tile.Numeric(Tile.Suit.Character, 4), Tile.Numeric(Tile.Suit.Character, 1))

/** 包牌情境活牌最前端排放聽牌的張數。 */
private const val PAO_WINNING_DRAWS: Int = 6

/** 包牌情境呼叫者兩組碰的張數。 */
private const val PAO_INVOKER_MELD_TILES: Int = 6

/** 一組碰的張數。 */
private const val PON_SIZE: Int = 3

/** 一至九萬、一至六筒、九條：單吊九條的十六張聽牌。 */
private val READY_HAND: List<Tile> = (1..9).map { Tile.Numeric(Tile.Suit.Character, it) } +
    (1..6).map { Tile.Numeric(Tile.Suit.Dot, it) } +
    Tile.Numeric(Tile.Suit.Bamboo, 9)

/** 花牌總數。 */
private const val ALL_FLOWERS: Int = 8

/** 七搶一需要的花牌張數。 */
private const val SEVEN_FLOWERS: Int = 7

/** 下家第一次摸牌摸到的是第幾張活牌：莊家先摸走開門的第一張。 */
private const val DOWNSTREAM_FIRST_DRAW: Int = 1

/** 途中第一次補花時，保留區最前端是開局後的第幾張補牌：莊家兩輪共補三張、下家與上家各一張之後。 */
private const val FIRST_MID_GAME_SUPPLEMENT: Int = 5
