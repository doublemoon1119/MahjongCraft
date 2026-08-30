package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTablePart
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongPlayerInfoEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongPlayerInfoPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongPlayerInfoPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongPlayerInfoPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.property.Properties
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** Fabric 1.20.1 的一桌一 entity 玩家公開資訊 presenter。 */
@Single(binds = [MahjongPlayerInfoPresenter::class])
class FabricMahjongPlayerInfoPresenter(private val serverHolder: FabricServerHolder) : MahjongPlayerInfoPresenter {
    override fun present(
        presentation: MahjongPlayerInfoPresentation,
        tableLocation: TableLocation,
        tableFacing: MahjongTableFacing,
    ): MahjongPlayerInfoPresentationResult {
        val world = resolveWorld(tableLocation) ?: return MahjongPlayerInfoPresentationResult.TABLE_NOT_FOUND
        val controllerPos = tableLocation.toBlockPos()
        val state = world.getBlockState(controllerPos)
        val table = (world.getBlockEntity(controllerPos) as? MahjongTableBlockEntity)?.takeIf {
            state.block is MahjongTableBlock &&
                state.get(MahjongTableBlock.PART) == MahjongTablePart.BOTTOM_CENTER &&
                it.tableId == presentation.tableId &&
                state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing() == tableFacing
        } ?: return MahjongPlayerInfoPresentationResult.TABLE_NOT_FOUND

        val entity = find(world, presentation.tableId, controllerPos) ?: MahjongPlayerInfoEntity(world = world).apply {
            refreshPositionAndAngles(controllerPos.x + 0.5, controllerPos.y.toDouble(), controllerPos.z + 0.5, 0f, 0f)
            assignToTable(presentation.tableId, controllerPos)
            if (!world.spawnEntity(this)) return MahjongPlayerInfoPresentationResult.SPAWN_FAILED
        }
        entity.players = presentation.players
        entity.dealerPlayerId = presentation.dealerPlayerId
        entity.tableFacing = tableFacing
        table.markDirty()
        return MahjongPlayerInfoPresentationResult.PRESENTED
    }

    override fun hideUntil(tableId: Uuid, tableLocation: TableLocation, gameTime: Long) {
        val world = resolveWorld(tableLocation) ?: return
        find(world, tableId, tableLocation.toBlockPos())?.hideUntil(gameTime)
    }

    override fun clear(tableId: Uuid, tableLocation: TableLocation): Int {
        val world = resolveWorld(tableLocation) ?: return 0
        val controllerPos = tableLocation.toBlockPos()
        val displays = findAll(world, tableId, controllerPos)
        displays.forEach(MahjongPlayerInfoEntity::discard)
        return displays.size
    }

    private fun resolveWorld(location: TableLocation): ServerWorld? {
        val identifier = Identifier.tryParse(location.dimensionId) ?: return null
        return serverHolder.current()?.getWorld(RegistryKey.of(RegistryKeys.WORLD, identifier))
    }

    private fun find(world: ServerWorld, tableId: Uuid, pos: BlockPos): MahjongPlayerInfoEntity? = findAll(world, tableId, pos).firstOrNull()

    private fun findAll(world: ServerWorld, tableId: Uuid, pos: BlockPos): List<MahjongPlayerInfoEntity> = world.getEntitiesByClass(
        MahjongPlayerInfoEntity::class.java,
        Box(pos).expand(TableOverlayEntitySearch.HORIZONTAL_RADIUS, TableOverlayEntitySearch.VERTICAL_RADIUS, TableOverlayEntitySearch.HORIZONTAL_RADIUS),
    ) { it.managedTableId == tableId }

    private fun TableLocation.toBlockPos() = BlockPos(x, y, z)
}
