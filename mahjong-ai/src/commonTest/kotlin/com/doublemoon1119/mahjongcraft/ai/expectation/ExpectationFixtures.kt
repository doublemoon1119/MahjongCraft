package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.AiDecisionPhase
import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.bundled.BundledRiichiExtension
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
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.uuid.Uuid

/** 期望值計算測試共用的日麻桌況。 */
internal object ExpectationFixtures {
    /** 反應視窗測試共用的穩定識別碼。 */
    val discardReactionId: Uuid = Uuid.random()

    /** 內建日麻規則模組。 */
    val module: RiichiRuleModule = RiichiRuleModule(BuiltInRuleModuleIds.RIICHI, RiichiRuleConfig())

    /** 登記內建規則的模組 registry。 */
    val moduleRegistry: MahjongModuleRegistryImpl = MahjongModuleRegistryImpl().apply {
        registerBundledRuleModules()
        freeze()
    }

    /** 登記日麻立直 handler 的擴充動作 registry。 */
    val extensionRegistry: ExtensionGameActionAiRegistry = ExtensionGameActionAiRegistry(moduleRegistry).apply {
        BundledRiichiExtension.registerGameActionAiHandlers(this)
        freeze()
    }

    /** 登記日麻對手模型的 registry。 */
    val opponentModelRegistry: OpponentModelRegistry = OpponentModelRegistry().apply {
        BundledRiichiExtension.registerOpponentModels(this)
        freeze()
    }

    /**
     * 內建日麻以 [level] 的讀牌深度建立的對手模型。
     *
     * @param level 對手模型使用的資訊等級。
     * @return 依該等級讀牌深度建立的模型。
     */
    fun opponentModel(level: InformationLevel): OpponentModel = opponentModelRegistry.create(module, level.readingDepth)

    /**
     * 建立萬子。
     *
     * @param value 牌面數字。
     * @return 指定數字的萬子。
     */
    fun m(value: Int): Tile = Tile.Numeric(Tile.Suit.Character, value)

    /**
     * 建立筒子。
     *
     * @param value 牌面數字。
     * @return 指定數字的筒子。
     */
    fun p(value: Int): Tile = Tile.Numeric(Tile.Suit.Dot, value)

    /**
     * 建立條子。
     *
     * @param value 牌面數字。
     * @return 指定數字的條子。
     */
    fun s(value: Int): Tile = Tile.Numeric(Tile.Suit.Bamboo, value)

    /**
     * 以 [tiles] 為立牌、[drawn] 為剛摸到的牌建立手牌。
     *
     * @param tiles 手牌中的立牌牌面。
     * @param drawn 剛摸到的牌面；null 表示沒有摸牌。
     * @param melds 已有副露，預設為空。
     * @return 以測試識別碼包裝輸入牌面的手牌。
     */
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
     * @param wind 玩家自風與起始座位。
     * @param hand 玩家的手牌，預設為空手牌。
     * @return 以日麻規則狀態與測試分數建立的玩家。
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
     * @param players 依座位順序排列的玩家清單；第一位作為莊家。
     * @return 以日麻設定與指定牌山建立的桌況。
     */
    fun table(players: List<MahjongPlayer>, liveTiles: Int = 56): TableState = FakeTableStateFactory.create(
        players = players,
        config = RiichiRuleConfig(),
        tileWall = TileWall(List(liveTiles + RiichiRuleConfig().deadTileCount) { FakeIdentifiedTileFactory.create(p(5)) }),
        dynamicRuleState = RiichiDynamicState(),
    )

    /**
     * 以 [self] 為觀察者的決策情境。
     *
     * @param table 要建立快照的桌況。
     * @param self 本次決策的玩家。
     * @param phase 決策階段，預設為自己的回合。
     * @param legalActions 該階段可選的合法動作，預設為空。
     * @return 以日麻規則計算強制捨牌的情境快照。
     */
    fun context(
        table: TableState,
        self: MahjongPlayer,
        phase: AiDecisionPhase = AiDecisionPhase.OwnTurn,
        legalActions: List<GameAction> = emptyList(),
    ): AiDecisionContext = AiDecisionContext(
        snapshot = table.toSnapshot(visibleHandPlayerIds = setOf(self.id), setAsideTiles = { emptyList() }),
        selfId = self.id,
        phase = phase,
        legalActions = legalActions,
        forcedDiscardTileId = module.forcedDiscardTileId(table, table.players.first { it.id == self.id }),
        discardReactionId = discardReactionId.takeIf { phase == AiDecisionPhase.RespondingToDiscard },
    )

    /**
     * 指定資訊範圍與估計參數的策略。
     *
     * @param level 策略可使用的資訊範圍。
     * @param parameters 估計參數，預設使用內建值。
     * @return 使用本 fixture registries 的期望值策略。
     */
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
