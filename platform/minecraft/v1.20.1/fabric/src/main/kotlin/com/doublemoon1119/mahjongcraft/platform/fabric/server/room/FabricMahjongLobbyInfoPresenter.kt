package com.doublemoon1119.mahjongcraft.platform.fabric.server.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTablePart
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongLobbyInfoEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 建立、更新及清除一桌唯一的等待中遊戲提示。 */
@Single
class FabricMahjongLobbyInfoPresenter(
    private val serverHolder: FabricServerHolder,
    private val locations: TableLocationRegistry,
    @Provided private val moduleRegistry: MahjongModuleRegistry,
) {
    fun present(tableId: Uuid, config: GameConfig, playerCount: Int): Boolean {
        val location = locations.get(tableId)?.location ?: return false
        val world = resolveWorld(location) ?: return false
        val controllerPos = BlockPos(location.x, location.y, location.z)
        val table = world.getBlockEntity(controllerPos) as? MahjongTableBlockEntity ?: return false
        if (table.tableId != tableId ||
            table.cachedState.block !is MahjongTableBlock ||
            table.cachedState.get(MahjongTableBlock.PART) != MahjongTablePart.BOTTOM_CENTER
        ) {
            return false
        }
        val placement = MahjongTileTableLayout.roundInfoDisplayPlacement(controllerPos.x, controllerPos.y, controllerPos.z)
        val existingEntities = findAll(world, tableId, controllerPos)
        val entity = existingEntities.firstOrNull() ?: MahjongLobbyInfoEntity(world = world).also {
            it.assignToTable(tableId, controllerPos)
            if (!world.spawnEntity(it)) return false
        }
        existingEntities.drop(1).forEach(MahjongLobbyInfoEntity::discard)
        entity.ruleModuleId = moduleRegistry.getModule(config.ruleConfig).id
        entity.playerCount = playerCount
        entity.maximumPlayerCount = config.ruleConfig.maxPlayers
        entity.refreshPositionAndAngles(placement.x, placement.y, placement.z, placement.yaw, 0f)
        return true
    }

    fun clear(tableId: Uuid): Int {
        val location = locations.get(tableId)?.location ?: return 0
        val world = resolveWorld(location) ?: return 0
        val controllerPos = BlockPos(location.x, location.y, location.z)
        return findAll(world, tableId, controllerPos).onEach(MahjongLobbyInfoEntity::discard).size
    }

    private fun findAll(world: ServerWorld, tableId: Uuid, controllerPos: BlockPos): List<MahjongLobbyInfoEntity> = world.getEntitiesByClass(MahjongLobbyInfoEntity::class.java, Box(controllerPos).expand(4.0)) {
        it.managedTableId == tableId
    }

    private fun resolveWorld(location: TableLocation): ServerWorld? {
        val identifier = Identifier.tryParse(location.dimensionId) ?: return null
        return serverHolder.current()?.getWorld(RegistryKey.of(RegistryKeys.WORLD, identifier))
    }
}
