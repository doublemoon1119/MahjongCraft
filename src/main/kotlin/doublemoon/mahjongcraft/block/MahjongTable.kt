package doublemoon.mahjongcraft.block

import doublemoon.mahjongcraft.MOD_ID
import doublemoon.mahjongcraft.block.enums.MahjongTablePart
import doublemoon.mahjongcraft.blockentity.MahjongTableBlockEntity
import doublemoon.mahjongcraft.game.GameManager
import doublemoon.mahjongcraft.game.GameStatus
import doublemoon.mahjongcraft.game.mahjong.riichi.MahjongGame
import doublemoon.mahjongcraft.game.mahjong.riichi.model.MahjongTableBehavior
import doublemoon.mahjongcraft.network.MahjongTablePacketListener.sendMahjongTablePacket
import doublemoon.mahjongcraft.registry.BlockEntityTypeRegistry
import doublemoon.mahjongcraft.util.boxBySize
import doublemoon.mahjongcraft.util.plus
import net.minecraft.block.*
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.BlockEntityTicker
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.block.piston.PistonBehavior
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.StateManager
import net.minecraft.state.property.EnumProperty
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Formatting
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.BlockView
import net.minecraft.world.World

/**
 * 參考自 [BedBlock]
 * */
class MahjongTable(settings: Settings) : BlockWithEntity(settings) {

    init {
        defaultState = stateManager.defaultState.with(PART, MahjongTablePart.BOTTOM_CENTER)
    }

    /**
     * 只有 blockstate 的 [PART] 為 [MahjongTablePart.BOTTOM_CENTER] 那個方塊才會渲染
     * */
    override fun getRenderType(state: BlockState): BlockRenderType =
        if (state[PART] == MahjongTablePart.BOTTOM_CENTER) BlockRenderType.MODEL else BlockRenderType.INVISIBLE

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState? {
        val centerPos = ctx.blockPos
        val canPlace = peripheryPos(centerPos).all { ctx.world.getBlockState(it.second).canReplace(ctx) }
        return if (canPlace) defaultState else null
    }

    override fun onPlaced(
        world: World,
        pos: BlockPos,
        state: BlockState,
        placer: LivingEntity?,
        itemStack: ItemStack,
    ) {
        super.onPlaced(world, pos, state, placer, itemStack)
        if (!world.isClient) {
            peripheryPos(pos).forEach { (part, position) -> world.setBlockState(position, state.with(PART, part)) }
            world.updateNeighbors(pos, Blocks.AIR)
            state.updateNeighbors(world, pos, 3)
        }
    }

    override fun onBreak(world: World, pos: BlockPos, state: BlockState, player: PlayerEntity) {
        if (!world.isClient) {
            val part = state[PART] ?: return super.onBreak(world, pos, state, player)
            val centerPos = getCenterPosByPart(pos, part)
            //確保打掉桌子的其中一格方塊就能拆掉整張桌子
            allPos(centerPos).forEach { blockPos ->
                val blockState = world.getBlockState(blockPos)
                if (blockState.block != this) return@forEach
                val blockPart = blockState[PART] ?: return@forEach
                if (blockPart == MahjongTablePart.BOTTOM_CENTER) {  //center 被破壞時會離開遊戲
                    GameManager.getGame<MahjongGame>(world = world as ServerWorld, pos = centerPos)?.onBreak()
                }
                if (blockPart == MahjongTablePart.BOTTOM_CENTER && !player.isCreative) {  //讓玩家在非創造破壞時, 讓中間的桌子掉落
                    world.breakBlock(blockPos, true)
                } else {
                    world.breakBlock(blockPos, false)
                }
            }
        }
        super.onBreak(world, pos, state, player)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(PART)
    }

