package com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongScoringStickDenomination
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongScoringStickEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.BuiltInTablePropKinds
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
    override val id: String = BuiltInTablePropKinds.SCORING_STICK

    override fun findManaged(world: ServerWorld, tableId: Uuid, searchBox: Box): List<Entity> = world.getEntitiesByClass(
        MahjongScoringStickEntity::class.java,
        searchBox,
    ) { stick -> stick.managedTableId == tableId }

    override fun variantOf(entity: Entity): String? = (entity as? MahjongScoringStickEntity)?.denomination?.points?.toString()

    override fun create(world: ServerWorld, tableId: Uuid, target: TablePropTarget): Entity? {
        val denomination = MahjongScoringStickDenomination.entries.firstOrNull { it.points.toString() == target.variant }
            ?: return null
        return MahjongScoringStickEntity(world = world).apply {
            refreshPositionAndAngles(target.x, target.y, target.z, target.yaw, 0.0f)
            this.denomination = denomination
            assignToTable(tableId)
        }
    }

    override fun onSpawned(entity: Entity) {
        (entity as? MahjongScoringStickEntity)?.enqueueDropAnimation()
    }
}

/** 登記內建的點棒種類。 */
fun FabricTablePropKindRegistry.registerBuiltInTablePropKinds() = register(ScoringStickTablePropKind)
