package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證借用角色釋放時，只解除本次接管新增的乘坐，不碰後來的外部載具。 */
class AiCharacterMountOwnershipTest {
    /** 接管前已存在的乘坐不屬於本次代理。 */
    @Test
    fun `original vehicle is not detached`() {
        val original = Any()
        assertFalse(AiCharacterMountOwnership.shouldDetach(original, original, original))
    }

    /** 本次安排的載具仍在使用時，可以安全解除。 */
    @Test
    fun `assigned vehicle is detached only while still current`() {
        val original = Any()
        val assigned = Any()
        assertTrue(AiCharacterMountOwnership.shouldDetach(original, assigned, assigned))
        assertTrue(AiCharacterMountOwnership.shouldDetach(null, assigned, assigned))
    }

    /** 外部已讓角色乘坐其他載具，清理不得把它拉下來。 */
    @Test
    fun `external replacement vehicle is not detached`() {
        val assigned = Any()
        val external = Any()
        assertFalse(AiCharacterMountOwnership.shouldDetach(null, assigned, external))
        assertFalse(AiCharacterMountOwnership.shouldDetach(null, assigned, null))
        assertFalse(AiCharacterMountOwnership.shouldDetach(null, null, external))
    }
}