    override fun onUse(
        state: BlockState,
        world: World,
        pos: BlockPos,
        player: PlayerEntity,
        hand: Hand,
        hit: BlockHitResult,
    ): ActionResult {
        if (hand === Hand.MAIN_HAND && !world.isClient) {
            val centerPos = getCenterPosByPart(pos = pos, part = state[PART])
            player as ServerPlayerEntity
            world as ServerWorld
            val game = GameManager.getGame<MahjongGame>(world = world, pos = centerPos) ?: return ActionResult.CONSUME
            if (!GameManager.isInAnyGame(player) || game.isInGame(player)) {
                //如果玩家沒有在任何遊戲中, 或者玩家在"這個"遊戲中
                if (game.status == GameStatus.PLAYING) { //如果遊戲已經開始,傳訊息->遊戲已經開始了
                    player.sendMessage(
                        Text.translatable("$MOD_ID.game.message.already_started").formatted(Formatting.YELLOW),
                        true
                    )
                } else {  //如果遊戲尚未開始,開啟 gui
                    player.sendMahjongTablePacket( //讓玩家開啟麻將桌 gui
                        behavior = MahjongTableBehavior.OPEN_TABLE_WAITING_GUI,
                        pos = centerPos
                    )
                }
            } else {  //玩家已經在某個遊戲中, 且不是"這個"遊戲
                val message = Text.translatable("$MOD_ID.game.message.already_in_a_game")
                    .formatted(Formatting.YELLOW) + " (" + game.name + ")"
                player.sendMessage(message, true)
            }
        }
        return ActionResult.SUCCESS
    }

    override fun createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? =
        if (state[PART] == MahjongTablePart.BOTTOM_CENTER) MahjongTableBlockEntity(pos, state) else null

    override fun <T : BlockEntity?> getTicker(
        world: World,
        state: BlockState,
        type: BlockEntityType<T>,
    ): BlockEntityTicker<T>? = checkType(
        type,
        BlockEntityTypeRegistry.mahjongTable
    ) { world1, pos, _, blockEntity -> MahjongTableBlockEntity.tick(world1, pos, blockEntity) }

    override fun getPistonBehavior(state: BlockState): PistonBehavior = PistonBehavior.IGNORE

    override fun getOutlineShape(
        state: BlockState,
        world: BlockView,
        pos: BlockPos,
        context: ShapeContext,
    ): VoxelShape = when (state[PART] ?: MahjongTablePart.BOTTOM_CENTER) {
        MahjongTablePart.BOTTOM_CENTER -> BOTTOM_CENTER_SHAPE
        MahjongTablePart.BOTTOM_EAST -> BOTTOM_EAST_SHAPE
        MahjongTablePart.BOTTOM_WEST -> BOTTOM_WEST_SHAPE
        MahjongTablePart.BOTTOM_SOUTH -> BOTTOM_SOUTH_SHAPE
        MahjongTablePart.BOTTOM_NORTH -> BOTTOM_NORTH_SHAPE
        MahjongTablePart.BOTTOM_SOUTHEAST -> BOTTOM_SOUTHEAST_SHAPE
        MahjongTablePart.BOTTOM_SOUTHWEST -> BOTTOM_SOUTHWEST_SHAPE
        MahjongTablePart.BOTTOM_NORTHEAST -> BOTTOM_NORTHEAST_SHAPE
        MahjongTablePart.BOTTOM_NORTHWEST -> BOTTOM_NORTHWEST_SHAPE
        MahjongTablePart.TOP_CENTER -> TOP_CENTER_SHAPE
        MahjongTablePart.TOP_EAST -> TOP_EAST_SHAPE
        MahjongTablePart.TOP_WEST -> TOP_WEST_SHAPE
        MahjongTablePart.TOP_SOUTH -> TOP_SOUTH_SHAPE
        MahjongTablePart.TOP_NORTH -> TOP_NORTH_SHAPE
        MahjongTablePart.TOP_SOUTHEAST -> TOP_SOUTHEAST_SHAPE
        MahjongTablePart.TOP_SOUTHWEST -> TOP_SOUTHWEST_SHAPE
        MahjongTablePart.TOP_NORTHEAST -> TOP_NORTHEAST_SHAPE
        MahjongTablePart.TOP_NORTHWEST -> TOP_NORTHWEST_SHAPE
    }

