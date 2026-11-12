package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionContext
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.judgment.TileSelectionRequirement
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 台灣麻將的合法動作判定器，只列出鳴牌、槓、胡與宣告聽牌等額外動作；一般捨牌由呼叫端依手牌建立。
 *
 * - 過水期間不能胡也不能自摸。
 * - 天聽、地聽的時機可以宣告聽牌；其他時機只有開啟報聽時才能宣告。
 * - 宣告聽牌後不能吃、碰、明槓或暗槓，只能用剛摸進來的牌加槓。
 * - 不能槓上家打出的牌；只能搶加槓，不能搶暗槓；別人移出手牌的牌不能胡或鳴。
 * - 最後一張可打出的牌只能胡；槓需要至少還有一張活牌。
 *
 * @property config 規則設定。
 */
class TaiwanLegalActionValidator(private val config: TaiwanRuleConfig) : LegalActionValidator {
    private val shantenCalculator = TaiwanShantenCalculator()
    private val winEvaluation = TaiwanWinEvaluation(config)

    override fun getLegalActions(context: LegalActionContext): List<GameAction> {
        val tableState = context.tableState
        val player = context.player.copy(hand = context.handBeforeIncoming)
        val incomingTile = context.incomingTile
        val isDeclared = player.components[TaiwanPlayerComponents.READY_DECLARATION] != null
        val canWin = player.components[TaiwanPlayerComponents.PASSED_WIN] == null
        val wallHasMoreTiles = tableState.tileWall.remainingCount > 0
        val legalActions = mutableListOf<GameAction>()

        if (context.sourceDirection == RelativeDirection.Self) {
            if (!isDeclared && wallHasMoreTiles && isReadyDeclarable(tableState, player) && readyDiscardTileIds(context.player).isNotEmpty()) {
                legalActions += TAIWAN_DECLARE_READY_ACTION
            }
            if (incomingTile != null) {
                if (canWin &&
                    isWinning(context.handWithIncoming) &&
                    winEvaluation.evaluate(tableState, player, incomingTile, isTsumo = true, isRobbingKong = false).isWinningHand
                ) {
                    legalActions += GameAction.Tsumo
                }
                if (wallHasMoreTiles) {
                    if (player.hand.exposedMelds.any { it.type == MeldType.Pon && it.tiles.first().tile == incomingTile.tile }) {
                        legalActions += GameAction.Kan(GameAction.KanType.ADDED_KAN, incomingTile.id, emptyList())
                    }
                    if (!isDeclared) legalActions += closedKanActions(context.handWithIncoming, incomingTile)
                }
            }
        } else if (incomingTile != null && context.sourceAction !is GameAction.Extension) {
            val sourceAction = context.sourceAction
            val isRobbing = sourceAction is GameAction.Kan
            val isRobbingAddedKan = sourceAction is GameAction.Kan && sourceAction.type == GameAction.KanType.ADDED_KAN
            if (canWin &&
                (!isRobbing || isRobbingAddedKan) &&
                isWinning(context.handWithIncoming) &&
                winEvaluation.evaluate(tableState, player, incomingTile, isTsumo = false, isRobbingKong = isRobbingAddedKan).isWinningHand
            ) {
                legalActions += GameAction.Ron(incomingTile.id)
            }
            if (!isRobbing && !isDeclared && wallHasMoreTiles) {
                legalActions += claimActions(player, incomingTile, context.sourceDirection)
            }
        }
        if (legalActions.isNotEmpty()) legalActions += GameAction.Pass
        return legalActions
    }

    /** 宣告聽牌需要另外指定打出的牌：打出後仍聽牌、且規則允許打出的立牌。 */
    override fun tileSelectionRequirement(
        tableState: TableState,
        player: MahjongPlayer,
        action: GameAction,
    ): TileSelectionRequirement? {
        if (action != TAIWAN_DECLARE_READY_ACTION) return null
        return TileSelectionRequirement(readyDiscardTileIds(player))
    }

    /**
     * 判斷玩家這一打的時機是否可以宣告聽牌。
     *
     * @param tableState 目前桌況。
     * @param player 即將打牌的玩家。
     * @return 天聽、地聽時機，或已開啟報聽時為 true。
     */
    internal fun isReadyDeclarable(tableState: TableState, player: MahjongPlayer): Boolean = TaiwanReadyDeclarationRules.isDeclarable(
        config = config,
        kind = readyKind(tableState, player),
    )

