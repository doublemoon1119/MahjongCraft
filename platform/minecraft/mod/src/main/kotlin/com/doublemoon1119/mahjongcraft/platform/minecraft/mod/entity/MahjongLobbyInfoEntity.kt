package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity

import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.block.MahjongTableLookup
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.registry.ModEntities
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import kotlin.uuid.Uuid

/** 等待中遊戲的三行公開提示；由 [managedTableId] 管理並在所屬桌子失效時自行銷毀。 */
class MahjongLobbyInfoEntity(
    type: EntityType<out MahjongLobbyInfoEntity> = ModEntities.mahjongLobbyInfo,
    world: Level,
) : AnimatedMahjongEntity<Nothing>(type, world),
    HiddenDebugHitboxEntity {
    /** 等待中遊戲使用的規則模組 ID。 */
    var ruleModuleId: String
        get() = entityData[RULE_MODULE_ID]
        set(value) = entityData.set(RULE_MODULE_ID, value)

    /** 目前房間內的玩家人數。 */
    var playerCount: Int
        get() = entityData[PLAYER_COUNT]
        set(value) = entityData.set(PLAYER_COUNT, value)

    /** 規則允許的最大玩家人數。 */
    var maximumPlayerCount: Int
        get() = entityData[MAXIMUM_PLAYER_COUNT]
        set(value) = entityData.set(MAXIMUM_PLAYER_COUNT, value)

    /** 所屬麻將桌；尚未指派或資料損壞時為 `null`。 */
    var managedTableId: Uuid?
        get() = entityData[MANAGED_TABLE_ID].takeIf(String::isNotBlank)?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        private set(value) = entityData.set(MANAGED_TABLE_ID, value?.toString().orEmpty())

    /** 所屬桌子的控制方塊位置，用來檢查此等待室提示是否仍有效。 */
    private var controllerPos: BlockPos? = null

    init {
        setNoGravity(true)
    }

    /**
     * 設定所屬桌子與用來檢查桌子是否仍有效的控制方塊位置。
     *
     * @param tableId 所屬麻將桌 UUID。
     * @param controllerPos 該桌控制方塊位置。
     */
    fun assignToTable(tableId: Uuid, controllerPos: BlockPos) {
        check(!level().isClientSide) { "Managed lobby info must be assigned by the server" }
        managedTableId = tableId
        this.controllerPos = controllerPos.immutable()
    }

    override fun tick() {
        super.tick()
        if (level().isClientSide) return
        if (tickCount % ORPHAN_CHECK_INTERVAL_TICKS != 0) return
        val expectedTableId = managedTableId ?: return discard()
        val pos = controllerPos ?: return discard()
        MahjongTableLookup.find(level(), pos, expectedTableId) ?: return discard()
    }

    override fun canBeCollidedWith(): Boolean = false
    override fun isPushable(): Boolean = false
    override fun isPickable(): Boolean = false

    override fun getBoundingBoxForCulling(): AABB = AABB(
        x - WIDTH / 2.0,
        y - HEIGHT / 2.0,
        z - WIDTH / 2.0,
        x + WIDTH / 2.0,
        y + HEIGHT / 2.0,
        z + WIDTH / 2.0,
    )

    override fun applyCustomStep(step: Nothing) = error("Lobby info has no custom animation step")
    override fun applyPlayMotion(step: AnimationStep.PlayMotion, startGameTime: Long) = error("Lobby info does not support motion")
    override fun serializeCustomStep(step: Nothing, nbt: CompoundTag) = error("Lobby info has no custom animation step")
    override fun deserializeCustomStep(nbt: CompoundTag): Nothing = error("Lobby info has no custom animation step")

    override fun defineSyncedData(data: SyncedDataDefiner) {
        data.define(RULE_MODULE_ID, "")
        data.define(PLAYER_COUNT, 0)
        data.define(MAXIMUM_PLAYER_COUNT, 0)
        data.define(MANAGED_TABLE_ID, "")
    }

    override fun readAdditionalSaveData(nbt: CompoundTag) {
        ruleModuleId = nbt.getString(NBT_RULE_MODULE_ID)
        playerCount = nbt.getInt(NBT_PLAYER_COUNT)
        maximumPlayerCount = nbt.getInt(NBT_MAXIMUM_PLAYER_COUNT)
        managedTableId = ManagedTableNbt.read(nbt)
        if (nbt.contains(NBT_CONTROLLER_X)) {
            controllerPos = BlockPos(nbt.getInt(NBT_CONTROLLER_X), nbt.getInt(NBT_CONTROLLER_Y), nbt.getInt(NBT_CONTROLLER_Z))
        }
    }

    override fun addAdditionalSaveData(nbt: CompoundTag) {
        nbt.putString(NBT_RULE_MODULE_ID, ruleModuleId)
        nbt.putInt(NBT_PLAYER_COUNT, playerCount)
        nbt.putInt(NBT_MAXIMUM_PLAYER_COUNT, maximumPlayerCount)
        ManagedTableNbt.write(nbt, managedTableId)
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
        private const val NBT_CONTROLLER_X = "ControllerX"
        private const val NBT_CONTROLLER_Y = "ControllerY"
        private const val NBT_CONTROLLER_Z = "ControllerZ"
        private val RULE_MODULE_ID: EntityDataAccessor<String> =
            SynchedEntityData.defineId(MahjongLobbyInfoEntity::class.java, EntityDataSerializers.STRING)
        private val PLAYER_COUNT: EntityDataAccessor<Int> =
            SynchedEntityData.defineId(MahjongLobbyInfoEntity::class.java, EntityDataSerializers.INT)
        private val MAXIMUM_PLAYER_COUNT: EntityDataAccessor<Int> =
            SynchedEntityData.defineId(MahjongLobbyInfoEntity::class.java, EntityDataSerializers.INT)
        private val MANAGED_TABLE_ID: EntityDataAccessor<String> =
            SynchedEntityData.defineId(MahjongLobbyInfoEntity::class.java, EntityDataSerializers.STRING)
    }
}
