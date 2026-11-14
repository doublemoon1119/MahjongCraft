package com.doublemoon1119.mahjongcraft.flow.api.event

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 新增事件資料會複製來源集合，且不接受不完整的公開 ID。 */
class MatchEventSnapshotTest {
    private val playerId = Uuid.random()
    private val eventId = Uuid.random()
    private val matchId = Uuid.random()
    private val venueId = Uuid.random()

    /** 動作、牌面和補牌資訊會在建立時固定，來源清單之後的修改不會滲入事件。 */
    @Test
    fun `performed action snapshots tiles`() {
        val faces = mutableListOf(PublicTileFace.create("test:one"))
        val action = PerformedAction.create(
            actionId = "test:discard",
            actorPlayerId = playerId,
            kind = PerformedActionKind.DISCARD,
            tiles = faces,
            supplementalTileCount = 0,
        )

        faces.clear()

        assertEquals(1, action.tiles.size)
        assertEquals("test:one", action.tiles.single()?.typeId)
    }

    /** 各層事件的清單與集合都會複製，不能因為呼叫端修改來源而改變已建立事件。 */
    @Test
    fun `all event collection properties are snapshots`() {
        val action = PerformedAction.create(
            actionId = "test:discard",
            actorPlayerId = playerId,
            kind = PerformedActionKind.DISCARD,
            tiles = listOf(PublicTileFace.create("test:one")),
        )
        val actions = mutableListOf(action)
        val actionEvent = ActionsPerformedEvent.create(eventId, matchId, venueId, "test:rule", 1, actions)

        val fieldEntries = mutableListOf(PublicEventMarker.create("test:marker", playerId, null))
        val fieldQuantities = mutableListOf(EventQuantity.create("test:points", 10))
        val fields = mutableListOf(WinDetailField.create("test:field", fieldEntries, fieldQuantities))
        val markers = mutableListOf(PublicEventMarker.create("test:riichi", playerId, null))
        val winDetail = RoundWinDetail.create(playerId, WinMethod.DISCARD, fields, markers)
        val winners = mutableListOf(winDetail)
        val responsible = linkedSetOf(playerId)
        val discarders = linkedSetOf(playerId)
        val group = RoundWinGroup.create("test:win", winners, responsible, discarders)
        val groups = mutableListOf(group)
        val scoreChanges = mutableListOf(RoundScoreChange.create(playerId, 25_000, 26_000, 1, 1))
        val roundCompleted = RoundCompletedEvent.create(
            eventId = eventId,
            matchId = matchId,
            venueId = venueId,
            ruleModuleId = "test:rule",
            roundContext = EventRoundContext.create(1, "test:east"),
            classification = RoundCompletionKind.WIN,
            outcomeId = "test:win",
            players = scoreChanges,
        )
        val settled = RoundSettledEvent.create(
            eventId = eventId,
            matchId = matchId,
            venueId = venueId,
            ruleModuleId = "test:rule",
            roundNumber = 1,
            outcomeId = "test:win",
            kind = RoundSettlementKind.WIN,
            beneficiaryPlayerIds = responsible,
            responsiblePlayerIds = discarders,
            players = scoreChanges,
            winGroups = groups,
        )
        val standings = mutableListOf(MatchStanding.create(playerId, false, 26_000, 1))
        val publicMarkers = mutableListOf(PublicEventMarker.create("test:bust", null, null))
        val completedPlayers = linkedSetOf(playerId)
        val ended = MatchEndedEvent.create(
            eventId = eventId,
            matchId = matchId,
            venueId = venueId,
            ruleModuleId = "test:rule",
            completion = MatchCompletion.COMPLETED,
            reasonId = "test:completed",
            standings = standings,
            roundContext = EventRoundContext.create(1, "test:east"),
            publicMarkers = publicMarkers,
            completedPlayerIds = completedPlayers,
        )
        val players = mutableListOf(MatchPlayer.create(playerId, false, 0))
        val events = mutableListOf<MatchEvent>(actionEvent, settled, roundCompleted, ended)
        val batch = CommittedMatchEventBatch.create(
            batchId = Uuid.random(),
            matchId = matchId,
            venueId = venueId,
            ruleModuleId = "test:rule",
            players = players,
            events = events,
        )

        actions.clear()
        fieldEntries.clear()
        fieldQuantities.clear()
        fields.clear()
        markers.clear()
        winners.clear()
        responsible.clear()
        discarders.clear()
        groups.clear()
        scoreChanges.clear()
        standings.clear()
        publicMarkers.clear()
        completedPlayers.clear()
        players.clear()
        events.clear()

        assertEquals(1, actionEvent.actions.size)
        assertEquals(1, actionEvent.actions.single().tiles.size)
        assertEquals(1, settled.winGroups.size)
        assertEquals(setOf(playerId), settled.winGroups.single().responsiblePlayerIds)
        assertEquals(1, settled.players.size)
        assertEquals(setOf(playerId), settled.responsiblePlayerIds)
        assertEquals(1, roundCompleted.players.size)
        assertEquals(1, ended.standings.size)
        assertEquals(1, ended.publicMarkers.size)
        assertEquals(setOf(playerId), ended.completedPlayerIds)
        assertEquals(1, group.winners.single().detailFields.size)
        assertEquals(1, group.winners.single().publicMarkers.size)
        assertEquals(1, group.winners.single().detailFields.single().entries.size)
        assertEquals(1, group.winners.single().detailFields.single().quantities.size)
        assertEquals(4, batch.events.size)
        assertEquals(1, batch.players.size)
    }

    /** 巢狀公開資料也保持自己的不可變快照，並保留隱藏牌的 null。 */
    @Test
    fun `nested public data keeps grouping and concealed faces`() {
        val quantity = EventQuantity.create("test:han", 3)
        val marker = PublicEventMarker.create("test:riichi", playerId, quantity)
        val field = WinDetailField.create("test:yaku", listOf(marker), listOf(quantity))
        val detail = RoundWinDetail.create(playerId, WinMethod.UNKNOWN, listOf(field), listOf(marker))
        val group = RoundWinGroup.create("test:win", listOf(detail), emptySet(), emptySet())
        val action = PerformedAction.create(
            actionId = "test:discard",
            actorPlayerId = playerId,
            kind = PerformedActionKind.DISCARD,
            tiles = listOf(null, PublicTileFace.create("test:one")),
        )

        assertEquals(3, group.winners.single().detailFields.single().quantities.single().amount)
        assertEquals(3, group.winners.single().publicMarkers.single().quantity?.amount)
        assertNull(action.tiles.first())
        assertEquals("test:one", action.tiles[1]?.typeId)
    }

    /** 公開資料的所有 ID 都必須使用完整 namespaced 格式。 */
    @Test
    fun `public ids must be namespaced`() {
        assertFailsWith<IllegalArgumentException> { EventQuantity.create("points", 1) }
        assertFailsWith<IllegalArgumentException> { PublicEventMarker.create("marker", null, null) }
        assertFailsWith<IllegalArgumentException> { EventRoundContext.create(1, "east") }
        assertFailsWith<IllegalArgumentException> { PublicTileFace.create("one") }
        assertFailsWith<IllegalArgumentException> {
            PerformedAction.create("discard", playerId, PerformedActionKind.DISCARD)
        }
        assertFailsWith<IllegalArgumentException> {
            WinDetailField.create("field", emptyList(), emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            RoundWinGroup.create("win", emptyList(), emptySet(), emptySet())
        }
    }
}
