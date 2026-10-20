package com.doublemoon1119.mahjongcraft.example.minecraft

import com.doublemoon1119.mahjongcraft.example.ExampleMahjongExtension
import com.doublemoon1119.mahjongcraft.example.action.ExamplePairingDeclarationAction
import com.doublemoon1119.mahjongcraft.example.minecraft.text.ExampleMinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleModule
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryActionResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundEvents
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryRecordingPersistenceMapper
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.CompactReplayCodec
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.CompactReplayRoundReader
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.ReplayReadResult
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.emptyPersistenceRegistries
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollSource
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistryImpl
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

/** 確認範例宣告經精簡牌譜讀取後仍能使用平台用語顯示。 */
class ExamplePairingHistoryPresentationTest {
    /** 保存並讀取範例宣告後，牌譜動作應顯示為「宣告」而非未知操作。 */
    @Test
    fun `compact history preserves declaration action vocabulary`() = runTest {
        val matchId = Uuid.random()
        val venueId = Uuid.random()
        val playerIds = List(2) { Uuid.random() }
        val table = GameInitializer.initialize(
            id = Uuid.random(),
            playerIds = playerIds,
            module = ExamplePairingRuleModule(ExamplePairingIds.RULE_MODULE, ExamplePairingRuleConfig()),
            diceRollSource = DiceRollSource { DiceRollResult.of(List(it) { 1 }) },
        ).tableState
        val actor = table.currentPlayer.id
        val events = listOf(
            HistoryOutboxEvent(
                matchId = matchId,
                venueId = venueId,
                roundNumber = 1,
                sequence = 1,
                transactionFirstSequence = 1,
                occurredAtEpochMillis = 100,
                actorPlayerId = null,
                fact = HistoryFact.MatchStarted(table, GameFlowConfig(), emptyMap()),
            ),
            HistoryOutboxEvent(
                matchId = matchId,
                venueId = venueId,
                roundNumber = 1,
                sequence = 2,
                transactionFirstSequence = 2,
                occurredAtEpochMillis = 200,
                actorPlayerId = actor,
                fact = HistoryFact.ActionAccepted(
                    action = GameAction.Extension(ExamplePairingDeclarationAction),
                    result = HistoryActionResult(
                        affectedTileIds = emptyList(),
                        newlyRevealedTileIds = emptyList(),
                        remainingWallTileCount = table.tileWall.remainingCount,
                        reservedWallTileIds = table.reservedWallTiles.map { it.id },
                        scoresByPlayerId = table.players.associate { it.id to it.score },
                        nextPlayerId = actor,
                    ),
                ),
            ),
            HistoryOutboxEvent(
                matchId = matchId,
                venueId = venueId,
                roundNumber = 1,
                sequence = 3,
                transactionFirstSequence = 3,
                occurredAtEpochMillis = 300,
                actorPlayerId = null,
                fact = HistoryFact.MatchCompleted(
                    reasonId = "mahjongcraft_example:completed",
                    finalScoresByPlayerId = table.players.associate { it.id to it.score },
                ),
            ),
        )
        val registries = emptyPersistenceRegistries().also { registry ->
            ExampleMahjongExtension().registerPersistenceDtos(registry)
            registry.freeze()
        }
        val document = CompactReplayCodec.encodeCompact(
            events = events,
            mapper = HistoryRecordingPersistenceMapper(registries),
            registries = registries,
            setAsideTiles = { emptyList() },
        )

        val read = CompactReplayRoundReader().readEvents(
            document = document,
            expectedMatchId = matchId,
            roundNumber = 1,
            startIndex = 0,
            limit = 20,
        )
        val replay = assertIs<ReplayReadResult.Success<HistoryRoundEvents>>(read).value
        val action = replay.transactions
            .flatMap { transaction -> transaction.facts }
            .filterIsInstance<HistoryReplayFact.KnownAction>()
            .single()
        val actionId = assertNotNull(action.extensionTypeId)
        assertEquals(ExamplePairingIds.DECLARE_ACTION, actionId)

        val vocabulary = GameActionVocabularyRegistryImpl().apply {
            ExampleMinecraftMahjongExtension().registerGameActionVocabulary(this)
        }
        val definition = vocabulary.find(ExamplePairingIds.RULE_MODULE, actionId)
        assertEquals(ExampleMinecraftMessageKeys.DECLARE, assertNotNull(definition).labelKey)
    }
}
