package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.block.MahjongTableLookup
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongRoundInfoEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.ServerHolder
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.entity.EntitySpawnGateway
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.tile.EntityMahjongTileWallPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongRoundInfoPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongRoundInfoPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongRoundInfoPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.AABB
import kotlin.uuid.Uuid

/**
 * 使用 Minecraft entity 呈現桌面中央局況顯示——比照
 * [EntityMahjongTileWallPresenter] 的
 * 「找到既有的就更新，找不到才生成」模式，不是每次都整批換新（見 [MahjongRoundInfoPresenter] KDoc）。
 *
 * @property serverHolder 取得目前伺服器世界。
 * @property spawnGateway 透過平台入口生成局況顯示 entity。
 */
class EntityMahjongRoundInfoPresenter(
    private val serverHolder: ServerHolder,
    private val spawnGateway: EntitySpawnGateway,
) : MahjongRoundInfoPresenter {
    override fun present(presentation: MahjongRoundInfoPresentation): MahjongRoundInfoPresentationResult {
        val world = resolveWorld(presentation.tableLocation) ?: return MahjongRoundInfoPresentationResult.TABLE_NOT_FOUND
        val controllerPos = presentation.tableLocation.toBlockPos()
        val table = MahjongTableLookup.find(world, controllerPos, presentation.tableId)
            ?: return MahjongRoundInfoPresentationResult.TABLE_NOT_FOUND
        if (table.facing != presentation.tableFacing) {
            return MahjongRoundInfoPresentationResult.TABLE_NOT_FOUND
        }

        val placement = MahjongTileTableLayout.roundInfoDisplayPlacement(controllerPos.x, controllerPos.y, controllerPos.z)

        val existing = findManagedDisplay(world, presentation.tableId, controllerPos)
        if (existing != null) {
            existing.applyPresentation(presentation)
            existing.moveTo(placement.x, placement.y, placement.z, placement.yaw, 0.0f)
        } else {
            val display = MahjongRoundInfoEntity(world = world).apply {
                applyPresentation(presentation)
                moveTo(placement.x, placement.y, placement.z, placement.yaw, 0.0f)
                assignToTable(presentation.tableId)
            }
            if (!spawnGateway.spawn(world, display, "round-info", presentation.tableId)) {
                return MahjongRoundInfoPresentationResult.SPAWN_FAILED
            }
        }
        table.blockEntity.setChanged()
        return MahjongRoundInfoPresentationResult.PRESENTED
    }

    override fun clear(tableId: Uuid, tableLocation: TableLocation): Int {
        val world = resolveWorld(tableLocation) ?: return 0
        val controllerPos = tableLocation.toBlockPos()
        val display = findManagedDisplay(world, tableId, controllerPos) ?: return 0
        display.discard()
        return 1
    }

    /**
     * 把呈現資料的原始數值套到 entity 上——不在這裡格式化成文字，翻譯要交給 client 端 renderer 依各自
     * 語系解析，見 [MahjongRoundInfoEntity] KDoc。
     *
     * @param presentation 局況顯示的原始呈現資料。
     */
    private fun MahjongRoundInfoEntity.applyPresentation(presentation: MahjongRoundInfoPresentation) {
        lines = presentation.lines
    }

    /**
     * 由版本無關 dimension ID 取得目前 server session 的世界。
     *
     * @param location 桌子位置。
     * @return 對應的已載入伺服器世界。無法解析時為 null。
     */
    private fun resolveWorld(location: TableLocation): ServerLevel? {
        val identifier = ResourceLocation.tryParse(location.dimensionId) ?: return null
        val worldKey = ResourceKey.create(Registries.DIMENSION, identifier)
        return serverHolder.current()?.getLevel(worldKey)
    }

    /**
     * 只查詢桌子結構附近並以同步 UUID 精確篩選，避免掃描整個 dimension。
     *
     * @param world 要搜尋的伺服器世界。
     * @param tableId 桌子 UUID。
     * @param controllerPos controller 方塊位置。
     * @return 第一個符合桌子 UUID 的局況顯示 entity。不存在時為 null。
     */
    private fun findManagedDisplay(
        world: ServerLevel,
        tableId: Uuid,
        controllerPos: BlockPos,
    ): MahjongRoundInfoEntity? = world.getEntitiesOfClass(
        MahjongRoundInfoEntity::class.java,
        AABB(controllerPos).inflate(
            TableOverlayEntitySearch.HORIZONTAL_RADIUS,
            TableOverlayEntitySearch.VERTICAL_RADIUS,
            TableOverlayEntitySearch.HORIZONTAL_RADIUS,
        ),
    ) { display -> display.managedTableId == tableId }.firstOrNull()

    /**
     * 將版本無關 table location 轉回 Minecraft 方塊座標。
     *
     * @return 對應的 Minecraft 方塊位置。
     */
    private fun TableLocation.toBlockPos(): BlockPos = BlockPos(x, y, z)
}
