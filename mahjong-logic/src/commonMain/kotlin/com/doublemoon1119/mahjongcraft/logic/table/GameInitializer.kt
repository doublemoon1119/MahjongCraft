package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
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
     * @param id 對局的唯一識別碼（沿用房間的 Uuid）。
     * @param playerIds 參與對局的玩家 Uuid 列表（尚未分配座位，內部會隨機排序）。
     * @param module 該對局採用的規則模組，提供牌山工廠、牌河實作與規則配置。
     * @param aiPlayerStrategyKeys 由 AI 操控的玩家 Uuid 對應到其 AI 策略 key 的映射（key 集合須為
     *        [playerIds] 的子集）。開局後 `Room` 記錄即被刪除，這是「這個玩家是不是 AI、用哪個
     *        策略」這項資訊唯一的搬家管道，之後隨 [MahjongPlayer] 實例透過既有的 `.copy()` 機制
     *        自然延續，不需要另外維護。
     * @return 已完成洗牌、（若規則支援）擲骰開門、發牌、分數初始化的新結果，含權威 [TableState] 與
     * 只有平台呈現層需要的一次性擲骰／牌牆結構資料。
     * @throws IllegalArgumentException 當玩家人數不在該規則允許的範圍內時拋出。
     */
    fun initialize(
        id: Uuid,
        playerIds: List<Uuid>,
        module: MahjongRuleModule<*>,
        aiPlayerStrategyKeys: Map<Uuid, String> = emptyMap(),
    ): GameInitializationResult {
        require(playerIds.size in module.config.minPlayers..module.config.maxPlayers) {
            "Player count ${playerIds.size} out of range for this rule config " +
                "(${module.config.minPlayers}..${module.config.maxPlayers})"
        }

        val seats = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).take(playerIds.size)
        val shuffledPlayerIds = playerIds.shuffled()

        val openedWall = module.buildOpenedWall()
        var wall = openedWall.wall
        val players = shuffledPlayerIds.mapIndexed { index, playerId ->
            val (tiles, remainingWall) = wall.drawN(module.config.initialHandSize)
            wall = remainingWall
            MahjongPlayer(
                id = playerId,
                initialSeat = seats[index],
                hand = Hand(tiles = tiles),
                discardPile = module.createDiscardPile(),
                playerRuleState = module.createInitialPlayerRuleState(),
                aiStrategyKey = aiPlayerStrategyKeys[playerId],
            )
        }

        val tableState = TableState(
            id = id,
            players = players,
            config = module.config,
            tileWall = wall,
            dynamicRuleState = module.createInitialDynamicState(),
            wallOpening = openedWall.wallOpening,
            initialDeadWall = openedWall.initialDeadWall,
        ).init()

        return GameInitializationResult(
            tableState = tableState,
            diceRoll = openedWall.diceRoll,
            wallStructure = openedWall.structure,
        )
    }

    /**
     * 依連莊/過莊判定的結果（[roundAdvancement]），建立下一局的桌況：重新擲骰開門、重新建牌山、
     * 重新發手牌，並重置每局狀態（手牌、牌河、規則特有的玩家狀態），但保留跨局狀態（分數、局數/
     * 本場數/場風/各玩家方位，皆直接沿用 [roundAdvancement] 算好的結果；供託等動態桌況狀態則沿用
     * [previousDynamicRuleState]，是否歸零由胡牌結算階段的
     * [com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule.collectStickPot] 決定，
     * 這裡單純延續、不重新判斷）。
     *
     * 座位順序（[roundAdvancement] 的 `players` 列表順序）不會重新洗牌，只有發牌本身、每局狀態
     * 會全部重來——跟 [initialize] 開新對局時「連座位順序都重新洗牌、分數歸零」不同。連莊仍是新的
     * 一局，也會重新擲骰開門。
     *
     * @param gameId 對局 Uuid（延續同一場對局，不是新對局）。
     * @param roundAdvancement 連莊/過莊判定後的結果（見 [TableState.advanceRound]），提供新的
     *        玩家列表（含已重新指派的座位方位）、局數、本場數、場風。
     * @param previousDynamicRuleState 上一局結束時的動態桌況狀態（例如供託／立直棒數量）。
     * @param module 該對局採用的規則模組。
     * @return 已完成重新擲骰開門、重新建牌山、發牌，並套用連莊/過莊結果的新結果，代表下一局的起始
     * 狀態，含權威 [TableState] 與只有平台呈現層需要的一次性擲骰／牌牆結構資料。
     */
    fun startNextRound(
        gameId: Uuid,
        roundAdvancement: RoundAdvancementResult,
        previousDynamicRuleState: DynamicRuleState?,
        module: MahjongRuleModule<*>,
    ): GameInitializationResult {
        val openedWall = module.buildOpenedWall()
        var wall = openedWall.wall
        val players = roundAdvancement.players.map { player ->
            val (tiles, remainingWall) = wall.drawN(module.config.initialHandSize)
            wall = remainingWall
            player.copy(
                hand = Hand(tiles = tiles),
                discardPile = module.createDiscardPile(),
                playerRuleState = module.createInitialPlayerRuleState(),
                passedTilesInRound = emptySet(),
                actionHistory = emptyList(),
            )
        }
        val dealerIndex = players.indexOfFirst { it.currentWind == Wind.EAST }

        val tableState = TableState(
            id = gameId,
            players = players,
            config = module.config,
            tileWall = wall,
            prevalentWind = roundAdvancement.prevalentWind,
            roundNumber = roundAdvancement.roundNumber,
            comboCount = roundAdvancement.comboCount,
            currentPlayerIndex = dealerIndex,
            dynamicRuleState = previousDynamicRuleState,
            wallOpening = openedWall.wallOpening,
            initialDeadWall = openedWall.initialDeadWall,
        )

        return GameInitializationResult(
            tableState = tableState,
            diceRoll = openedWall.diceRoll,
            wallStructure = openedWall.structure,
        )
    }

    /**
     * 建立一副已套用開門結果的牌山：洗牌、（若規則同時提供 [MahjongRuleModule.createWallOpeningPolicy]
     * 與 [MahjongRuleModule.createWallLayout]）擲骰、解析開門位置、依牌牆布局重排成正式摸牌順序並
     * 切出王牌。任一者尚未支援時，直接沿用原始洗牌結果、不擲骰、沒有王牌——通用初始化流程不得自行
     * 為尚未支援開門流程的規則套用其他玩法的公式或假設固定張數。
     */
    private fun MahjongRuleModule<*>.buildOpenedWall(): OpenedWall {
        val shuffledWall = createWallFactory().create()
        val openingPolicy = createWallOpeningPolicy()
        val layout = createWallLayout()
        if (openingPolicy == null || layout == null) {
            return OpenedWall(
                wall = shuffledWall,
                wallOpening = null,
                initialDeadWall = emptyList(),
                diceRoll = null,
                structure = null,
            )
        }

        val diceRoll = DiceRollResult.of(List(openingPolicy.diceCount) { (1..DICE_FACES).random() })
        val wallOpening = openingPolicy.resolve(diceRoll)
        val layoutResult = layout.resolve(shuffledWall.getAllTiles(), wallOpening)

        return OpenedWall(
            wall = TileWall(layoutResult.drawOrder),
            wallOpening = wallOpening,
            initialDeadWall = layoutResult.initialDeadWall,
            diceRoll = diceRoll,
            structure = layoutResult.structure,
        )
    }

    /**
     * [buildOpenedWall] 的結果：已套用（或未套用）開門結果的牌山、對應的開門位置及王牌快照，以及只有
     * 平台呈現層需要的權威擲骰結果與牌牆結構座標。
     */
    private data class OpenedWall(
        val wall: TileWall,
        val wallOpening: WallOpening?,
        val initialDeadWall: List<IdentifiedTile>,
        val diceRoll: DiceRollResult?,
        val structure: Map<Uuid, TileWallPosition>?,
    )

    /** 六面骰的點數上限。 */
    private const val DICE_FACES: Int = 6

    /**
     * 連續從牌山最前方摸取 [count] 張牌。
     *
     * @return 摸到的牌列表（依摸牌順序排列）與摸牌後的新 [TileWall] 實例。
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
}
