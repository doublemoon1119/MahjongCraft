package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.RoomParticipant
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.RoomParticipantDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.RoomParticipantProvider
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.RoomVacancy
import com.doublemoon1119.mahjongcraft.platform.fabric.server.character.RoomParticipantRegistry
import net.minecraft.world.entity.Entity
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證既有實體能力的登記邊界與伺服器查詢值的 Java 可見性。 */
class RoomParticipantRegistrationTest {
    /** 登記不辨識實體，極端優先權也保持穩定排序；凍結後拒絕寫入。 */
    @Test
    fun `participant providers register lazily with deterministic priority and freeze`() {
        val registry = RoomParticipantRegistry()
        val provider = deferred()
        registry.register(RoomParticipantDefinition("test:z", provider, Int.MAX_VALUE))
        registry.register(RoomParticipantDefinition("test:a", provider, Int.MAX_VALUE))
        registry.register(RoomParticipantDefinition("test:last", provider, Int.MIN_VALUE))
        assertEquals(listOf("test:a", "test:z", "test:last"), registry.definitions().map { it.id })
        registry.freeze()
        registry.definitions().toMutableList().clear()
        assertEquals(3, registry.definitions().size)
        assertFailsWith<IllegalStateException> { registry.register(RoomParticipantDefinition("test:late", provider)) }
    }

    /** 容量在登記時拒絕，不能在執行時偷偷忽略後面的能力。 */
    @Test
    fun `participant provider capacity and duplicates fail registration`() {
        val registry = RoomParticipantRegistry()
        val provider = deferred()
        repeat(64) { registry.register(RoomParticipantDefinition("test:provider_$it", provider)) }
        assertFailsWith<IllegalArgumentException> { registry.register(RoomParticipantDefinition("test:overflow", provider)) }
        assertFailsWith<IllegalArgumentException> { registry.register(RoomParticipantDefinition("test:provider_0", provider)) }
        assertEquals(64, registry.registrationKeys.size)
    }

    /** 查詢結果只由框架建立，不開放 Java 呼叫建構子或內部建立入口。 */
    @Test
    fun `vacancy has no Java callable constructor or internal factory`() {
        assertTrue(RoomVacancy::class.java.constructors.all { it.isSynthetic || !Modifier.isPublic(it.modifiers) })
        val factories = RoomVacancy.Companion::class.java.declaredMethods.filter { it.name.startsWith("create") }
        assertTrue(factories.isNotEmpty())
        assertTrue(factories.all { it.isSynthetic })
    }

    /** 建立不能在登記時執行的能力替身。 */
    private fun deferred(): RoomParticipantProvider = object : RoomParticipantProvider {
        override fun describe(entity: Entity): RoomParticipant? = error("Registration must not invoke providers")

        override fun canJoin(entity: Entity, hostId: Uuid): Boolean = error("Registration must not invoke authorization")
    }
}
