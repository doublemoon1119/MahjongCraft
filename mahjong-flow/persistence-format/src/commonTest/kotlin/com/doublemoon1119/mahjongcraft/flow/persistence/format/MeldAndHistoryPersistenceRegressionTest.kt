package com.doublemoon1119.mahjongcraft.flow.persistence.format

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTableChange
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTableResult
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.MahjongPlayerPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryRecordingPersistenceMapper
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

/** 驗證副露可見性、牌張順序與鳴牌後待捨牌狀態的持久化及歷史重建。 */
class MeldAndHistoryPersistenceRegressionTest {
    /** 內建 persistence registry。 */
    private val registries: PersistenceRegistries = bundledPersistenceRegistries()

    /** 副露完整 round-trip 必須保留牌張順序及可見性與牌面朝向。 */
    @Test
    fun `meld persistence round trip preserves tile order visibility and pose`() {
        val tiles = listOf(
            identified(Tile.Numeric(Tile.Suit.Character, 3)),
            identified(Tile.Numeric(Tile.Suit.Character, 1)),
            identified(Tile.Numeric(Tile.Suit.Character, 2)),
        )
        val meld = Meld(
            type = MeldType.CHI,
            tiles = tiles,
            sourceTile = tiles[2],
            sourceDirection = RelativeDirection.Left,
            publiclyRevealedTileIds = setOf(tiles[0].id, tiles[2].id),
            faceDownTileIds = setOf(tiles[1].id),
        )
        val player = MahjongPlayer(
            id = Uuid.random(),
            initialSeatIndex = Wind.EAST.ordinal,
            hand = Hand(melds = listOf(meld)),
            discardPile = TaiwanDiscardPile(),
            seatWind = Wind.EAST,
            mustDiscardAfterMeld = true,
        )

        val encoded = Json.encodeToString(
            MahjongPlayerPersistenceDto.serializer(),
            player.toPersistenceDto(
                registries.discardPiles,
                registries.playerRuleStates,
                registries.exhaustiveDrawReasons,
                registries.extensionGameActions,
            ),
        )
        val restored = Json.decodeFromString(MahjongPlayerPersistenceDto.serializer(), encoded).toDomain(
            registries.discardPiles,
            registries.playerRuleStates,
            registries.exhaustiveDrawReasons,
            registries.extensionGameActions,
        )

        assertEquals(player, restored)
        assertEquals(tiles, restored.hand.melds.single().tiles)
        assertEquals(setOf(tiles[0].id, tiles[2].id), restored.hand.melds.single().publiclyRevealedTileIds)
        assertEquals(setOf(tiles[1].id), restored.hand.melds.single().faceDownTileIds)
        assertEquals(true, restored.mustDiscardAfterMeld)
    }

    /** 只有副露可見性或牌面朝向改變時，歷史差異仍須可序列化並重建桌況。 */
    @Test
    fun `visibility-only and pose-only meld changes reconstruct through history table change`() {
        val tiles = List(3) { index -> identified(Tile.Numeric(Tile.Suit.Dot, index + 1)) }
        val baseMeld = Meld(
            type = MeldType.PON,
            tiles = tiles,
            sourceTile = tiles.last(),
            sourceDirection = RelativeDirection.Across,
            publiclyRevealedTileIds = setOf(tiles.last().id),
            faceDownTileIds = emptySet(),
        )
        val before = tableState(Hand(melds = listOf(baseMeld)))
        listOf(
            "visibility" to (tiles.map { it.id }.toSet() to emptySet<Uuid>()),
            "pose" to (setOf(tiles.last().id) to setOf(tiles.first().id)),
        ).forEach { (_, meldPresentation) ->
            val (publicIds, faceDownIds) = meldPresentation
            val after = before.copy(
                players = listOf(
                    before.players.single().copy(
                        hand = Hand(
                            melds = listOf(
                                baseMeld.copy(
                                    publiclyRevealedTileIds = publicIds,
                                    faceDownTileIds = faceDownIds,
                                ),
                            ),
                        ),
                    ),
                ),
            )
            val change = assertNotNull(HistoryTableChange.between(before, after))
            val mapper = HistoryRecordingPersistenceMapper(registries)
            val event = HistoryOutboxEvent(
                matchId = Uuid.random(),
                venueId = before.id,
                roundNumber = 1,
                sequence = 1L,
                occurredAtEpochMillis = 1L,
                actorPlayerId = before.players.single().id,
                fact = HistoryFact.TableChanged(HistoryTableResult.Change(change)),
            )
            val encoded = mapper.encodePendingEvent(event)
            val decoded = mapper.decodePendingEvent(encoded)
            val decodedChange = (decoded.fact as HistoryFact.TableChanged).result as HistoryTableResult.Change

            assertEquals(after, decodedChange.change.applyTo(before))
            assertEquals(after.players.single().hand, decodedChange.change.changedPlayers.single().playerWithoutHistory.hand)
        }
    }

    /** 建立含單一玩家副露的最小桌況。 */
    private fun tableState(hand: Hand): TableState {
        val player = MahjongPlayer(
            id = Uuid.random(),
            initialSeatIndex = Wind.EAST.ordinal,
            hand = hand,
            discardPile = TaiwanDiscardPile(),
            seatWind = Wind.EAST,
        )
        return TableState(
            id = Uuid.random(),
            players = listOf(player),
            config = TaiwanRuleConfig(),
            tileWall = TileWall(emptyList()),
            dealerPlayerId = player.id,
        )
    }

    /** 建立具唯一識別碼的測試牌。 */
    private fun identified(tile: Tile): IdentifiedTile = IdentifiedTile(Uuid.random(), tile)
}
