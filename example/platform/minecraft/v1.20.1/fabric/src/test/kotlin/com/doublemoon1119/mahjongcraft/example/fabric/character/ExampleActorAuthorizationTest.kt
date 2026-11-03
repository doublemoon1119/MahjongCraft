package com.doublemoon1119.mahjongcraft.example.fabric.character

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 放置者授權與名稱及遊戲成員 UUID 無關。 */
class ExampleActorAuthorizationTest {
    /** 缺少放置者或不同房主不能借用人偶。 */
    @Test
    fun `only the recorded placer can host an actor`() {
        val owner = Uuid.random()
        assertTrue(ExampleActorAuthorization.canJoin(owner, owner))
        assertFalse(ExampleActorAuthorization.canJoin(null, owner))
        assertFalse(ExampleActorAuthorization.canJoin(owner, Uuid.random()))
    }
}
