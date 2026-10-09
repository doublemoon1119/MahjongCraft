package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證 Fabric 位置橋接只讀取已發布的不可變記憶體索引。 */
class FabricGameEventLocationSourceTest {
    /** 尚未登記的場地沒有位置，不需要查詢世界。 */
    @Test
    fun `unknown venues have no location`() {
        val source = FabricGameEventLocationSource(TableLocationRegistry())

        assertNull(source.find(Uuid.random()))
    }

    /** 背景取得的位置保持原值，不受主執行緒後續更新或移除影響。 */
    @Test
    fun `background lookup retains its captured location after index changes`() {
        val registry = TableLocationRegistry()
        val source = FabricGameEventLocationSource(registry)
        val venueId = Uuid.random()
        val original = TableLocation(dimensionId = "minecraft:overworld", x = 1, y = 64, z = 2)
        registry.put(venueId, original)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val captured = executor.submit<TableLocation?> { source.find(venueId) }.get(5, TimeUnit.SECONDS)
            registry.put(venueId, original.copy(x = 10))
            registry.remove(venueId)

            assertEquals(original, captured)
            assertNull(executor.submit<TableLocation?> { source.find(venueId) }.get(5, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
        }
    }
}
