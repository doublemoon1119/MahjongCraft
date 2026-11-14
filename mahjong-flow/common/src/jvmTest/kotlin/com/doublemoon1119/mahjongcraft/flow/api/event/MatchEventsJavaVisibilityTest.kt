package com.doublemoon1119.mahjongcraft.flow.api.event

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Java 寫的第三方無法建立事件與其中的資料（沒有公開的建構子，內部建立入口對 Java 不可見），也無法修改事件中的集合。 */
class MatchEventsJavaVisibilityTest {
    private val types = listOf(
        MatchStartedEvent::class.java,
        RoundSettledEvent::class.java,
        MatchEndedEvent::class.java,
        MatchPlayer::class.java,
        RoundScoreChange::class.java,
        MatchStanding::class.java,
        EventQuantity::class.java,
        PublicEventMarker::class.java,
        EventRoundContext::class.java,
        PublicTileFace::class.java,
        PerformedAction::class.java,
        ActionsPerformedEvent::class.java,
        RoundCompletedEvent::class.java,
        WinDetailField::class.java,
        RoundWinDetail::class.java,
        RoundWinGroup::class.java,
        CommittedMatchEventBatch::class.java,
        RuleResultCompletion::class.java,
        RuleResultDetail::class.java,
        RuleResultsResolvedEvent::class.java,
    )

    /** 每個型別都沒有 Java 看得到的建構子；編譯器產生的存取用建構子是 synthetic，Java 原始碼無法呼叫。 */
    @Test
    fun `no constructor is callable from Java`() {
        types.forEach { type ->
            val visible = type.declaredConstructors.filter { !it.isSynthetic && !Modifier.isPrivate(it.modifiers) }
            assertEquals(emptyList(), visible.map { it.toGenericString() }, "${type.simpleName} must not expose a constructor")
        }
    }

    /** 內部建立入口都是 synthetic，Java 原始碼無法呼叫。 */
    @Test
    fun `factories are hidden from Java`() {
        types.forEach { type ->
            val companion = type.declaredClasses.single { it.simpleName == "Companion" }
            val factories = companion.declaredMethods.filter { it.name.startsWith("create") }
            assertTrue(factories.isNotEmpty(), "${type.simpleName} must have a factory")
            assertTrue(factories.all { it.isSynthetic }, "${type.simpleName} factories must be synthetic")
        }
    }

    /** Java 直接呼叫集合與迭代器的修改方法時一律被拒絕。 */
    @Test
    fun `collections reject Java mutators`() {
        val playerId = Uuid.random()
        val event = RoundSettledEvent.create(
            eventId = Uuid.random(),
            matchId = Uuid.random(),
            venueId = Uuid.random(),
            ruleModuleId = "test:rule",
            roundNumber = 1,
            outcomeId = "test:outcome",
            kind = RoundSettlementKind.WIN,
            beneficiaryPlayerIds = mutableSetOf(playerId),
            responsiblePlayerIds = mutableSetOf(),
            players = mutableListOf(RoundScoreChange.create(playerId, 25_000, 33_000, 1, 1)),
        )

        assertRejected { javaCall(event.beneficiaryPlayerIds, "clear") }
        assertRejected { javaCall(event.responsiblePlayerIds, "add", Any::class.java, playerId) }
        assertRejected { javaCall(event.players, "clear") }
        assertRejected {
            val iterator = checkNotNull(javaCall(event.beneficiaryPlayerIds, "iterator"))
            javaCall(iterator, "next")
            javaCall(iterator, "remove")
        }
        assertEquals(setOf(playerId), event.beneficiaryPlayerIds)
    }

