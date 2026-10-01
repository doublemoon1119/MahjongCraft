package com.doublemoon1119.mahjongcraft.platform.fabric.server.seating

import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongStoolBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongStoolSeatEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.MahjongSeatingPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.MahjongSeatingTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.property.Properties
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 使用 Fabric 1.20.1 玩家傳送實作 [MahjongSeatingPresenter]。
 *
 * 每位玩家傳送到分配到的座位位置並面向桌子；該位置是麻將凳時接著坐上去。已經坐在分配到的凳子上的玩家不移動。
 */
@Single(binds = [MahjongSeatingPresenter::class])
class FabricMahjongSeatingPresenter(
    private val tableLocationRegistry: TableLocationRegistry,
    private val serverHolder: FabricServerHolder,
    private val seatService: MahjongStoolSeatService,
) : MahjongSeatingPresenter {
    override fun present(tableId: Uuid, seatedPlayerIds: List<Uuid>) {
        val location = tableLocationRegistry.get(tableId)?.location ?: return
        val world = resolveWorld(location) ?: return
        val controllerPos = BlockPos(location.x, location.y, location.z)
        val state = world.getBlockState(controllerPos)
        if (!state.contains(Properties.HORIZONTAL_FACING)) return
        val tableFacing = state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing()
        val placements = MahjongSeatingTableLayout.seatPlacements(location.x, location.y, location.z, tableFacing)

        seatedPlayerIds.forEachIndexed { index, playerId ->
            val placement = placements.getOrNull(index) ?: return@forEachIndexed
            val player = serverHolder.findPlayer(playerId) ?: return@forEachIndexed
            val stoolPos = BlockPos.ofFloored(placement.x, placement.y, placement.z)
            if (isSeatedOn(player, world, stoolPos)) return@forEachIndexed
            player.teleport(world, placement.x, placement.y, placement.z, placement.yaw, 0.0f)
            if (world.getBlockState(stoolPos).block is MahjongStoolBlock) seatService.sit(world, stoolPos, player)
        }
    }

    /** [player] 是否已經坐在 [world] 中位於 [stoolPos] 的麻將凳上。 */
    private fun isSeatedOn(player: ServerPlayerEntity, world: ServerWorld, stoolPos: BlockPos): Boolean {
        val seat = player.vehicle as? MahjongStoolSeatEntity ?: return false
        return seat.world === world && seat.blockPos == stoolPos
    }

    /** 由版本無關 dimension ID 取得目前 server session 的世界。 */
    private fun resolveWorld(location: TableLocation): ServerWorld? {
        val identifier = Identifier.tryParse(location.dimensionId) ?: return null
        val worldKey = RegistryKey.of(RegistryKeys.WORLD, identifier)
        return serverHolder.current()?.getWorld(worldKey)
    }
}
