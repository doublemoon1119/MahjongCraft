package com.doublemoon1119.mahjongcraft.example.fabric.table

import com.doublemoon1119.mahjongcraft.example.minecraft.presentation.ExampleMinecraftPresentationIds
import com.doublemoon1119.mahjongcraft.example.minecraft.presentation.ExampleTablePropDefinitions
import com.doublemoon1119.mahjongcraft.platform.fabric.api.tableprop.FabricTablePropKind
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropContent
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropTarget
import net.minecraft.entity.Entity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Box
import kotlin.uuid.Uuid

/** 範例攤牌籌碼的 Fabric 桌面物件種類。 */
object ExampleHandRevealChipTablePropKind : FabricTablePropKind {
    /** 晶片種類與唯一接受的變體。 */
    const val KIND_ID = ExampleMinecraftPresentationIds.HAND_REVEAL_CHIP_KIND

    /** 已完成攤牌的籌碼變體。 */
    const val HAND_REVEALED_VARIANT = ExampleMinecraftPresentationIds.HAND_REVEAL_CHIP_VARIANT

    override val definition = ExampleTablePropDefinitions.HAND_REVEAL_CHIP

    override fun placementIdOf(entity: Entity): String? = (entity as? ExampleHandRevealChipEntity)
        ?.placementId
        ?.takeIf(String::isNotBlank)

    override fun ruleModuleIdOf(entity: Entity): String? = (entity as? ExampleHandRevealChipEntity)?.tablePropRuleModuleId?.takeIf(String::isNotBlank)

    override fun findManaged(world: ServerWorld, tableId: Uuid, searchBox: Box): List<Entity> = world.getEntitiesByClass(
        ExampleHandRevealChipEntity::class.java,
        searchBox,
    ) { it.managedTableId == tableId }

    override fun contentOf(entity: Entity): TablePropContent? = (entity as? ExampleHandRevealChipEntity)?.let {
        TablePropContent.CustomVariant(HAND_REVEALED_VARIANT)
    }

    override fun create(world: ServerWorld, tableId: Uuid, target: TablePropTarget): Entity? {
        if (target.content != TablePropContent.CustomVariant(HAND_REVEALED_VARIANT)) return null
        return ExampleHandRevealChipEntity(ExampleHandRevealChipEntities.handRevealChip, world).apply {
            managedTableId = tableId
            placementId = target.id
            tablePropRuleModuleId = target.ruleModuleId
            refreshPositionAndAngles(target.x, target.y, target.z, target.yaw, 0f)
        }
    }
}
