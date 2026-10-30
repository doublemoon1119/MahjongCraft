package com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.TablePropTileEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.BuiltInTablePropDefinitions
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropContent
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropTarget
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.toAssetKey
import net.minecraft.entity.Entity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Box
import kotlin.uuid.Uuid

/**
 * 公開牌面展示種類；副本不具有正式牌張身分或互動能力。
 *
 * @property tileAssets 牌種資源與規則外觀的唯一查詢來源。
 */
class FaceUpTileTablePropKind(private val tileAssets: MinecraftTileAssetRegistry) : FabricTablePropKind {
    override val definition = BuiltInTablePropDefinitions.FACE_UP_TILE

    override fun ruleModuleIdOf(entity: Entity): String? = (entity as? TablePropTileEntity)?.tablePropRuleModuleId?.takeIf(String::isNotBlank)

    override fun findManaged(world: ServerWorld, tableId: Uuid, searchBox: Box): List<Entity> = world.getEntitiesByClass(
        TablePropTileEntity::class.java,
        searchBox,
    ) { it.managedTableId == tableId }

    override fun placementIdOf(entity: Entity): String? = (entity as? TablePropTileEntity)?.placementId?.takeIf { it.isNotBlank() }

    override fun contentOf(entity: Entity): TablePropContent? = (entity as? TablePropTileEntity)?.tile?.let(TablePropContent::FaceUpTile)

    override fun create(world: ServerWorld, tableId: Uuid, target: TablePropTarget): Entity? {
        val content = target.content as? TablePropContent.FaceUpTile ?: return null
        val appearance = tileAssets.findAppearanceForRule(target.ruleModuleId) ?: return null
        return TablePropTileEntity(world = world).apply {
            managedTableId = tableId
            placementId = target.id
            tile = content.tile
            assetKey = content.tile.toAssetKey(tileAssets)
            appearanceId = appearance.id
            tablePropRuleModuleId = target.ruleModuleId
            refreshPositionAndAngles(target.x, target.y, target.z, target.yaw, 0f)
        }
    }
}
