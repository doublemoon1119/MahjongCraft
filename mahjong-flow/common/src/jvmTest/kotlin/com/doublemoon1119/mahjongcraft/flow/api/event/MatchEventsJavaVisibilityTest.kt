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

    /** 以 Java 介面的方法反射呼叫，模擬 Java 原始碼的呼叫方式。 */
    private fun javaCall(target: Any, name: String, parameterType: Class<*>? = null, argument: Any? = null): Any? {
        val owner = if (target is Iterator<*>) Iterator::class.java else Collection::class.java
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
