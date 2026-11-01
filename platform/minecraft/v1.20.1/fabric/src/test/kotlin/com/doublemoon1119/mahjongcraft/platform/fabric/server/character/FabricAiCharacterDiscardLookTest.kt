package com.doublemoon1119.mahjongcraft.platform.fabric.server.character

import net.minecraft.util.math.Vec3d
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證短暫公開注視在換局、鳴走及到期時失效。 */
class FabricAiCharacterDiscardLookTest {
    /** 牌河不再有該牌時，不能繼續注視原本的位置。 */
    @Test
    fun `discard removed by a claim or new round invalidates its look target`() {
        val tileId = Uuid.random()
        val look = FabricAiCharacterDiscardLook(tileId, Vec3d(1.0, 2.0, 3.0), 16L)
        assertTrue(look.isCurrent(5L, setOf(tileId)))
        assertFalse(look.isCurrent(5L, emptySet()))
        assertFalse(look.isCurrent(5L, setOf(Uuid.random())))
    }

    /** 牌仍在牌河時，也只短暫注視，不永久搶占目前行動者的目標。 */
    @Test
    fun `discard look expires even while the public tile remains`() {
        val tileId = Uuid.random()
        val look = FabricAiCharacterDiscardLook(tileId, Vec3d(1.0, 2.0, 3.0), 16L)
        assertTrue(look.isCurrent(16L, setOf(tileId)))
        assertFalse(look.isCurrent(17L, setOf(tileId)))
    }
}
