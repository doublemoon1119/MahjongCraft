package com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongScoringStickEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickDenomination
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.BuiltInTablePropDefinitions
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropContent
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropTarget
import net.minecraft.entity.Entity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Box
import kotlin.uuid.Uuid

/**
 * 內建的點棒種類，對應 [MahjongScoringStickEntity]。
 *
 * 變體是面額的點數字串（例如 `"1000"`），對應 [MahjongScoringStickDenomination.points]。
 */
object ScoringStickTablePropKind : FabricTablePropKind {
    override val definition = BuiltInTablePropDefinitions.SCORING_STICK

    override fun placementIdOf(entity: Entity): String? = (entity as? MahjongScoringStickEntity)?.tablePropPlacementId?.takeIf { it.isNotBlank() }

    override fun ruleModuleIdOf(entity: Entity): String? = (entity as? MahjongScoringStickEntity)?.tablePropRuleModuleId?.takeIf(String::isNotBlank)

    override fun findManaged(world: ServerWorld, tableId: Uuid, searchBox: Box): List<Entity> = world.getEntitiesByClass(
        MahjongScoringStickEntity::class.java,
        searchBox,
    ) { stick -> stick.managedTableId == tableId }

    override fun contentOf(entity: Entity): TablePropContent? = (entity as? MahjongScoringStickEntity)?.let { TablePropContent.CustomVariant(it.denomination.points.toString()) }

    override fun create(world: ServerWorld, tableId: Uuid, target: TablePropTarget): Entity? {
        val denomination = MahjongScoringStickDenomination.entries.firstOrNull { it.points.toString() == (target.content as? TablePropContent.CustomVariant)?.value }
            ?: return null
        return MahjongScoringStickEntity(world = world).apply {
            tablePropRuleModuleId = target.ruleModuleId
            refreshPositionAndAngles(target.x, target.y, target.z, target.yaw, 0.0f)
            tablePropPlacementId = target.id
            this.denomination = denomination
            assignToTable(tableId)
        }
    }

    override fun onSpawned(entity: Entity) {
        (entity as? MahjongScoringStickEntity)?.enqueueDropAnimation()
    }
}
