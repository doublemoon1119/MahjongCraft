package com.doublemoon1119.mahjongcraft.flow.persistence.dto.game
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule.buildDiscardPilePersistenceRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule.buildExhaustiveDrawReasonPersistenceRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule.buildPlayerRuleStatePersistenceRegistry
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoLiability
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoYaku
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardEntry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證玩家完整權威狀態的 encoded persistence round-trip。 */
class MahjongPlayerPersistenceTest {
    /** persistence 測試使用的 JSON 編解碼器。 */
    private val json = Json

    /** 內建牌河的 persistence registry。 */
    private val discardPileRegistry = buildDiscardPilePersistenceRegistry()

    /** 內建玩家規則狀態的 persistence registry。 */
    private val playerRuleStateRegistry = buildPlayerRuleStatePersistenceRegistry()

    /** 內建流局原因的 persistence registry。 */
    private val exhaustiveDrawReasonRegistry = buildExhaustiveDrawReasonPersistenceRegistry()

    /** 驗證日麻玩家的隱藏狀態、牌河與動作歷史皆能完整還原。 */
    @Test
    fun `riichi player round-trips with all authoritative state`() {
        val standingTile = identified(RiichiTileTypes.redFive(Tile.Suit.Character))
        val lastDrawn = identified(Tile.Honor.Red)
        val riichiTile = identified(Tile.Numeric(Tile.Suit.Dot, 3))
        val takenDiscard = identified(Tile.Numeric(Tile.Suit.Bamboo, 7))
        val discardPile = RiichiDiscardPile()
            .discard(RiichiDiscardEntry(riichiTile, isRiichi = true))
            .discard(RiichiDiscardEntry(takenDiscard, isTaken = true))
        val player = MahjongPlayer(
            id = Uuid.random(),
            initialSeat = Wind.WEST,
            hand = Hand(tiles = listOf(standingTile), lastDrawn = lastDrawn),
            discardPile = discardPile,
            playerRuleState = RiichiPlayerState(
                riichiTile = riichiTile,
                isIppatsu = true,
                paoLiability = PaoLiability(PaoYaku.Daisangen, RelativeDirection.Left),
            ),
            score = 31_200,
            aiStrategyKey = "random",
            currentWind = Wind.SOUTH,
            passedTilesInRound = setOf(Tile.Honor.White, Tile.Numeric(Tile.Suit.Dot, 5)),
            actionHistory = listOf(
                GameAction.Riichi,
                GameAction.Discard(riichiTile.id),
                GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.Normal),
            ),
        )

        val restored = encodedRoundTrip(player)

        assertPlayerCommonState(player, restored)
        assertEquals(player.playerRuleState, restored.playerRuleState)
        val restoredEntries = assertIs<RiichiDiscardPile>(restored.discardPile).entries
        assertEquals(listOf(riichiTile, takenDiscard), restoredEntries.map { it.tile })
        assertEquals(listOf(true, false), restoredEntries.map { it.isRiichi })
        assertEquals(listOf(false, true), restoredEntries.map { it.isTaken })
    }

    /** 驗證沒有玩家規則狀態的台麻玩家仍能完整還原。 */
    @Test
    fun `taiwan player round-trips without player rule state`() {
        val discard = identified(Tile.Extension(TaiwanTileTypes.PLUM))
        val player = MahjongPlayer(
            id = Uuid.random(),
            initialSeat = Wind.EAST,
            discardPile = TaiwanDiscardPile().discard(DiscardPile.DiscardEntry(discard, isTaken = true)),
            score = 16,
        )

        val restored = encodedRoundTrip(player)

        assertPlayerCommonState(player, restored)
        assertNull(restored.playerRuleState)
        val restoredEntry = assertIs<TaiwanDiscardPile>(restored.discardPile).entries.single()
        assertEquals(discard, restoredEntry.tile)
        assertEquals(true, restoredEntry.isTaken)
    }

    /** 將玩家編碼成 JSON 後解碼並還原成 domain。 */
    private fun encodedRoundTrip(player: MahjongPlayer): MahjongPlayer {
        val encoded = json.encodeToString(
            MahjongPlayerPersistenceDto.serializer(),
            player.toPersistenceDto(
                discardPileRegistry,
                playerRuleStateRegistry,
                exhaustiveDrawReasonRegistry,
                json,
            ),
        )
        return json.decodeFromString(MahjongPlayerPersistenceDto.serializer(), encoded).toDomain(
            discardPileRegistry,
            playerRuleStateRegistry,
            exhaustiveDrawReasonRegistry,
            json,
        )
    }

    /** 比對不依賴牌河具體實作的玩家權威狀態。 */
    private fun assertPlayerCommonState(expected: MahjongPlayer, actual: MahjongPlayer) {
        assertEquals(expected.id, actual.id)
        assertEquals(expected.initialSeat, actual.initialSeat)
        assertEquals(expected.hand, actual.hand)
        assertEquals(expected.score, actual.score)
        assertEquals(expected.aiStrategyKey, actual.aiStrategyKey)
        assertEquals(expected.currentWind, actual.currentWind)
        assertEquals(expected.passedTilesInRound, actual.passedTilesInRound)
        assertEquals(expected.actionHistory, actual.actionHistory)
    }

    /** 建立具有隨機穩定識別碼的測試牌。 */
    private fun identified(tile: Tile): IdentifiedTile = IdentifiedTile(Uuid.random(), tile)
}
