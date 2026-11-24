package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.player

import net.minecraft.world.entity.player.PlayerModelPart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證不在世界中的玩家外觀如何決定外層部位。 */
class PlayerModelPartVisibilityTest {
    /** 看不到玩家自己的設定時，帽子、外套、袖子、褲管與披風全部顯示。 */
    @Test
    fun `every part is shown when the player's own settings are unavailable`() {
        PlayerModelPart.entries.forEach { part -> assertTrue(modelPartShown(part, livePartShown = null), part.name) }
    }

    /** 玩家在世界中時，每個部位都照他自己的設定。 */
    @Test
    fun `parts follow the player's own settings when available`() {
        val shown = setOf(PlayerModelPart.HAT, PlayerModelPart.CAPE)

        val result = PlayerModelPart.entries.filter { part -> modelPartShown(part, livePartShown = { it in shown }) }.toSet()

        assertEquals(shown, result)
    }
}
