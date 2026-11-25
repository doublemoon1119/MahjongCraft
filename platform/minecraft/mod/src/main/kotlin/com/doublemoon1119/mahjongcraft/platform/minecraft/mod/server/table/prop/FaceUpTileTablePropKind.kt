package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.table.prop

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.tableprop.TablePropKind
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.TablePropTileEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.BuiltInTablePropDefinitions
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropContent
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropTarget
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.toAssetKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import kotlin.uuid.Uuid

/**
 * 公開牌面展示種類。副本不具有正式牌張身分或互動能力。
 *
 * 牌面依組件的公開效果選擇素材，與同樣牌面的正式牌張一致。
 *
 * @property tileAssets 牌種資源與規則外觀的唯一查詢來源。
 * @property faceCodec 保存與還原展示牌面的格式。
 */
internal class FaceUpTileTablePropKind(
    private val tileAssets: MinecraftTileAssetRegistry,
    private val faceCodec: TablePropTileFaceCodec,
) : TablePropKind {
    override val definition = BuiltInTablePropDefinitions.FACE_UP_TILE

    override fun ruleModuleIdOf(entity: Entity): String? = (entity as? TablePropTileEntity)?.tablePropRuleModuleId?.takeIf(String::isNotBlank)

    override fun findManaged(world: ServerLevel, tableId: Uuid, searchBox: AABB): List<Entity> = world.getEntitiesOfClass(
        TablePropTileEntity::class.java,
        searchBox,
    ) { it.managedTableId == tableId }

    override fun placementIdOf(entity: Entity): String? = (entity as? TablePropTileEntity)?.placementId?.takeIf { it.isNotBlank() }

    override fun contentOf(entity: Entity): TablePropContent? = (entity as? TablePropTileEntity)
        ?.let { prop -> faceCodec.decode(prop.faceData) }
        ?.let(TablePropContent::FaceUpTile)

    override fun create(world: ServerLevel, tableId: Uuid, target: TablePropTarget): Entity? {
        val content = target.content as? TablePropContent.FaceUpTile ?: return null
        val appearance = tileAssets.findAppearanceForRule(target.ruleModuleId) ?: return null
        return TablePropTileEntity(world = world).apply {
            managedTableId = tableId
            placementId = target.id
            faceData = faceCodec.encode(content.face)
            assetKey = content.face.toAssetKey(tileAssets)
            appearanceId = appearance.id
            tablePropRuleModuleId = target.ruleModuleId
            moveTo(target.x, target.y, target.z, target.yaw, 0f)
        }
    }
}
