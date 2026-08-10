package com.doublemoon1119.mahjongcraft.flow.persistence.dto.game

import com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule.buildExhaustiveDrawReasonPersistenceRegistry
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.table.PendingChankanReaction
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證動作與反應視窗的 encoded persistence round-trip。 */
class PendingReactionPersistenceTest {
    /** persistence 測試使用的 JSON 編解碼器。 */
    private val json = Json

    /** 內建流局原因的 persistence registry。 */
    private val exhaustiveDrawReasonRegistry = buildExhaustiveDrawReasonPersistenceRegistry()

    /** 驗證所有 [GameAction] 變體都能完整還原。 */
    @Test
    fun `all game actions round-trip through encoded persistence DTOs`() {
        val tileId = Uuid.random()
        val withTileIds = listOf(Uuid.random(), Uuid.random(), Uuid.random())
        val actions = listOf(
            GameAction.GameStarted,
            GameAction.RoundStarted,
            GameAction.Draw,
            GameAction.Discard(tileId),
            GameAction.Chi(tileId, withTileIds.take(2)),
            GameAction.Pon(tileId),
            GameAction.Kan(GameAction.KanType.ADDED_KAN, tileId, withTileIds),
            GameAction.Ron(tileId),
            GameAction.Tsumo,
            GameAction.Riichi,
            GameAction.Pass,
            GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.SuukanNagare),
        )
        val serializer = ListSerializer(GameActionPersistenceDto.serializer())

        val encoded = json.encodeToString(
            serializer,
            actions.map { it.toPersistenceDto(exhaustiveDrawReasonRegistry, json) },
        )
        val restored = json.decodeFromString(serializer, encoded).map {
            it.toDomain(exhaustiveDrawReasonRegistry, json)
        }

        assertEquals(actions, restored)
    }

    /** 驗證一般捨牌反應視窗保留資格玩家與既有回應。 */
    @Test
    fun `pending discard reaction round-trips with submitted responses`() {
        val discarderId = Uuid.random()
        val responderA = Uuid.random()
        val responderB = Uuid.random()
        val tileId = Uuid.random()
        val reaction = PendingReaction(
            discarderId = discarderId,
            tileId = tileId,
            eligiblePlayerIds = setOf(responderA, responderB),
            responses = mapOf(responderA to GameAction.Pon(tileId)),
        )

        val encoded = json.encodeToString(
            PendingReactionPersistenceDto.serializer(),
            reaction.toPersistenceDto(exhaustiveDrawReasonRegistry, json),
        )
        val restored = json.decodeFromString(PendingReactionPersistenceDto.serializer(), encoded)
            .toDomain(exhaustiveDrawReasonRegistry, json)

        assertEquals(reaction, restored)
    }

    /** 驗證搶槓反應視窗保留槓動作、被搶牌與既有回應。 */
    @Test
    fun `pending chankan reaction round-trips with robbed tile`() {
        val declarerId = Uuid.random()
        val responderA = Uuid.random()
        val responderB = Uuid.random()
        val robbedTile = IdentifiedTile(Uuid.random(), Tile.Numeric(Tile.Suit.Dot, 5, isRed = true))
        val kanAction = GameAction.Kan(
            type = GameAction.KanType.CLOSED_KAN,
            tileId = robbedTile.id,
            withTiles = List(3) { Uuid.random() },
        )
        val reaction = PendingChankanReaction(
            declarerId = declarerId,
            kanAction = kanAction,
            robbedTile = robbedTile,
            eligiblePlayerIds = setOf(responderA, responderB),
            responses = mapOf(responderA to GameAction.Pass),
        )

        val encoded = json.encodeToString(
            PendingChankanReactionPersistenceDto.serializer(),
            reaction.toPersistenceDto(exhaustiveDrawReasonRegistry, json),
        )
        val restored = json.decodeFromString(PendingChankanReactionPersistenceDto.serializer(), encoded)
            .toDomain(exhaustiveDrawReasonRegistry, json)

        assertEquals(reaction, restored)
    }
}
