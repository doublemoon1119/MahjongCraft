package com.doublemoon1119.mahjongcraft.example.minecraft.mod.table

import com.doublemoon1119.mahjongcraft.example.minecraft.mod.table.ExampleHandRevealChipEntity
import com.doublemoon1119.mahjongcraft.example.minecraft.presentation.ExampleMinecraftPresentationIds
import com.doublemoon1119.mahjongcraft.example.minecraft.presentation.ExampleTablePropDefinitions
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.tableprop.TablePropKind
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropContent
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropTarget
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import kotlin.uuid.Uuid

/** 範例攤牌籌碼的原生桌面物件種類。 */
object ExampleHandRevealChipTablePropKind : TablePropKind {
    /** 晶片種類與唯一接受的變體。 */
    const val KIND_ID = ExampleMinecraftPresentationIds.HAND_REVEAL_CHIP_KIND

    /** 已完成攤牌的籌碼變體。 */
    const val HAND_REVEALED_VARIANT = ExampleMinecraftPresentationIds.HAND_REVEAL_CHIP_VARIANT

    override val definition = ExampleTablePropDefinitions.HAND_REVEAL_CHIP

    override fun placementIdOf(entity: Entity): String? = (entity as? ExampleHandRevealChipEntity)
        ?.placementId
        ?.takeIf(String::isNotBlank)

    override fun ruleModuleIdOf(entity: Entity): String? = (entity as? ExampleHandRevealChipEntity)?.tablePropRuleModuleId?.takeIf(String::isNotBlank)

    override fun findManaged(world: ServerLevel, tableId: Uuid, searchBox: AABB): List<Entity> = world.getEntitiesOfClass(
        ExampleHandRevealChipEntity::class.java,
        searchBox,
    ) { it.managedTableId == tableId }

    override fun contentOf(entity: Entity): TablePropContent? = (entity as? ExampleHandRevealChipEntity)?.let {
        TablePropContent.CustomVariant(HAND_REVEALED_VARIANT)
    }

    override fun create(world: ServerLevel, tableId: Uuid, target: TablePropTarget): Entity? {
        if (target.content != TablePropContent.CustomVariant(HAND_REVEALED_VARIANT)) return null
        return ExampleHandRevealChipEntity(ExampleHandRevealChipEntities.handRevealChip, world).apply {
            managedTableId = tableId
            placementId = target.id
            tablePropRuleModuleId = target.ruleModuleId
            moveTo(target.x, target.y, target.z, target.yaw, 0f)
        }
    }
}
