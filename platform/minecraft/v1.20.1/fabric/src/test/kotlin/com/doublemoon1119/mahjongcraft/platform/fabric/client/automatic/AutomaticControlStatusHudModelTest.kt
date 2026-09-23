package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlSnapshotDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.BuiltInMinecraftAutomaticControlIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.registerBuiltInAutomaticControlDisplays
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證常駐 HUD 僅使用本機已保存偏好與本局權威快照。 */
class AutomaticControlStatusHudModelTest {
    private val displays = AutomaticControlDisplayResolver(
        AutomaticControlDisplayRegistryImpl().apply {
            registerBuiltInAutomaticControlDisplays()
            freeze()
        },
    )

    @Test
    fun `auto sort is first and round controls use authoritative state`() {
        val rows = automaticControlStatusRows(
            activeGameId = "game-a",
            snapshot = snapshot(
                supported = setOf("mahjongcraft:auto_tsumogiri", "mahjongcraft:auto_win", "example:custom"),
                enabled = setOf("mahjongcraft:auto_win", "example:custom"),
            ),
            autoSortHandEnabled = true,
            displays = displays,
        )

        assertEquals(
            listOf(
                BuiltInMinecraftAutomaticControlIds.AUTO_SORT_HAND,
                "mahjongcraft:auto_win",
                "mahjongcraft:auto_tsumogiri",
                "example:custom",
            ),
            rows.map { it.controlId },
        )
        assertEquals(listOf(true, true, false, true), rows.map { it.enabled })
        assertEquals("example:custom", rows.last().label.string)
    }

    @Test
    fun `missing stale or unsupported snapshot hides entire hud`() {
        assertTrue(automaticControlStatusRows("game-a", null, true, displays).isEmpty())
        assertTrue(automaticControlStatusRows("game-b", snapshot(), true, displays).isEmpty())
        assertTrue(automaticControlStatusRows("game-a", snapshot(supported = emptySet()), true, displays).isEmpty())
    }

    private fun snapshot(
        supported: Set<String> = setOf("mahjongcraft:auto_win"),
        enabled: Set<String> = emptySet(),
    ) = AutomaticControlSnapshotDto(
        gameId = "game-a",
        revision = 1,
        supportedControlIds = supported,
        enabledControlIds = enabled,
    )
}
