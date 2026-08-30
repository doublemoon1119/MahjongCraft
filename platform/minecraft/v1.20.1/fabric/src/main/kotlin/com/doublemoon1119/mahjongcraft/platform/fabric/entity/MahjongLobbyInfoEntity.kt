package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import net.minecraft.entity.EntityType
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World
import kotlin.uuid.Uuid

/** 等待中遊戲的三行公開提示；由 [managedTableId] 管理並在所屬桌子失效時自行銷毀。 */
class MahjongLobbyInfoEntity(
    type: EntityType<out MahjongLobbyInfoEntity> = ModEntities.mahjongLobbyInfo,
    world: World,
) : AnimatedMahjongEntity<Nothing>(type, world) {
    var ruleModuleId: String
        get() = dataTracker[RULE_MODULE_ID]
        set(value) = dataTracker.set(RULE_MODULE_ID, value)

    var playerCount: Int
        get() = dataTracker[PLAYER_COUNT]
        set(value) = dataTracker.set(PLAYER_COUNT, value)

    var maximumPlayerCount: Int
        get() = dataTracker[MAXIMUM_PLAYER_COUNT]
        set(value) = dataTracker.set(MAXIMUM_PLAYER_COUNT, value)

    var managedTableId: Uuid?
        get() = dataTracker[MANAGED_TABLE_ID].takeIf(String::isNotBlank)?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        private set(value) = dataTracker.set(MANAGED_TABLE_ID, value?.toString().orEmpty())

    private var controllerPos: BlockPos? = null

    init {
        setNoGravity(true)
    }

    fun assignToTable(tableId: Uuid, controllerPos: BlockPos) {
        check(!world.isClient) { "Managed lobby info must be assigned by the server" }
        managedTableId = tableId
        this.controllerPos = controllerPos.toImmutable()
    }

    override fun tick() {
        super.tick()
        if (world.isClient) return
        if (age % ORPHAN_CHECK_INTERVAL_TICKS != 0) return
        val expectedTableId = managedTableId ?: return discard()
        val table = controllerPos?.let { world.getBlockEntity(it) as? MahjongTableBlockEntity }
        if (table?.tableId != expectedTableId) return discard()
    }

    override fun isCollidable(): Boolean = false
    override fun isPushable(): Boolean = false
    override fun canHit(): Boolean = false

    override fun getVisibilityBoundingBox(): Box = Box(
        x - WIDTH / 2.0,
        y - HEIGHT / 2.0,
        z - WIDTH / 2.0,
        x + WIDTH / 2.0,
        y + HEIGHT / 2.0,
        z + WIDTH / 2.0,
    )

    override fun applyCustomStep(step: Nothing) = error("Lobby info has no custom animation step")
    override fun applyPlayMotion(step: AnimationStep.PlayMotion, startGameTime: Long) = error("Lobby info does not support motion")
    override fun serializeCustomStep(step: Nothing, nbt: NbtCompound) = error("Lobby info has no custom animation step")
    override fun deserializeCustomStep(nbt: NbtCompound): Nothing = error("Lobby info has no custom animation step")

    override fun initDataTracker() {
        dataTracker.startTracking(RULE_MODULE_ID, "")
        dataTracker.startTracking(PLAYER_COUNT, 0)
        dataTracker.startTracking(MAXIMUM_PLAYER_COUNT, 0)
        dataTracker.startTracking(MANAGED_TABLE_ID, "")
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        ruleModuleId = nbt.getString(NBT_RULE_MODULE_ID)
        playerCount = nbt.getInt(NBT_PLAYER_COUNT)
        maximumPlayerCount = nbt.getInt(NBT_MAXIMUM_PLAYER_COUNT)
        managedTableId = nbt.getString(NBT_MANAGED_TABLE_ID).takeIf(String::isNotBlank)?.let { Uuid.parse(it) }
        if (nbt.contains(NBT_CONTROLLER_X)) {
            controllerPos = BlockPos(nbt.getInt(NBT_CONTROLLER_X), nbt.getInt(NBT_CONTROLLER_Y), nbt.getInt(NBT_CONTROLLER_Z))
        }
    }

    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        nbt.putString(NBT_RULE_MODULE_ID, ruleModuleId)
        nbt.putInt(NBT_PLAYER_COUNT, playerCount)
        nbt.putInt(NBT_MAXIMUM_PLAYER_COUNT, maximumPlayerCount)
        managedTableId?.let { nbt.putString(NBT_MANAGED_TABLE_ID, it.toString()) }
        controllerPos?.let {
            nbt.putInt(NBT_CONTROLLER_X, it.x)
            nbt.putInt(NBT_CONTROLLER_Y, it.y)
            nbt.putInt(NBT_CONTROLLER_Z, it.z)
        }
    }

    companion object {
        const val WIDTH = 4f
        const val HEIGHT = 3f
        private const val ORPHAN_CHECK_INTERVAL_TICKS = 20
        private const val NBT_RULE_MODULE_ID = "RuleModuleId"
        private const val NBT_PLAYER_COUNT = "PlayerCount"
        private const val NBT_MAXIMUM_PLAYER_COUNT = "MaximumPlayerCount"
        private const val NBT_MANAGED_TABLE_ID = "ManagedTableId"
        private const val NBT_CONTROLLER_X = "ControllerX"
        private const val NBT_CONTROLLER_Y = "ControllerY"
        private const val NBT_CONTROLLER_Z = "ControllerZ"
        private val RULE_MODULE_ID: TrackedData<String> =
            DataTracker.registerData(MahjongLobbyInfoEntity::class.java, TrackedDataHandlerRegistry.STRING)
        private val PLAYER_COUNT: TrackedData<Int> =
            DataTracker.registerData(MahjongLobbyInfoEntity::class.java, TrackedDataHandlerRegistry.INTEGER)
        private val MAXIMUM_PLAYER_COUNT: TrackedData<Int> =
            DataTracker.registerData(MahjongLobbyInfoEntity::class.java, TrackedDataHandlerRegistry.INTEGER)
        private val MANAGED_TABLE_ID: TrackedData<String> =
            DataTracker.registerData(MahjongLobbyInfoEntity::class.java, TrackedDataHandlerRegistry.STRING)
    }
}