    /**
     * 依玩家這一打的時機判斷宣告種類。
     *
     * @param tableState 目前桌況。
     * @param player 即將打牌的玩家。
     * @return 宣告種類。
     */
    internal fun readyKind(tableState: TableState, player: MahjongPlayer): TaiwanReadyKind = TaiwanReadyDeclarationRules.kind(
        tableHasMelds = tableState.players.any { it.hand.melds.isNotEmpty() },
        isDealer = player.id == tableState.dealerPlayerId,
        previousDiscards = player.discardPile.entries.size,
    )

    /**
     * 列出打出後仍聽牌、且規則允許打出的立牌。
     *
     * @param player 即將打牌的玩家，手牌含剛摸入的牌。
     * @return 可以用來宣告聽牌的捨牌。
     */
    internal fun readyDiscardTileIds(player: MahjongPlayer): Set<Uuid> {
        val discardable = TaiwanDiscardRestriction.discardableTileIds(player)
        return player.hand.standingTiles
            .filter { candidate -> discardable == null || candidate.id in discardable }
            .filter { candidate ->
                val discardResult = player.hand.discardById(candidate.id) ?: return@filter false
                waits(discardResult.hand).isNotEmpty()
            }
            .mapTo(mutableSetOf()) { it.id }
    }

    /**
     * 列出手牌聽的牌。
     *
     * @param hand 不含胡牌張的手牌。
     * @return 聽的牌；沒有聽牌時為空集合。
     */
    internal fun waits(hand: Hand): Set<Tile> = (shantenCalculator.calculate(Hand(tiles = hand.standingTiles, melds = hand.melds)) as? ShantenResult.Tenpai)
        ?.winningTiles
        ?.toSet()
        .orEmpty()

    /**
     * 判斷含胡牌張的手牌是否已成和牌形。
     *
     * @param handWithIncoming 含胡牌張的手牌。
     * @return 已成和牌形時為 true。
     */
    private fun isWinning(handWithIncoming: Hand): Boolean = shantenCalculator.calculate(handWithIncoming) is ShantenResult.Complete

    /**
     * 列出手中四張相同牌組成的暗槓。
     *
     * @param handWithIncoming 含剛摸入牌的手牌。
     * @param incomingTile 剛摸入的牌；組成暗槓時優先作為宣告牌。
     * @return 暗槓動作。
     */
    private fun closedKanActions(handWithIncoming: Hand, incomingTile: IdentifiedTile): List<GameAction> = handWithIncoming.standingTiles
        .groupBy { it.tile }
        .values
        .filter { it.size == 4 }
        .map { candidate ->
            val trigger = candidate.firstOrNull { it.id == incomingTile.id } ?: candidate.first()
            GameAction.Kan(GameAction.KanType.CLOSED_KAN, trigger.id, candidate.filterNot { it.id == trigger.id }.map { it.id })
        }

    /**
     * 列出別人打出的牌可以吃、碰或明槓的組合。
     *
     * @param player 手牌不含來源牌的玩家。
     * @param incomingTile 別人打出的牌。
     * @param sourceDirection 打出者相對於玩家的方向。
     * @return 鳴牌動作。
     */
    private fun claimActions(
        player: MahjongPlayer,
        incomingTile: IdentifiedTile,
        sourceDirection: RelativeDirection,
    ): List<GameAction> {
        val standing = player.hand.standingTiles
        val matching = standing.filter { it.tile == incomingTile.tile }
        val actions = mutableListOf<GameAction>()
        if (matching.size >= 2) actions += GameAction.Pon(incomingTile.id, matching.take(2).map { it.id })
        val numeric = incomingTile.tile as? Tile.Numeric
        if (sourceDirection == RelativeDirection.Left && numeric != null) {
            CHI_OFFSETS.forEach { (first, second) ->
                if (numeric.value + first !in 1..9 || numeric.value + second !in 1..9) return@forEach
                val firstId = standing.firstOrNull { it.tile == Tile.Numeric(numeric.suit, numeric.value + first) }?.id
                val secondId = standing.firstOrNull { it.tile == Tile.Numeric(numeric.suit, numeric.value + second) }?.id
                if (firstId != null && secondId != null) actions += GameAction.Chi(incomingTile.id, listOf(firstId, secondId))
            }
        }
        if (matching.size == 3 && sourceDirection != RelativeDirection.Left) {
            actions += GameAction.Kan(GameAction.KanType.OPEN_KAN, incomingTile.id, matching.map { it.id })
        }
        return actions
    }

    /** 吃牌時手中兩張牌相對於來源牌的點數差。 */
    private companion object {
        /** 低兩張、夾中間與高兩張的組合。 */
        val CHI_OFFSETS: List<Pair<Int, Int>> = listOf(-2 to -1, -1 to 1, 1 to 2)
    }
}
