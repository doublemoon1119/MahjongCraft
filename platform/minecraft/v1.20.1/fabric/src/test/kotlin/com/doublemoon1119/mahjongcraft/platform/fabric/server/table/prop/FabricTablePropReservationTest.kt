package com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop

import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.FabricEntitySpawnGateway
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.TablePropRefreshQueue
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證擲骰中央保留只由目前世代釋放，且不跨伺服器啟動沿用。 */
class FabricTablePropReservationTest {
    /** 舊工作完成不能釋放同一桌的新開局保留。 */
    @Test
    fun `old opening cannot release a newer reservation`() {
        val presenter = presenter()
        val tableId = Uuid.random()
        presenter.reserveCenter(tableId, 1)
        presenter.reserveCenter(tableId, 2)
        assertFalse(presenter.releaseCenter(tableId, 1))
        assertTrue(presenter.releaseCenter(tableId, 2))
        assertFalse(presenter.releaseCenter(tableId, 2))
    }

    /** 不同桌子的保留獨立，換 session 後全部清除。 */
    @Test
    fun `reservations are isolated by table and reset on a new session`() {
        val presenter = presenter()
        val first = Uuid.random()
        val second = Uuid.random()
        presenter.reserveCenter(first, 3)
        presenter.reserveCenter(second, 4)
        assertTrue(presenter.releaseCenter(first, 3))
        assertFalse(presenter.releaseCenter(second, 3))
        presenter.resetSession()
        assertFalse(presenter.releaseCenter(second, 4))
    }

    /**
     * 建立未綁定世界的呈現器，測試保留狀態不觸碰原生世界。
     *
     * @return 測試用的原生桌面呈現器。
     */
    private fun presenter(): FabricTablePropPresenter = FabricTablePropPresenter(
        serverHolder = FabricServerHolder(),
        spawnGateway = FabricEntitySpawnGateway(),
        kindRegistry = FabricTablePropKindRegistryImpl(),
        refreshQueue = TablePropRefreshQueue(),
    )
}
