package com.doublemoon1119.mahjongcraft.platform.fabric.client.tile

import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.Vec3d
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * 逐 client tick 追蹤各桌的捨牌是否已經落到牌河，見 [DiscardSettlementState]；捨牌的標示與說明只在牌落定後顯示。
 *
 * @property stateStore 玩家看得到的桌況。
 */
class ClientDiscardSettlementTracker(
    private val stateStore: ClientMahjongStateStore,
) {
    /** 各桌捨牌的落定狀態。 */
    private val state = DiscardSettlementState<Vec3d>()

    /** 註冊 client tick 更新；要在讀取落定狀態的功能之前註冊。 */
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    /**
     * 判斷一張牌是否已經落到牌河。
     *
     * @param tableId 桌子。
     * @param tileId 牌。
     * @return 已經落定時為 true。
     */
    fun isSettled(tableId: Uuid, tileId: Uuid): Boolean = state.isSettled(tableId, tileId)

    /**
     * 依各桌的牌河紀錄與牌實體更新落定狀態。
     *
     * @param client Minecraft client 狀態與當前世界。
     */
    private fun tick(client: MinecraftClient) {
        val tableIds = stateStore.gameTableIds()
        state.retainTables(tableIds)
        val tiles = client.world?.entities?.filterIsInstance<MahjongTileEntity>()?.associateBy { it.uuid.toKotlinUuid() }.orEmpty()
        tableIds.forEach { tableId ->
            val snapshot = stateStore.gameSnapshot(tableId) ?: return@forEach
            val discardTileIds = snapshot.players.flatMapTo(mutableSetOf()) { player -> player.discardPile.entries.map { it.tile.id } }
            state.update(
                tableId = tableId,
                discardTileIds = discardTileIds,
                positionOf = { tileId -> tiles[tileId]?.pos },
                isMoving = { tileId -> tiles[tileId]?.animating == true },
            )
        }
    }
}
