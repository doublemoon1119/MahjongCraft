package doublemoon.mahjongcraft.entity

import doublemoon.mahjongcraft.registry.EntityTypeRegistry
import doublemoon.mahjongcraft.registry.ItemRegistry
import net.minecraft.entity.EntityType
import net.minecraft.entity.MovementType
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundEvents
import net.minecraft.tag.FluidTags
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import kotlin.math.min

/**
 * 骰子實體
 * */
class DiceEntity(
    type: EntityType<DiceEntity> = EntityTypeRegistry.dice,
    world: World,
    pos: Vec3d? = null,
    yaw: Float? = null
) : GameEntity(type, world) {

    /**
     * 骰子當前的點數 (與擺的方向有關)
     * */
    var point: DicePoint
        set(value) = dataTracker.set(POINT, value.name)
        get() = DicePoint.valueOf(dataTracker[POINT])

    val rolling: Boolean
        get() = !isOnGround

    init {
        pos?.let { setPosition(it.x, it.y, it.z) }
        yaw?.let { setRotation(it, 0f) }
    }

    override fun interact(player: PlayerEntity, hand: Hand): ActionResult {
        if (!player.world.isClient) {
            player as ServerPlayerEntity
            if (!isSpawnedByGame && player.isSneaking) { //不是由遊戲產生, 是手動放置的一般實體
                //蹲下才能把骰子撿起來
                player.giveItemStack(ItemRegistry.dice.defaultStack)
                playSound(SoundEvents.ENTITY_ITEM_PICKUP, 1f, 1f)
                remove()
            }
        }
        return ActionResult.SUCCESS
    }

    override fun tick() {
        super.tick()
        prevX = x
        prevY = y
        prevZ = z
        if (isSubmergedIn(FluidTags.WATER)) {
            setUnderwaterMovement()
        } else if (!this.hasNoGravity()) {
            velocity = velocity.add(0.0, -0.03, 0.0)
        }
        if (world.getFluidState(blockPos).isIn(FluidTags.LAVA)) {
            setVelocity(
                random.nextDouble() - random.nextDouble() * 0.2,
                0.2,
                random.nextDouble() - random.nextDouble() * 0.2
            )
            playSound(SoundEvents.ENTITY_GENERIC_BURN, 0.4f, 2.0f + random.nextFloat() * 0.4f)
        }
        if (!world.isSpaceEmpty(boundingBox)) {
            pushOutOfBlocks(x, (boundingBox.minY + boundingBox.maxY) / 2.0, z)
        }
        move(MovementType.SELF, velocity)
        var f = 0.98f
        if (onGround) {
            val pos = BlockPos(x, y - 1.0, z)
            f = world.getBlockState(pos).block.slipperiness * 0.98f
        }
        velocity = velocity.multiply(f.toDouble(), 0.98, f.toDouble())
        if (onGround) {
            velocity = velocity.multiply(1.0, -0.9, 1.0)
        }
        ++age
        if (rolling) { //骰子在滾動
            point = DicePoint.random()
        }
    }

    private fun setUnderwaterMovement() {
        val vec3d = velocity
        setVelocity(
            vec3d.x * 0.99,
            min(vec3d.y + 5.0E-4, 0.06),
            vec3d.z * 0.99
        )
    }

    override fun initDataTracker() {
        super.initDataTracker()
        dataTracker.startTracking(POINT, DicePoint.random().name)
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        super.readCustomDataFromNbt(nbt)
        val pointStr = nbt.get("Point")?.asString() ?: DicePoint.random().name //避免指令召喚的骰子沒有 nbt 導致無法召喚
        point = DicePoint.valueOf(pointStr)
    }

    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        super.writeCustomDataToNbt(nbt)
        nbt.putString("Point", point.name)
    }

    companion object {
        //麻將牌的寬高深和比例
        const val DICE_SCALE = 0.2f
        const val DICE_WIDTH = 1f / 16 * 10 * DICE_SCALE
        const val DICE_HEIGHT = 1f / 16 * 10 * DICE_SCALE

        val POINT: TrackedData<String> =
            DataTracker.registerData(DiceEntity::class.java, TrackedDataHandlerRegistry.STRING)
    }
}

enum class DicePoint(
    val xpRotDegrees: Float,
    val ypRotDegrees: Float,
    val value: Int
) {
    ONE(0f, 0f, 1),
    TWO(90f, 0f, 2),
    THREE(90f, 90f, 3),
    FOUR(-90f, 0f, 4),
    FIVE(90f, -90f, 5),
    SIX(-180f, 0f, 6);

    fun next(): DicePoint = values()[(ordinal + 1) % values().size]

    companion object {
        fun random(): DicePoint = values()[(0..values().lastIndex).random()]
    }
}
