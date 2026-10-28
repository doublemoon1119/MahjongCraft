package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileOrder
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueCalculator
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueContextCalculator
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenCalculator
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.module.ExhaustiveDrawSettlementResult
import com.doublemoon1119.mahjongcraft.logic.module.HandTileDescription
import com.doublemoon1119.mahjongcraft.logic.module.HandTilePose
import com.doublemoon1119.mahjongcraft.logic.module.HandTileVisibility
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.PublicPlayerIndicator
import com.doublemoon1119.mahjongcraft.logic.module.RevealedHandSettlement
import com.doublemoon1119.mahjongcraft.logic.module.WinResolutionResult
import com.doublemoon1119.mahjongcraft.logic.module.WinSettlementResult
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PlayerRuleState
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayout
import com.doublemoon1119.mahjongcraft.logic.table.opening.RoundOpeningProcedure
import com.doublemoon1119.mahjongcraft.logic.tile.TileInterpretationPolicy
import kotlin.random.Random
import kotlin.uuid.Uuid

/**
 * 簡易配對麻將規則模組，供範例擴充展示基本規則接點。
 *
 * @property id 此規則模組的註冊識別碼。
 * @property config 此規則使用的設定。
 */
class ExamplePairingRuleModule(
    override val id: String,
    override val config: ExamplePairingRuleConfig,
) : MahjongRuleModule<ExamplePairingRuleConfig> {
    override val tileOrder: TileOrder = ExamplePairingTileOrder

    override fun createWallFactory(playerCount: Int): ExamplePairingWallFactory = ExamplePairingWallFactory(playerCount, config.wallTileValues)

    override fun createRoundOpeningProcedure(playerCount: Int): RoundOpeningProcedure? = ExamplePairingRoundOpeningProcedure(config.wallTileValues.size)

    override fun createWallLayout(playerCount: Int): TileWallLayout? = ExamplePairingWallLayout(playerCount, config.wallTileValues.size)

    override fun createTileInterpretationPolicy(): TileInterpretationPolicy = ExamplePairingTileInterpretationPolicy

    override fun createDiscardPile(): DiscardPile<*> = ExamplePairingDiscardPile()

    override fun createShantenCalculator(): ShantenCalculator = ExamplePairingShantenCalculator()

    override fun createLegalActionValidator(): LegalActionValidator = ExamplePairingLegalActionValidator()

    override fun createHandValueCalculator(): HandValueCalculator<*, *> = ExamplePairingHandValueCalculator()

    override fun createHandValueContextCalculator(): HandValueContextCalculator<*, *> = ExamplePairingHandValueContextCalculator()

    override fun createInitialDynamicState(): DynamicRuleState = createBonusState()

    override fun createNextRoundDynamicState(previous: DynamicRuleState?): DynamicRuleState = createBonusState()

    override fun createInitialPlayerRuleState(): PlayerRuleState = ExamplePairingPlayerState()

    override fun projectPlayerRuleState(tableState: TableState, player: MahjongPlayer, observerId: Uuid): PlayerRuleState? {
        val state = player.playerRuleState as? ExamplePairingPlayerState ?: return null
        return state.takeIf { player.id == observerId || it.handRevealed }
    }

    override fun describeHandTiles(tableState: TableState): List<HandTileDescription> = tableState.players
        .filter { (it.playerRuleState as? ExamplePairingPlayerState)?.handRevealed == true }
        .flatMap { player -> player.hand.standingTiles }
        .map { tile ->
            HandTileDescription(
                tileId = tile.id,
                visibility = HandTileVisibility.Public,
                pose = HandTilePose.FACE_UP,
            )
        }

    override fun projectDynamicRuleState(tableState: TableState, observerId: Uuid): DynamicRuleState? = (tableState.dynamicRuleState as? ExamplePairingDynamicState)?.let { it.copy(bonusTileValues = it.bonusTileValues.toSet()) }

    override fun getPublicPlayerIndicators(tableState: TableState, player: MahjongPlayer): List<PublicPlayerIndicator> = if ((player.playerRuleState as? ExamplePairingPlayerState)?.handRevealed == true) {
        listOf(PublicPlayerIndicator(ExamplePairingIds.HAND_REVEALED_INDICATOR))
    } else {
        emptyList()
    }

    override fun onPlayerDrew(player: MahjongPlayer): MahjongPlayer = player

    override fun onPlayerDiscarded(player: MahjongPlayer): MahjongPlayer = player

    override fun onMeldClaimed(players: List<MahjongPlayer>): List<MahjongPlayer> = players

    override fun beforeDiscardClaimed(
        claimingPlayer: MahjongPlayer,
        meldType: MeldType,
        calledTile: IdentifiedTile,
        sourceDirection: RelativeDirection,
    ): MahjongPlayer = claimingPlayer

    override fun declareTsumo(tableState: TableState, player: MahjongPlayer): WinResolutionResult? {
        if (player.hand.lastDrawn == null) return null
        val fullHand = player.hand
        if (ExamplePairingShantenCalculator().calculate(fullHand) !is ShantenResult.Complete) return null
        val bonusTileValues = requireNotNull(tableState.dynamicRuleState as? ExamplePairingDynamicState) {
            "Example pairing dynamic state is required for settlement"
        }.bonusTileValues
        val bonusTileCount = bonusTileValues.count { value -> player.hand.standingTiles.any { it.tile.examplePairingValue() == value } }
        val payment = config.paymentPerPlayer + bonusTileCount
        val payments = tableState.players.filter { it.id != player.id }.associate { it.id to payment }
        return WinResolutionResult(
            settlement = WinSettlementResult(payment * payments.size, payments),
            handValueResult = ExamplePairingHandValueResult(bonusTileCount),
        )
    }

    /** 依本局設定隨機選出比牌山牌種少一種的固定加分牌集合。 */
    private fun createBonusState(): ExamplePairingDynamicState = ExamplePairingDynamicState(
        bonusTileValues = config.wallTileValues.shuffled(Random.Default).take(config.wallTileValues.size - 1).toSet(),
    )

    /** 將範例牌面轉成配對規則使用的萬子點數；倒置一萬也視為一萬。 */
    private fun Tile.examplePairingValue(): Int? = when (this) {
        is Tile.Numeric -> value
        is Tile.Extension -> if (typeId == ExamplePairingIds.INVERTED_ONE_MAN) 1 else null
        is Tile.Honor -> null
    }

    override fun declareRon(
        tableState: TableState,
        player: MahjongPlayer,
        winningTile: IdentifiedTile,
        discarderId: Uuid,
        isRobbingKan: Boolean,
    ): WinResolutionResult? = null

    override fun resolveMultiRonAbortiveDraw(): ExhaustiveDrawReason? = null

    override fun collectStickPot(tableState: TableState): Pair<DynamicRuleState?, Int>? = null

    override fun declareExhaustiveDraw(tableState: TableState): ExhaustiveDrawSettlementResult? {
        if (tableState.tileWall.remainingCount != 0) return null
        val revealedHands = tableState.players.mapNotNull { player ->
            val result = ExamplePairingShantenCalculator().calculate(player.hand)
            (result as? ShantenResult.Tenpai)?.let {
                RevealedHandSettlement(player.id, it.winningTiles.toSet())
            }
        }
        return ExhaustiveDrawSettlementResult(
            reason = ExamplePairingExhaustiveDrawReason,
            tenpaiPlayerIds = revealedHands.mapTo(mutableSetOf()) { it.playerId },
            revealedHands = revealedHands,
            scoreDeltas = emptyMap(),
            stickPotCollectorPlayerIds = emptySet(),
        )
    }
}
