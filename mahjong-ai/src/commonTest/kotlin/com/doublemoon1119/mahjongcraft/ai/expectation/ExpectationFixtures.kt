package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.AiDecisionPhase
import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.riichi.registerRiichiGameActionHandler
import com.doublemoon1119.mahjongcraft.ai.riichi.registerRiichiOpponentModel
import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory

/** 期望值計算測試共用的日麻桌況。 */
internal object ExpectationFixtures {
    /** 內建日麻規則模組。 */
    val module: RiichiRuleModule = RiichiRuleModule(BuiltInRuleModuleIds.RIICHI, RiichiRuleConfig())

    /** 登記內建規則的模組 registry。 */
    val moduleRegistry: MahjongModuleRegistryImpl = MahjongModuleRegistryImpl().apply {
        registerBuiltInRuleModules()
        freeze()
    }

    /** 登記日麻立直 handler 的擴充動作 registry。 */
    val extensionRegistry: ExtensionGameActionAiRegistry = ExtensionGameActionAiRegistry().apply {
        registerRiichiGameActionHandler(moduleRegistry)
        freeze()
    }

    /** 登記日麻對手模型的 registry。 */
    val opponentModelRegistry: OpponentModelRegistry = OpponentModelRegistry().apply {
        registerRiichiOpponentModel()
        freeze()
    }

    /** 內建日麻以 [level] 的讀牌深度建立的對手模型。 */
    fun opponentModel(level: InformationLevel): OpponentModel = opponentModelRegistry.create(module, level.readingDepth)

    /** 萬子。 */
    fun m(value: Int): Tile = Tile.Numeric(Tile.Suit.Character, value)

    /** 筒子。 */
    fun p(value: Int): Tile = Tile.Numeric(Tile.Suit.Dot, value)

    /** 條子。 */
    fun s(value: Int): Tile = Tile.Numeric(Tile.Suit.Bamboo, value)

    /** 以 [tiles] 為立牌、[drawn] 為剛摸到的牌建立手牌。 */
    fun hand(
        tiles: List<Tile>,
        drawn: Tile? = null,
        melds: List<Meld> = emptyList(),
    ): Hand = Hand(
        tiles = tiles.map(FakeIdentifiedTileFactory::create),
        melds = melds,
        lastDrawn = drawn?.let(FakeIdentifiedTileFactory::create),
    )

    /**
     * 一位日麻玩家。
     *
     * @param discards 牌河，依打出順序。
     * @param riichi 是否已宣告立直。
     */
    fun player(
        wind: Wind,
        hand: Hand = Hand(),
        discards: List<Tile> = emptyList(),
        riichi: Boolean = false,
    ): MahjongPlayer = FakeMahjongPlayerFactory.create(
        initialSeat = wind,
        hand = hand,
        discardPile = discards.fold(RiichiDiscardPile()) { pile, tile -> pile.discardTile(FakeIdentifiedTileFactory.create(tile)) },
        playerRuleState = RiichiPlayerState(riichiTile = if (riichi) FakeIdentifiedTileFactory.create(Tile.Honor.West) else null),
    ).copy(score = 25000)

    /**
     * 依座位順序排列的桌況，第一位為莊家。
     *
     * @param liveTiles 牌山中扣除王牌後還能摸的張數。
     */
    fun table(players: List<MahjongPlayer>, liveTiles: Int = 56): TableState = FakeTableStateFactory.create(
        players = players,
        config = RiichiRuleConfig(),
        tileWall = TileWall(List(liveTiles + RiichiRuleConfig().deadTileCount) { FakeIdentifiedTileFactory.create(p(5)) }),
        dynamicRuleState = RiichiDynamicState(),
    )

    /** 以 [self] 為觀察者的決策情境。 */
    fun context(
        table: TableState,
        self: MahjongPlayer,
        phase: AiDecisionPhase = AiDecisionPhase.OwnTurn,
        legalActions: List<GameAction> = emptyList(),
    ): AiDecisionContext = AiDecisionContext(
        snapshot = table.toSnapshot(visibleHandPlayerIds = setOf(self.id)),
        selfId = self.id,
        phase = phase,
        legalActions = legalActions,
        forcedDiscardTileId = module.forcedDiscardTileId(table, table.players.first { it.id == self.id }),
    )

    /** 指定資訊範圍與估計參數的策略。 */
    fun strategy(
        level: InformationLevel,
        parameters: ExpectationParameters = ExpectationParameters.DEFAULT,
    ): ExpectedValueAiStrategy = ExpectedValueAiStrategy(
        level = level,
        moduleRegistry = moduleRegistry,
        extensionActionRegistry = extensionRegistry,
        opponentModels = opponentModelRegistry,
        parameters = parameters,
    )
}
