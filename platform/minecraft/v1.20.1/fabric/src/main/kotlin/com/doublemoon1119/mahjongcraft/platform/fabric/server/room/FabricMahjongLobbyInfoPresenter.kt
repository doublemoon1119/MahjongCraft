package com.doublemoon1119.mahjongcraft.platform.fabric.server.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTablePart
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongLobbyInfoEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.FabricEntitySpawnGateway
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import kotlin.uuid.Uuid

/**
 * 建立、更新及清除一桌唯一的等待中遊戲提示。
 *
 * @property serverHolder 查詢目前伺服器與世界。
 * @property locations 查詢麻將桌位置索引。
 * @property spawnGateway 透過平台入口生成提示實體。
 * @property moduleRegistry 解析遊戲規則所屬模組。
 */
class FabricMahjongLobbyInfoPresenter(
    private val serverHolder: FabricServerHolder,
    private val locations: TableLocationRegistry,
    private val spawnGateway: FabricEntitySpawnGateway,
    private val moduleRegistry: MahjongModuleRegistry,
) {
    /**
     * 建立或更新指定桌子的提示實體，並清除該桌多餘的重複實體。
     *
     * @param tableId 桌子 UUID。
     * @param config 該等待室目前的遊戲設定，用於解析規則模組。
     * @param playerCount 目前等待室人數。
     * @return 桌子位置、世界、方塊或實體生成條件皆符合並完成呈現時為 true。
     */
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
            if (!spawnGateway.spawn(world, it, "lobby-info", tableId)) return false
        }
        existingEntities.drop(1).forEach(MahjongLobbyInfoEntity::discard)
        entity.ruleModuleId = moduleRegistry.getModule(config.ruleConfig).id
        entity.playerCount = playerCount
        entity.maximumPlayerCount = config.ruleConfig.maxPlayers
        entity.refreshPositionAndAngles(placement.x, placement.y, placement.z, placement.yaw, 0f)
        return true
    }

    /**
     * 移除指定桌子附近所有由此 presenter 管理的提示實體。
     *
     * @param tableId 桌子 UUID。
     * @return 實際移除的提示實體數量；位置或世界無法解析時為 0。
     */
    fun clear(tableId: Uuid): Int {
        val location = locations.get(tableId)?.location ?: return 0
        val world = resolveWorld(location) ?: return 0
        val controllerPos = BlockPos(location.x, location.y, location.z)
        return findAll(world, tableId, controllerPos).onEach(MahjongLobbyInfoEntity::discard).size
    }

    /**
     * 找出控制方塊附近且登記為指定桌子的提示實體。
     *
     * @param world 要搜尋的伺服器世界。
     * @param tableId 桌子 UUID。
     * @param controllerPos 桌子控制方塊座標。
     * @return 搜尋範圍內所有符合桌子 UUID 的提示實體。
     */
    private fun findAll(world: ServerWorld, tableId: Uuid, controllerPos: BlockPos): List<MahjongLobbyInfoEntity> = world.getEntitiesByClass(MahjongLobbyInfoEntity::class.java, Box(controllerPos).expand(4.0)) {
        it.managedTableId == tableId
    }

    /**
     * 依位置中的維度 ID 解析目前伺服器世界。
     *
     * @param location 桌子所在位置。
     * @return 對應的伺服器世界；維度 ID 無效、伺服器不存在或世界未載入時為 null。
     */
    private fun resolveWorld(location: TableLocation): ServerWorld? {
        val identifier = Identifier.tryParse(location.dimensionId) ?: return null
        return serverHolder.current()?.getWorld(RegistryKey.of(RegistryKeys.WORLD, identifier))
    }
}
