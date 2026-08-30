package com.doublemoon1119.mahjongcraft.platform.fabric.block

import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.MahjongTableRoomService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableLifecycleService
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import net.minecraft.block.Block
import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockState
import net.minecraft.block.BlockWithEntity
import net.minecraft.block.ShapeContext
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.StateManager
import net.minecraft.state.property.EnumProperty
import net.minecraft.state.property.Properties
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView
import net.minecraft.world.World
import net.minecraft.world.WorldAccess
import org.slf4j.LoggerFactory

/**
 * 以底層中央為 controller 的 3×3×2 麻將桌方塊。
 *
 * 所有 parts 共用同一個方塊 registry ID，透過 [PART] 與 [Properties.HORIZONTAL_FACING] 保存結構位置。
 * 只有 [MahjongTablePart.BOTTOM_CENTER] 建立 [MahjongTableBlockEntity]；其餘 parts 的互動與生命週期皆
 * 先反查 controller。
 *
 * @property design 此方塊 registry ID 對應的固定桌型。
 */
class MahjongTableBlock(
    settings: Settings,
    internal val design: MahjongTableDesign,
    private val roomService: MahjongTableRoomService,
    private val tableLifecycleService: FabricTableLifecycleService,
) : BlockWithEntity(settings) {
    /** 記錄結構放置失敗或缺少 controller 等異常狀態。 */
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    /** 目前正在由 controller 主動拆除的結構，避免 part callbacks 重複進入 cleanup。 */
    private val removingControllers = mutableSetOf<BlockPos>()

    /** 建立預設為朝北的 controller blockstate。 */
    init {
        defaultState = stateManager.defaultState
            .with(PART, MahjongTablePart.BOTTOM_CENTER)
            .with(Properties.HORIZONTAL_FACING, Direction.NORTH)
    }

    /** 只為 controller part 建立保存穩定 UUID 的方塊實體。 */
    override fun createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? = if (state.get(PART) == MahjongTablePart.BOTTOM_CENTER) MahjongTableBlockEntity(pos, state) else null

    /** 使用普通 block model 顯示目前的多方塊佔位外觀。 */
    override fun getRenderType(state: BlockState): BlockRenderType = BlockRenderType.MODEL

    /** 依桌型、part 與朝向回傳 server 權威碰撞。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getCollisionShape(
        state: BlockState,
        world: BlockView,
        pos: BlockPos,
        context: ShapeContext,
    ): VoxelShape = design.collisionShape(state.get(PART), state.get(Properties.HORIZONTAL_FACING))

    /** 回傳主要幾何的選取框；無碰撞的上層中央仍可被選取並轉交互動。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOutlineShape(
        state: BlockState,
        world: BlockView,
        pos: BlockPos,
        context: ShapeContext,
    ): VoxelShape = design.outlineShape(state.get(PART), state.get(Properties.HORIZONTAL_FACING))

    /** 註冊結構 part 與水平朝向 blockstate properties。 */
    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(PART, Properties.HORIZONTAL_FACING)
    }

    /**
     * 僅在 18 個目標位置皆可替換、且都沒有實體佔用其碰撞範圍時允許放置 controller。
     *
     * 沒有碰撞的 part（例如 [MahjongTablePart.TOP_CENTER]）交給 [net.minecraft.world.World.canPlace]
     * 自然放行——沒有形狀就不會與任何實體衝突，跟站在地毯／絆線上面同一套邏輯。
     */
    override fun getPlacementState(context: ItemPlacementContext): BlockState? {
        val controllerPos = context.blockPos
        val facing = context.horizontalPlayerFacing.opposite
        val canPlace = MahjongTableStructure.placements(controllerPos, facing).all { (part, pos) ->
            val replaceable = part == MahjongTablePart.BOTTOM_CENTER || context.world.getBlockState(pos).isReplaceable
            val candidateState = defaultState.with(PART, part).with(Properties.HORIZONTAL_FACING, facing)
            replaceable && context.world.canPlace(candidateState, pos, ShapeContext.absent())
        }
        return if (canPlace) {
            defaultState
                .with(PART, MahjongTablePart.BOTTOM_CENTER)
                .with(Properties.HORIZONTAL_FACING, facing)
        } else {
            null
        }
    }

    /** controller 放置後建立其餘 17 個 parts；中途失敗時回滾本次結構。 */
    override fun onPlaced(
        world: World,
        pos: BlockPos,
        state: BlockState,
        placer: LivingEntity?,
        itemStack: ItemStack,
    ) {
        super.onPlaced(world, pos, state, placer, itemStack)
        if (world.isClient || state.get(PART) != MahjongTablePart.BOTTOM_CENTER) return

        val facing = state.get(Properties.HORIZONTAL_FACING)
        val placedParts = mutableListOf<BlockPos>()
        val completed = MahjongTableStructure.placements(pos, facing)
            .filterKeys { part -> part != MahjongTablePart.BOTTOM_CENTER }
            .all { (part, partPos) ->
                world.setBlockState(
                    partPos,
                    defaultState.with(PART, part).with(Properties.HORIZONTAL_FACING, facing),
                    NOTIFY_ALL,
                ).also { placed -> if (placed) placedParts.add(partPos) }
            }
        if (!completed) {
            placedParts.forEach { placedPos -> world.removeBlock(placedPos, false) }
            world.removeBlock(pos, false)
            logger.warn("Rolled back incomplete Mahjong table placement at {}", pos)
        }
    }

    /** 任意 part 被替換時，從 controller 執行一次 cleanup 並移除仍存在的其餘 parts。 */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onStateReplaced(
        state: BlockState,
        world: World,
        pos: BlockPos,
        newState: BlockState,
        moved: Boolean,
    ) {
        if (state.block !== newState.block && world is ServerWorld) removeStructure(world, pos, state)
        super.onStateReplaced(state, world, pos, newState, moved)
    }

    /** 在伺服器端把任意 part 的右鍵交給 controller；蹲下與否都只開啟 RoomScreen。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onUse(
        state: BlockState,
        world: World,
        pos: BlockPos,
        player: PlayerEntity,
        hand: Hand,
        hit: BlockHitResult,
    ): ActionResult {
        if (!world.isClient) {
            val table = resolveController(world, pos, state) ?: return ActionResult.FAIL
            if (!isComplete(world, table.pos, table.cachedState)) {
                logger.warn("Rejected interaction with incomplete Mahjong table {} at {}", table.tableId, table.pos)
                return ActionResult.FAIL
            }
            val serverPlayer = player as ServerPlayerEntity
            roomService.openRoomScreen(table, serverPlayer)
        }
        return ActionResult.SUCCESS
    }

    /** 由任意 part 的狀態與座標取得 controller 方塊實體。 */
    internal fun resolveController(world: WorldAccess, pos: BlockPos, state: BlockState): MahjongTableBlockEntity? {
        if (state.block !== this) return null
        val controllerPos = MahjongTableStructure.controllerPosition(
            pos,
            state.get(PART),
            state.get(Properties.HORIZONTAL_FACING),
        )
        val table = world.getBlockEntity(controllerPos) as? MahjongTableBlockEntity ?: return null
        return table.takeIf { controller -> controller.cachedState.block === this }
    }

    /** 確認 controller 對應的 18 個位置都具有相同朝向及預期 part。 */
    internal fun isComplete(world: WorldAccess, controllerPos: BlockPos, controllerState: BlockState): Boolean {
        if (controllerState.block !== this || controllerState.get(PART) != MahjongTablePart.BOTTOM_CENTER) return false
        val facing = controllerState.get(Properties.HORIZONTAL_FACING)
        return MahjongTableStructure.placements(controllerPos, facing).all { (expectedPart, partPos) ->
            val partState = world.getBlockState(partPos)
            partState.block === this &&
                partState.get(PART) == expectedPart &&
                partState.get(Properties.HORIZONTAL_FACING) == facing
        }
    }

    /** 從任意被替換 part 定位 controller，執行資料 cleanup 並移除完整結構。 */
    private fun removeStructure(world: ServerWorld, pos: BlockPos, state: BlockState) {
        val facing = state.get(Properties.HORIZONTAL_FACING)
        val controllerPos = MahjongTableStructure.controllerPosition(pos, state.get(PART), facing).toImmutable()
        if (!removingControllers.add(controllerPos)) return

        try {
            val table = world.getBlockEntity(controllerPos) as? MahjongTableBlockEntity
            if (table == null) {
                logger.warn("Removed incomplete Mahjong table part at {} without controller at {}", pos, controllerPos)
            } else {
                tableLifecycleService.onBlockReplaced(world, table)
            }
            MahjongTableStructure.placements(controllerPos, facing).values.forEach { partPos ->
                if (partPos != pos && world.getBlockState(partPos).block === this) world.removeBlock(partPos, false)
            }
        } finally {
            removingControllers.remove(controllerPos)
        }
    }

    /** 麻將桌 blockstate properties。 */
    companion object {
        /** 目前方塊在 3×3×2 結構中的位置。 */
        val PART: EnumProperty<MahjongTablePart> = EnumProperty.of("part", MahjongTablePart::class.java)
    }
}