    /** 新增事件資料的集合也拒絕 Java 直接修改。 */
    @Test
    fun `new event collections reject Java mutators`() {
        val playerId = Uuid.random()
        val face = PublicTileFace.create("test:one")
        val action = PerformedAction.create("test:discard", playerId, PerformedActionKind.DISCARD, listOf(face))
        val actionEvent = ActionsPerformedEvent.create(
            eventId = Uuid.random(),
            matchId = Uuid.random(),
            venueId = Uuid.random(),
            ruleModuleId = "test:rule",
            roundNumber = 1,
            actions = listOf(action),
        )
        val quantity = EventQuantity.create("test:points", 1)
        val marker = PublicEventMarker.create("test:marker", playerId, quantity)
        val field = WinDetailField.create("test:field", listOf(marker), listOf(quantity))
        val detail = RoundWinDetail.create(playerId, WinMethod.DISCARD, listOf(field), listOf(marker))
        val group = RoundWinGroup.create("test:win", listOf(detail), setOf(playerId), setOf(playerId))
        val completion = RuleResultCompletion.create("test:outcome", RoundCompletionKind.WIN, setOf(playerId))
        val ruleResult = RuleResultDetail.create("test:result", listOf(detail), completion)
        val ruleResults = RuleResultsResolvedEvent.create(
            eventId = Uuid.random(),
            matchId = Uuid.random(),
            venueId = Uuid.random(),
            ruleModuleId = "test:rule",
            results = listOf(ruleResult),
        )
        val ended = MatchEndedEvent.create(
            eventId = Uuid.random(),
            matchId = Uuid.random(),
            venueId = Uuid.random(),
            ruleModuleId = "test:rule",
            completion = MatchCompletion.COMPLETED,
            reasonId = "test:completed",
            standings = listOf(MatchStanding.create(playerId, false, 25_000, 1)),
            completedScores = mutableMapOf(playerId to 25_000),
        )
        val roundCompleted = RoundCompletedEvent.create(
            eventId = Uuid.random(),
            matchId = Uuid.random(),
            venueId = Uuid.random(),
            ruleModuleId = "test:rule",
            roundContext = EventRoundContext.create(1, "test:east"),
            classification = RoundCompletionKind.WIN,
            outcomeId = "test:win",
            players = listOf(RoundScoreChange.create(playerId, 25_000, 26_000, 1, 1)),
        )
        val batch = CommittedMatchEventBatch.create(
            batchId = Uuid.random(),
            matchId = Uuid.random(),
            venueId = Uuid.random(),
            ruleModuleId = "test:rule",
            players = listOf(MatchPlayer.create(playerId, false, 0)),
            events = listOf(actionEvent, roundCompleted),
        )

        assertRejected { javaCall(action.tiles, "clear") }
        assertRejected { javaCall(actionEvent.actions, "clear") }
        assertRejected { javaCall(field.entries, "clear") }
        assertRejected { javaCall(field.quantities, "clear") }
        assertRejected { javaCall(detail.detailFields, "clear") }
        assertRejected { javaCall(group.winners, "clear") }
        assertRejected { javaCall(group.responsiblePlayerIds, "clear") }
        assertRejected { javaCall(ruleResults.results, "clear") }
        assertRejected { javaCall(ruleResult.winDetails, "clear") }
        assertRejected { javaCall(ended.completedScores, "clear") }
        assertRejected {
            val entries = checkNotNull(javaCall(ended.completedScores, "entrySet")) as Set<*>
            val entry = entries.single()
            javaCall(checkNotNull(entry), "setValue", Any::class.java, 50_000)
        }
        assertRejected { javaCall(roundCompleted.players, "clear") }
        assertRejected { javaCall(batch.players, "clear") }
        assertRejected { javaCall(batch.events, "clear") }
    }

    /** 以 Java 介面的方法反射呼叫，模擬 Java 原始碼的呼叫方式。 */
    private fun javaCall(target: Any, name: String, parameterType: Class<*>? = null, argument: Any? = null): Any? {
        val owner = when (target) {
            is Iterator<*> -> Iterator::class.java
            is Map.Entry<*, *> -> Map.Entry::class.java
            is Map<*, *> -> Map::class.java
            else -> Collection::class.java
        }
        val method = if (parameterType == null) owner.getMethod(name) else owner.getMethod(name, parameterType)
        return try {
            if (parameterType == null) method.invoke(target) else method.invoke(target, argument)
        } catch (wrapped: InvocationTargetException) {
            throw wrapped.targetException
        }
    }

    /** [block] 因不支援修改而失敗。 */
    private fun assertRejected(block: () -> Unit) {
        assertFailsWith<UnsupportedOperationException> { block() }
    }
}
