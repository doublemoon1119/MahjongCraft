package com.doublemoon1119.mahjongcraft.example.fabric.table

import com.doublemoon1119.mahjongcraft.example.minecraft.presentation.ExampleMinecraftPresentationIds
import com.doublemoon1119.mahjongcraft.example.minecraft.presentation.ExampleTablePropDefinitions
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FabricTablePropKind
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropContent
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropTarget
import net.minecraft.entity.Entity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Box
import kotlin.uuid.Uuid

/** 範例宣告籌碼的 Fabric 桌面物件種類。 */
object ExampleDeclarationChipTablePropKind : FabricTablePropKind {
    /** 晶片種類與唯一接受的變體。 */
    const val KIND_ID = ExampleMinecraftPresentationIds.DECLARATION_CHIP_KIND

    /** 已完成宣告的晶片變體。 */
    const val DECLARED_VARIANT = ExampleMinecraftPresentationIds.DECLARATION_CHIP_VARIANT

    override val definition = ExampleTablePropDefinitions.DECLARATION_CHIP

    override fun placementIdOf(entity: Entity): String? = (entity as? ExampleDeclarationChipEntity)
        ?.placementId
        ?.takeIf(String::isNotBlank)

    override fun ruleModuleIdOf(entity: Entity): String? = (entity as? ExampleDeclarationChipEntity)?.tablePropRuleModuleId?.takeIf(String::isNotBlank)

    override fun findManaged(world: ServerWorld, tableId: Uuid, searchBox: Box): List<Entity> = world.getEntitiesByClass(
        ExampleDeclarationChipEntity::class.java,
        searchBox,
    ) { it.managedTableId == tableId }

    override fun contentOf(entity: Entity): TablePropContent? = (entity as? ExampleDeclarationChipEntity)?.let {
        TablePropContent.CustomVariant(DECLARED_VARIANT)
    }

    override fun create(world: ServerWorld, tableId: Uuid, target: TablePropTarget): Entity? {
        if (target.content != TablePropContent.CustomVariant(DECLARED_VARIANT)) return null
        return ExampleDeclarationChipEntity(ExampleDeclarationChipEntities.declarationChip, world).apply {
            managedTableId = tableId
            placementId = target.id
            tablePropRuleModuleId = target.ruleModuleId
            refreshPositionAndAngles(target.x, target.y, target.z, target.yaw, 0f)
        }
    }
}
