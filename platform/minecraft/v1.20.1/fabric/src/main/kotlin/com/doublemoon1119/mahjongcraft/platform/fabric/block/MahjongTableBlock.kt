package com.doublemoon1119.mahjongcraft.platform.fabric.block

import com.doublemoon1119.mahjongcraft.platform.fabric.api.table.MahjongTableStructureBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.api.table.MahjongTables
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.SimpleWaterloggedBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * 以底層中央為 controller 的 3×3×2 麻將桌方塊。
 *
 * 所有 parts 共用同一個方塊 registry ID，透過 [PART] 與 [Properties.HORIZONTAL_FACING] 保存結構位置。
 * 只有 [MahjongTablePart.BOTTOM_CENTER] 建立 [MahjongTableBlockEntity]；其餘 parts 的互動與生命週期皆
 * 先反查 controller。每個 part 與原版可含水方塊相同，放置時原位置是水源就含水。
 *
 * @param settings Minecraft 方塊設定。
 * @property design 此方塊 registry ID 對應的固定桌型。
 */
class MahjongTableBlock(
    settings: Properties,
    internal val design: MahjongTableDesign,
) : BaseEntityBlock(settings),
    MahjongTableStructureBlock,
    SimpleWaterloggedBlock {
    /** 記錄結構放置失敗或缺少 controller 等異常狀態。 */
    private val logger = mahjongCraftLogger(MahjongTableBlock::class)

    /** 目前正在由 controller 主動拆除的結構，避免 part callbacks 重複進入 cleanup。 */
    private val removingControllers = mutableSetOf<BlockPos>()

    /** 建立預設為朝北的 controller blockstate。 */
    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(PART, MahjongTablePart.BOTTOM_CENTER)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.WATERLOGGED, false),
        )
    }

    /** 只為 controller part 建立保存穩定 UUID 的方塊實體。 */
    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? = if (state.getValue(PART) == MahjongTablePart.BOTTOM_CENTER) MahjongTableBlockEntity(pos, state) else null

    /** 使用普通 block model 顯示目前的多方塊佔位外觀。 */
    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    /** 依桌型、part 與朝向回傳 server 權威碰撞。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getCollisionShape(
        state: BlockState,
        world: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = design.collisionShape(state.getValue(PART), state.getValue(BlockStateProperties.HORIZONTAL_FACING))

    /** 回傳主要幾何的選取框；無碰撞的上層中央仍可被選取並轉交互動。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getShape(
        state: BlockState,
        world: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = design.outlineShape(state.getValue(PART), state.getValue(BlockStateProperties.HORIZONTAL_FACING))

    /** 註冊結構 part、水平朝向與含水 blockstate properties。 */
    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(PART, BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.WATERLOGGED)
    }

    /** 含水時回報水源的流體狀態。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getFluidState(state: BlockState): FluidState = state.waterloggedFluidState()

    /** 含水時排程水流更新，讓周圍的水照常流動。 */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun updateShape(
        state: BlockState,
        direction: Direction,
        neighborState: BlockState,
        world: LevelAccessor,
        pos: BlockPos,
        neighborPos: BlockPos,
    ): BlockState {
        state.scheduleWaterTickIfWaterlogged(world, pos)
        return super.updateShape(state, direction, neighborState, world, pos, neighborPos)
    }

    /**
     * 僅在 18 個目標位置皆可替換、且都沒有實體佔用其碰撞範圍時允許放置 controller。
     *
     * 沒有碰撞的 part（例如 [MahjongTablePart.TOP_CENTER]）交給 [net.minecraft.world.level.Level.isUnobstructed]
     * 自然放行——沒有形狀就不會與任何實體衝突，跟站在地毯／絆線上面同一套邏輯。
     */
    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val controllerPos = context.clickedPos
        val facing = context.horizontalDirection.opposite
        val canPlace = MahjongTableStructure.placements(controllerPos, facing).all { (part, pos) ->
            val replaceable = part == MahjongTablePart.BOTTOM_CENTER || context.level.getBlockState(pos).canBeReplaced()
            val candidateState = defaultBlockState().setValue(PART, part).setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
            replaceable && context.level.isUnobstructed(candidateState, pos, CollisionContext.empty())
        }
        return if (canPlace) {
            defaultBlockState()
                .setValue(PART, MahjongTablePart.BOTTOM_CENTER)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                .waterloggedAt(context.level, controllerPos)
        } else {
            null
        }
    }

    /** controller 放置後建立其餘 17 個 parts，各自依原位置決定是否含水；中途失敗時回滾本次結構。 */
    override fun setPlacedBy(
        world: Level,
        pos: BlockPos,
        state: BlockState,
        placer: LivingEntity?,
        itemStack: ItemStack,
    ) {
        super.setPlacedBy(world, pos, state, placer, itemStack)
        if (world.isClientSide || state.getValue(PART) != MahjongTablePart.BOTTOM_CENTER) return

        val facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING)
        val placedParts = mutableListOf<BlockPos>()
        val completed = MahjongTableStructure.placements(pos, facing)
            .filterKeys { part -> part != MahjongTablePart.BOTTOM_CENTER }
            .all { (part, partPos) ->
                world.setBlock(
                    partPos,
                    defaultBlockState().setValue(PART, part).setValue(BlockStateProperties.HORIZONTAL_FACING, facing).waterloggedAt(world, partPos),
                    UPDATE_ALL,
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
    override fun onRemove(
        state: BlockState,
        world: Level,
        pos: BlockPos,
        newState: BlockState,
        moved: Boolean,
    ) {
        if (state.block !== newState.block && world is ServerLevel) removeStructure(world, pos, state)
        super.onRemove(state, world, pos, newState, moved)
    }

    /** 在伺服器端把任意 part 的右鍵交給 controller；蹲下與否都只開啟 RoomScreen。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun use(
        state: BlockState,
        world: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult,
    ): InteractionResult {
        if (!world.isClientSide) {
            val controllerPos = mahjongTableControllerPos(world, pos, state) ?: return InteractionResult.FAIL
            if (!isComplete(world, controllerPos, world.getBlockState(controllerPos))) {
                logger.warn("Rejected interaction with incomplete Mahjong table at {}", controllerPos)
                return InteractionResult.FAIL
            }
        }
        return MahjongTables.interact(world, pos, state, player)
    }

    /**
     * 由任意 part 的狀態與座標換算 controller 位置。
     *
     * @param world 方塊所在世界。
     * @param pos 任意結構 part 的座標。
     * @param state 該座標目前的方塊狀態。
     * @return 依 part 與朝向換算的 controller 位置；方塊不是此桌時為 null。
     */
    override fun mahjongTableControllerPos(
        world: BlockGetter,
        pos: BlockPos,
        state: BlockState,
    ): BlockPos? {
        if (state.block !== this) return null
        return MahjongTableStructure.controllerPosition(pos, state.getValue(PART), state.getValue(BlockStateProperties.HORIZONTAL_FACING))
    }

    /**
     * 確認 controller 對應的 18 個位置都具有相同朝向及預期 part。
     *
     * @param world 查詢方塊狀態的世界存取介面。
     * @param controllerPos controller 座標。
     * @param controllerState controller 目前的方塊狀態。
     * @return 結構完整且每個 part 的種類與朝向吻合時為 true。
     */
    internal fun isComplete(world: LevelAccessor, controllerPos: BlockPos, controllerState: BlockState): Boolean {
        if (controllerState.block !== this || controllerState.getValue(PART) != MahjongTablePart.BOTTOM_CENTER) return false
        val facing = controllerState.getValue(BlockStateProperties.HORIZONTAL_FACING)
        return MahjongTableStructure.placements(controllerPos, facing).all { (expectedPart, partPos) ->
            val partState = world.getBlockState(partPos)
            partState.block === this &&
                partState.getValue(PART) == expectedPart &&
                partState.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing
        }
    }

    /**
     * 從任意被替換 part 定位 controller，執行資料 cleanup 並移除完整結構。
     *
     * @param world 伺服器世界。
     * @param pos 被替換 part 的座標。
     * @param state 被替換 part 的方塊狀態。
     */
    private fun removeStructure(world: ServerLevel, pos: BlockPos, state: BlockState) {
        val facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING)
        val controllerPos = MahjongTableStructure.controllerPosition(pos, state.getValue(PART), facing).immutable()
        if (!removingControllers.add(controllerPos)) return

        try {
            MahjongTables.onControllerRemoved(world, controllerPos)
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
        val PART: EnumProperty<MahjongTablePart> = EnumProperty.create("part", MahjongTablePart::class.java)
    }
}
