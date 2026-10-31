package com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop

import com.doublemoon1119.mahjongcraft.platform.fabric.api.tableprop.FabricTablePropKind
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableLookup
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.FabricEntitySpawnGateway
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.TablePropRefreshQueue
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropLayoutResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropTarget
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.intersectsTablePropCenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.updateTablePropScene
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import kotlinx.coroutines.CancellationException
import net.minecraft.entity.Entity
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import kotlin.uuid.Uuid

/**
 * 使用 Fabric 1.20.1 entity 差量同步規則桌面物件；每個物件由 [FabricTablePropKindRegistry] 裡對應的種類
 * 負責生成與辨識。
 *
 * 內容或幾何無法完整呈現時清除失效顯示並回報，不留下部分成功或過時的規則物件。
 *
 * @property serverHolder 取得目前伺服器世界。
 * @property spawnGateway 透過平台入口生成桌面物件 entity。
 * @property refreshQueue 保存實體尚未載入時延後重新呈現。
 * @property kindRegistry 查詢規則桌面物件種類實作。
 */
class FabricTablePropPresenter(
    private val serverHolder: FabricServerHolder,
    private val spawnGateway: FabricEntitySpawnGateway,
    private val kindRegistry: FabricTablePropKindRegistry,
    private val refreshQueue: TablePropRefreshQueue,
) : TablePropPresenter {
    /** 記錄種類、幾何與生成失敗的診斷。 */
    private val logger = mahjongCraftLogger(FabricTablePropPresenter::class)

    /** 擲骰對中央區域的暫時保留，世代由開局呈現流程提供。 */
    private val centerReservations = mutableMapOf<Uuid, Long>()

    /** 最近一次有效描述，供擲骰開始時立即移除已占用中央的物件。 */
    private val scenes = mutableMapOf<Uuid, TablePropPresentation>()

    /** 每桌最近一次顯示的錯誤，避免相同錯誤在每次更新時洗版。 */
    private val notifiedFailures = mutableMapOf<Uuid, String>()

    override fun resetSession() {
        scenes.clear()
        centerReservations.clear()
        notifiedFailures.clear()
    }

    override fun reserveCenter(tableId: Uuid, generation: Long) {
        centerReservations[tableId] = generation
        val scene = scenes[tableId] ?: return
        val world = resolveWorld(scene.tableLocation) ?: return
        findManagedProps(world, tableId, scene.tableLocation.toBlockPos()).forEach { prop ->
            val target = prop.target() ?: return@forEach
            val dimensions = kindRegistry.definitions[target.kind]?.dimensions ?: return@forEach
            if (intersectsTablePropCenter(scene.tableLocation, target, dimensions)) prop.entity.discard()
        }
    }

    override fun releaseCenter(tableId: Uuid, generation: Long): Boolean {
        if (centerReservations[tableId] != generation) return false
        centerReservations.remove(tableId)
        return true
    }

    /**
     * 驗證完整場景後一次套用；任一步驟失敗時移除失效顯示，不影響權威對局。
     *
     * @param presentation 桌子與目標桌面物件的呈現資料。
     * @return 完整呈現、桌子不存在或呈現失敗的結果。
     */
    override fun present(presentation: TablePropPresentation): TablePropPresentationResult {
        val world = resolveWorld(presentation.tableLocation) ?: return TablePropPresentationResult.TABLE_NOT_FOUND
        val controllerPos = presentation.tableLocation.toBlockPos()
        if (!FabricTablePropSearchArea.entitiesLoaded(world, controllerPos)) {
            refreshQueue.enqueue(world.server, presentation.tableId)
            return TablePropPresentationResult.WAITING_FOR_CHUNKS
        }
        val table = MahjongTableLookup.find(world, controllerPos, presentation.tableId)
            ?: return TablePropPresentationResult.TABLE_NOT_FOUND
        if (table.facing != presentation.tableFacing) {
            return TablePropPresentationResult.TABLE_NOT_FOUND
        }
        var existing = emptyList<ManagedProp>()
        val spawned = mutableListOf<Entity>()
        val preparedKinds = mutableMapOf<Entity, FabricTablePropKind>()
        try {
            existing = findManagedProps(world, presentation.tableId, controllerPos)
            scenes[presentation.tableId] = presentation
            val layout = presentation.targets(kindRegistry.definitions)
            if (layout is TablePropLayoutResult.Invalid) {
                return fail(presentation, world, existing, spawned, "${layout.reason}: ${layout.diagnostic}")
            }
            val plannedTargets = (layout as TablePropLayoutResult.Success).targets
            if (plannedTargets.any { !FabricTablePropSearchArea.contains(controllerPos, it.x, it.y, it.z) }) {
                return fail(presentation, world, existing, spawned, "Table prop is outside the supported search area")
            }
            val targets = plannedTargets.filterNot { target ->
                centerReservations.containsKey(presentation.tableId) && intersectsTablePropCenter(presentation.tableLocation, target, kindRegistry.definitions.getValue(target.kind).dimensions)
            }
            val applied = updateTablePropScene(
                targets = targets,
                existing = existing.map { it.entity },
                targetOf = { entity -> existing.first { it.entity === entity }.target() },
                create = { target ->
                    val kind = checkNotNull(kindRegistry.find(target.kind))
                    kind.create(world, presentation.tableId, target)?.also {
                        spawned += it
                        preparedKinds[it] = kind
                    }
                },
                spawn = { spawnGateway.spawn(world, it, "table-prop", presentation.tableId) },
                remove = Entity::discard,
                onSpawned = { entity ->
                    preparedKinds.getValue(entity).onSpawned(entity)
                },
            )
            if (!applied) return fail(presentation, world, existing, spawned, "Entity creation or spawn rejected")
            notifiedFailures.remove(presentation.tableId)
            table.blockEntity.markDirty()
            return TablePropPresentationResult.PRESENTED
        } catch (cancelled: CancellationException) {
            existing.forEach { it.entity.discard() }
            spawned.forEach(Entity::discard)
            throw cancelled
        } catch (failure: Exception) {
            logger.warn("Table prop presentation failed for table {}", presentation.tableId, failure)
            return fail(presentation, world, existing, spawned, failure.message ?: failure.javaClass.simpleName)
        } catch (failure: LinkageError) {
            logger.warn("Table prop extension linkage failed for table {}", presentation.tableId, failure)
            return fail(presentation, world, existing, spawned, failure.javaClass.simpleName)
        }
    }

    /**
     * 清除無法代表最新桌況的整批顯示，並向附近玩家提示一次。
     *
     * @param presentation 失敗的桌面呈現。
     * @param world 桌子所在世界。
     * @param existing 原有的規則物件。
     * @param spawned 本次已生成的規則物件。
     * @param diagnostic 給管理員查詢的診斷。
     * @return 呈現失敗結果。
     */
    private fun fail(
        presentation: TablePropPresentation,
        world: ServerWorld,
        existing: List<ManagedProp>,
        spawned: List<Entity>,
        diagnostic: String,
    ): TablePropPresentationResult {
        existing.forEach { it.entity.discard() }
        spawned.forEach(Entity::discard)
        val previousFailure = notifiedFailures.put(presentation.tableId, diagnostic)
        if (previousFailure != diagnostic) logger.warn("Cleared rule props for table {} rule {}: {}", presentation.tableId, presentation.ruleModuleId, diagnostic)
        if (previousFailure == null) {
            world.players.filter { it.squaredDistanceTo(presentation.tableLocation.x.toDouble(), presentation.tableLocation.y.toDouble(), presentation.tableLocation.z.toDouble()) <= 32.0 * 32.0 }.forEach {
                it.sendMessage(Text.translatable(MinecraftMessageKeys.TABLE_RULE_PROPS_UNAVAILABLE).formatted(Formatting.YELLOW), false)
            }
        }
        return TablePropPresentationResult.SPAWN_FAILED
    }

    /**
     * 清除指定 controller 周圍、由這張桌子管理的全部規則桌面物件。
     *
     * @param tableId 桌子 UUID。
     * @param tableLocation 桌子位置。
     * @return 被移除的桌面物件數量。
     */
    override fun clear(tableId: Uuid, tableLocation: TableLocation): Int {
        centerReservations.remove(tableId)
        notifiedFailures.remove(tableId)
        scenes.remove(tableId)
        val world = resolveWorld(tableLocation) ?: return 0
        val props = findManagedProps(world, tableId, tableLocation.toBlockPos())
        props.forEach { prop -> prop.entity.discard() }
        return props.size
    }

    /**
     * 查詢每個已登記種類在桌子結構附近、由這張桌子管理的 entity。
     *
     * @param world 桌子所在伺服器世界。
     * @param tableId 桌子 UUID。
     * @param controllerPos controller 方塊位置。
     * @return entity 與負責種類配對的清單。
     */
    private fun findManagedProps(world: ServerWorld, tableId: Uuid, controllerPos: BlockPos): List<ManagedProp> {
        val searchBox = FabricTablePropSearchArea.box(controllerPos)
        return kindRegistry.kinds.flatMap { kind ->
            kind.findManaged(world, tableId, searchBox).map { entity -> ManagedProp(kind, entity) }
        }
    }

    /**
     * 由版本無關 dimension ID 取得目前 server session 的世界。
     *
     * @param location 桌子位置。
     * @return 對應的已載入伺服器世界；無法解析時為 null。
     */
    private fun resolveWorld(location: TableLocation): ServerWorld? {
        val identifier = Identifier.tryParse(location.dimensionId) ?: return null
        val worldKey = RegistryKey.of(RegistryKeys.WORLD, identifier)
        return serverHolder.current()?.getWorld(worldKey)
    }

    /**
     * 將版本無關 table location 轉回 Fabric 方塊座標。
     *
     * @return 對應的 Minecraft 方塊位置。
     */
    private fun TableLocation.toBlockPos(): BlockPos = BlockPos(x, y, z)

    /**
     * 桌上一個既有的規則桌面物件與負責它的種類。
     *
     * @property kind 解讀變體及搜尋該 entity 的桌面物件種類。
     * @property entity 桌上已存在的 Minecraft entity。
     */
    private class ManagedProp(
        val kind: FabricTablePropKind,
        val entity: Entity,
    ) {
        /**
         * 讀出目前的身分與落點；種類認不得變體時為 null。
         *
         * @return entity 對應的桌面物件目標；無法辨識變體時為 null。
         */
        fun target(): TablePropTarget? {
            val id = kind.placementIdOf(entity) ?: return null
            val ruleModuleId = kind.ruleModuleIdOf(entity) ?: return null
            val content = kind.contentOf(entity) ?: return null
            return TablePropTarget(
                id = id,
                ruleModuleId = ruleModuleId,
                kind = kind.id,
                content = content,
                x = entity.x,
                y = entity.y,
                z = entity.z,
                yaw = entity.yaw,
            )
        }
    }
}
