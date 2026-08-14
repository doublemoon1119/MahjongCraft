package com.doublemoon1119.mahjongcraft.platform.fabric.server.seating

import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.MahjongSeatingPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.MahjongSeatingTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 使用 Fabric 1.20.1 玩家傳送實作 [MahjongSeatingPresenter]。 */
@Single(binds = [MahjongSeatingPresenter::class])
class FabricMahjongSeatingPresenter(
    private val tableLocationRegistry: TableLocationRegistry,
    private val serverHolder: FabricServerHolder,
) : MahjongSeatingPresenter {
    override fun present(tableId: Uuid, seatedPlayerIds: List<Uuid>) {
        val location = tableLocationRegistry.get(tableId)?.location ?: return
        val world = resolveWorld(location) ?: return
        val placements = MahjongSeatingTableLayout.seatPlacements(location.x, location.y, location.z)

        seatedPlayerIds.forEachIndexed { index, playerId ->
            val placement = placements.getOrNull(index) ?: return@forEachIndexed
            val player = serverHolder.findPlayer(playerId) ?: return@forEachIndexed
            player.teleport(world, placement.x, placement.y, placement.z, placement.yaw, 0.0f)
        }
    }

    /** 由版本無關 dimension ID 取得目前 server session 的世界。 */
    private fun resolveWorld(location: TableLocation): ServerWorld? {
        val identifier = Identifier.tryParse(location.dimensionId) ?: return null
        val worldKey = RegistryKey.of(RegistryKeys.WORLD, identifier)
        return serverHolder.current()?.getWorld(worldKey)
    }
}
