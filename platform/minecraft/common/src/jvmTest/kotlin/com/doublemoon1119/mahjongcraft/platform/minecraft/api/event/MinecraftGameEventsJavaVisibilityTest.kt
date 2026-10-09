package com.doublemoon1119.mahjongcraft.platform.minecraft.api.event

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證 Minecraft 遊戲事件 API 的建構與內部建立入口不會意外暴露給 Java。 */
class MinecraftGameEventsJavaVisibilityTest {
    /** 不允許第三方直接建立的 API 型別。 */
    private val types = listOf(
        GameEventContext::class.java,
        TableLocation::class.java,
        MahjongCraftEvent::class.java,
    )

    /** API 型別不提供 Java 可呼叫的非私有、非 synthetic 建構子。 */
    @Test
    fun `no public constructors are exposed to Java`() {
        types.forEach { type ->
            val visibleConstructors = type.declaredConstructors.filter {
                !it.isSynthetic && !Modifier.isPrivate(it.modifiers)
            }
            assertEquals(
                emptyList(),
                visibleConstructors.map { it.toGenericString() },
                "${type.simpleName} must not expose a Java-callable constructor",
            )
        }
    }

    /** API 型別的內部建立入口皆為 synthetic，Java 原始碼無法直接呼叫。 */
    @Test
    fun `internal factories are hidden from Java`() {
        types.forEach { type ->
            val companion = type.declaredClasses.single { it.simpleName == "Companion" }
            val factories = companion.declaredMethods.filter { it.name.startsWith("create") }
            assertTrue(factories.isNotEmpty(), "${type.simpleName} must have a create factory")
            assertTrue(factories.all { it.isSynthetic }, "${type.simpleName} create factories must be synthetic")
        }
    }

    /** 事件情境的內部 session 識別碼 getter 必須隱藏，桌子位置 getter 則維持公開。 */
    @Test
    fun `context exposes only the public location getter to Java`() {
        val sessionGetters = GameEventContext::class.java.declaredMethods.filter {
            it.name.startsWith("getSessionId")
        }
        assertTrue(sessionGetters.isNotEmpty(), "GameEventContext must have a session ID getter")
        assertTrue(sessionGetters.all { it.isSynthetic }, "GameEventContext session ID getters must be synthetic")

        val locationGetter = GameEventContext::class.java.getMethod("getTableLocation")
        assertTrue(Modifier.isPublic(locationGetter.modifiers), "GameEventContext table location must be public")
        assertTrue(!locationGetter.isSynthetic, "GameEventContext table location getter must not be synthetic")
    }
}