    companion object {
        val PART: EnumProperty<MahjongTablePart> = EnumProperty.of("mahjong_table_part", MahjongTablePart::class.java)

        private val BOTTOM_CENTER_SHAPE: VoxelShape

        init { //計算出 BOTTOM_CENTER_SHAPE
            val bottom: VoxelShape = boxBySize(1.0, 0.0, 1.0, 14.0, 2.0, 14.0)
            val pillar: VoxelShape = boxBySize(4.0, 2.0, 4.0, 8.0, 12.0, 8.0)
            val top: VoxelShape = boxBySize(-15.0, 14.0, -15.0, 46.0, 2.0, 46.0)
            val border1: VoxelShape = boxBySize(-16, 14, -15, 1, 3, 47)
            val border2: VoxelShape = boxBySize(-15, 14, 31, 47, 3, 1)
            val border3: VoxelShape = boxBySize(31, 14, -16, 1, 3, 47)
            val border4: VoxelShape = boxBySize(-16, 14, -16, 47, 3, 1)
            BOTTOM_CENTER_SHAPE = VoxelShapes.union(bottom, pillar, top, border1, border2, border3, border4)
        }

        //這裡的位置是指相對於 BOTTOM_CENTER_SHAPE 的
        private val BOTTOM_EAST_SHAPE: VoxelShape = BOTTOM_CENTER_SHAPE.offset(-1.0, 0.0, 0.0)
        private val BOTTOM_WEST_SHAPE: VoxelShape = BOTTOM_CENTER_SHAPE.offset(1.0, 0.0, 0.0)
        private val BOTTOM_SOUTH_SHAPE: VoxelShape = BOTTOM_CENTER_SHAPE.offset(0.0, 0.0, -1.0)
        private val BOTTOM_NORTH_SHAPE: VoxelShape = BOTTOM_CENTER_SHAPE.offset(0.0, 0.0, 1.0)
        private val BOTTOM_SOUTHEAST_SHAPE: VoxelShape = BOTTOM_SOUTH_SHAPE.offset(-1.0, 0.0, 0.0)
        private val BOTTOM_SOUTHWEST_SHAPE: VoxelShape = BOTTOM_SOUTH_SHAPE.offset(1.0, 0.0, 0.0)
        private val BOTTOM_NORTHEAST_SHAPE: VoxelShape = BOTTOM_NORTH_SHAPE.offset(-1.0, 0.0, 0.0)
        private val BOTTOM_NORTHWEST_SHAPE: VoxelShape = BOTTOM_NORTH_SHAPE.offset(1.0, 0.0, 0.0)
        private val TOP_CENTER_SHAPE: VoxelShape = BOTTOM_CENTER_SHAPE.offset(0.0, -1.0, 0.0)
        private val TOP_EAST_SHAPE: VoxelShape = BOTTOM_EAST_SHAPE.offset(0.0, -1.0, 0.0)
        private val TOP_WEST_SHAPE: VoxelShape = BOTTOM_WEST_SHAPE.offset(0.0, -1.0, 0.0)
        private val TOP_SOUTH_SHAPE: VoxelShape = BOTTOM_SOUTH_SHAPE.offset(0.0, -1.0, 0.0)
        private val TOP_NORTH_SHAPE: VoxelShape = BOTTOM_NORTH_SHAPE.offset(0.0, -1.0, 0.0)
        private val TOP_SOUTHEAST_SHAPE: VoxelShape = BOTTOM_SOUTHEAST_SHAPE.offset(0.0, -1.0, 0.0)
        private val TOP_SOUTHWEST_SHAPE: VoxelShape = BOTTOM_SOUTHWEST_SHAPE.offset(0.0, -1.0, 0.0)
        private val TOP_NORTHEAST_SHAPE: VoxelShape = BOTTOM_NORTHEAST_SHAPE.offset(0.0, -1.0, 0.0)
        private val TOP_NORTHWEST_SHAPE: VoxelShape = BOTTOM_NORTHWEST_SHAPE.offset(0.0, -1.0, 0.0)

        /**
         * 取得以 [pos] 為中心, xz 平面周圍不含 [pos] 的 8 格方塊位置 + xz 平面往偏移 +y 的平面 9 個方塊的位置
         * */
        private fun peripheryPos(pos: BlockPos): List<Pair<MahjongTablePart, BlockPos>> = listOf(
            MahjongTablePart.BOTTOM_EAST to pos.east(),
            MahjongTablePart.BOTTOM_WEST to pos.west(),
            MahjongTablePart.BOTTOM_SOUTH to pos.south(),
            MahjongTablePart.BOTTOM_NORTH to pos.north(),
            MahjongTablePart.BOTTOM_NORTHEAST to pos.north().east(),
            MahjongTablePart.BOTTOM_NORTHWEST to pos.north().west(),
            MahjongTablePart.BOTTOM_SOUTHEAST to pos.south().east(),
            MahjongTablePart.BOTTOM_SOUTHWEST to pos.south().west(),
            MahjongTablePart.TOP_CENTER to pos.up(),
            MahjongTablePart.TOP_EAST to pos.east().up(),
            MahjongTablePart.TOP_WEST to pos.west().up(),
            MahjongTablePart.TOP_SOUTH to pos.south().up(),
            MahjongTablePart.TOP_NORTH to pos.north().up(),
            MahjongTablePart.TOP_NORTHEAST to pos.north().east().up(),
            MahjongTablePart.TOP_NORTHWEST to pos.north().west().up(),
            MahjongTablePart.TOP_SOUTHEAST to pos.south().east().up(),
            MahjongTablePart.TOP_SOUTHWEST to pos.south().west().up(),
        )

        /**
         * 取得以 [centerPos] 為中心, xz 平面含 [centerPos] 的 9 格方塊位置
         * */
        private fun allPos(centerPos: BlockPos): List<BlockPos> =
            buildList { add(centerPos); addAll(peripheryPos(centerPos).map { it.second }) }

        /**
         * 以 [pos] 加上 [part] 找到麻將桌中心的位置
         * */
        private fun getCenterPosByPart(pos: BlockPos, part: MahjongTablePart): BlockPos = when (part) {
            MahjongTablePart.BOTTOM_CENTER -> pos
            MahjongTablePart.BOTTOM_EAST -> pos.west()
            MahjongTablePart.BOTTOM_WEST -> pos.east()
            MahjongTablePart.BOTTOM_SOUTH -> pos.north()
            MahjongTablePart.BOTTOM_NORTH -> pos.south()
            MahjongTablePart.BOTTOM_SOUTHEAST -> pos.north().west()
            MahjongTablePart.BOTTOM_SOUTHWEST -> pos.north().east()
            MahjongTablePart.BOTTOM_NORTHEAST -> pos.south().west()
            MahjongTablePart.BOTTOM_NORTHWEST -> pos.south().east()
            MahjongTablePart.TOP_CENTER -> pos.down()
            MahjongTablePart.TOP_EAST -> pos.west().down()
            MahjongTablePart.TOP_WEST -> pos.east().down()
            MahjongTablePart.TOP_SOUTH -> pos.north().down()
            MahjongTablePart.TOP_NORTH -> pos.south().down()
            MahjongTablePart.TOP_SOUTHEAST -> pos.north().west().down()
            MahjongTablePart.TOP_SOUTHWEST -> pos.north().east().down()
            MahjongTablePart.TOP_NORTHEAST -> pos.south().west().down()
            MahjongTablePart.TOP_NORTHWEST -> pos.south().east().down()
        }
    }
}