package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * 從目前伺服器、位置索引與已發布權威快照取得桌級交付的來源。
 *
 * @property serverHolder 目前伺服器與啟動身分。
 * @property locations 已發布的桌子位置索引。
 * @property store 已發布的權威房間與對局快照。
 */
@Single(binds = [TableStateDeliveryEnvironment::class])
internal class FabricTableStateDeliveryEnvironment(
    private val serverHolder: FabricServerHolder,
    private val locations: TableLocationRegistry,
    private val store: AuthoritativeStateStore,
) : TableStateDeliveryEnvironment {
    override val sessionId: Uuid? get() = serverHolder.sessionId()

    override fun locationEntry(tableId: Uuid): TableLocationEntry? = locations.get(tableId)

    override fun hasAuthoritativeState(tableId: Uuid): Boolean {
        val state = store.state.value
        return tableId in state.rooms || tableId in state.games
    }

    override fun trackingObservers(entry: TableLocationEntry): Set<Uuid> {
        val server = serverHolder.current() ?: return emptySet()
        check(server.isOnThread) { "Table observer capture must run on the server thread" }
        val dimension = Identifier.tryParse(entry.location.dimensionId) ?: return emptySet()
        val world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, dimension)) ?: return emptySet()
        return PlayerLookup.tracking(world, BlockPos(entry.location.x, entry.location.y, entry.location.z))
            .mapTo(mutableSetOf()) { it.uuid.toKotlinUuid() }
    }
}
